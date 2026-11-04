package ma.entraide.tawassol.rh;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.demande.DemandeRepository;
import ma.entraide.tawassol.demande.SoldeCongeService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Soldes de congé côté RH : demandes à régulariser, annulation d'une approbation, contrôle de cohérence, correction agent par agent,
 * jours fériés. Aucune décision n'est automatique : la RH tranche, y compris en lot.
 */
@RestController
@RequestMapping("/api/v1/rh")
@PreAuthorize("hasRole('RH')")
public class RhCongesController {
    public record Regularisation(List<String> ids, String decision, String commentaire) {}
    public record Annulation(String commentaire) {}
    public record SoldeSaisie(BigDecimal droit, BigDecimal reporte, BigDecimal pris, String categorieAgent) {}
    public record JourFerie(LocalDate date, String libelleFr, String libelleAr, String type, Boolean aConfirmer) {}

    private static final int MAX_LOT = 500;
    private static final String A_REGULARISER = """
            d.statut IN ('soumise','validee_responsable') AND d.date_fin IS NOT NULL AND d.date_fin < ? AND t.code LIKE 'CONGE\\_%'
            """;

    private final JdbcTemplate jdbc;
    private final DemandeRepository demandes;
    private final SoldeCongeService soldes;
    private final AuditService audit;
    private final Clock clock;

    public RhCongesController(JdbcTemplate jdbc, DemandeRepository demandes, SoldeCongeService soldes, AuditService audit, Clock clock) {
        this.jdbc = jdbc; this.demandes = demandes; this.soldes = soldes; this.audit = audit; this.clock = clock;
    }

    // ---- demandes à régulariser : « soumise » alors que la période est passée ----

    @GetMapping("/conges/a-regulariser")
    public Map<String, Object> aRegulariser(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size, @RequestParam(required = false) String q) {
        size = Math.max(1, Math.min(size, 200)); page = Math.max(page, 0);
        LocalDate today = LocalDate.now(clock);
        String like = "%" + (q == null ? "" : q.trim()) + "%";
        String from = " FROM demande d JOIN type_demande t ON t.id = d.type_demande_id JOIN personnel p ON p.id = d.personnel_id WHERE " + A_REGULARISER + " AND (p.matricule LIKE ? OR p.nom_fr LIKE ? OR p.prenom_fr LIKE ?)";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, today, like, like, like);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT d.id, d.numero, t.code AS typeCode, t.libelle AS typeLibelle, d.statut, d.date_debut AS dateDebut, d.date_fin AS dateFin, d.nb_jours AS nbJours, "
                + "p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr, (d.id_legacy IS NOT NULL) AS herite" + from
                + " ORDER BY d.date_debut DESC, d.id LIMIT ? OFFSET ?", today, like, like, like, size, (long) page * size);
        return Map.of("content", rows, "total", total == null ? 0 : total, "page", page, "size", size);
    }

    /** Régularisation en lot sous contrôle RH : « approuver » = congé réellement pris (décompte le solde), « rejeter » = jamais pris. */
    @PostMapping("/conges/regulariser")
    @Transactional
    public Map<String, Object> regulariser(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Regularisation r) {
        if (r.ids() == null || r.ids().isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDS_REQUIS", "Aucune demande sélectionnée");
        if (r.ids().size() > MAX_LOT) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LOT_TROP_GRAND", "Maximum " + MAX_LOT + " demandes par lot");
        boolean approuver = "approuver".equals(r.decision());
        if (!approuver && !"rejeter".equals(r.decision())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DECISION_INVALIDE", "Décision : approuver ou rejeter");
        if (r.commentaire() == null || r.commentaire().isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COMMENT_REQUIRED", "Un commentaire est obligatoire pour une régularisation");
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        String cible = approuver ? "approuvee" : "rejetee";
        int traitees = 0, ignorees = 0;
        for (String id : r.ids()) {
            List<Map<String, Object>> l = jdbc.queryForList("SELECT d.id, d.statut, d.personnel_id, d.date_debut, d.nb_jours, t.code FROM demande d JOIN type_demande t ON t.id = d.type_demande_id WHERE d.id = ? AND " + A_REGULARISER, id, today);
            if (l.isEmpty() || id == null) { ignorees++; continue; }
            Map<String, Object> d = l.get(0);
            String personnelId = (String) d.get("personnel_id");
            if (personnelId.equals(u.personnelId())) { ignorees++; continue; }   // on ne régularise pas son propre congé
            String ancien = (String) d.get("statut");
            jdbc.update("UPDATE demande SET statut = ?, traite_par = ?, date_traitement = ?, commentaire_rh = ? WHERE id = ?", cible, u.userId(), now, "Régularisation RH : " + r.commentaire(), id);
            demandes.addHistorique(id, u.userId(), ancien, cible, "Régularisation RH (période passée) : " + r.commentaire(), now);
            if (approuver && "CONGE_ANNUEL".equals(d.get("code")) && d.get("nb_jours") != null && d.get("date_debut") != null)
                soldes.deduire(personnelId, annee(d.get("date_debut")), (BigDecimal) d.get("nb_jours"));
            audit.record(u.userId(), "CONGE_REGULARISE", "demande", id, "{\"statut\":\"" + ancien + "\"}", "{\"statut\":\"" + cible + "\"}");
            traitees++;
        }
        return Map.of("traitees", traitees, "ignorees", ignorees);
    }

    /** Revenir sur un congé approuvé : il passe à « annulée » et restitue ses jours au solde. */
    @PostMapping("/conges/{id}/annuler-approbation")
    @Transactional
    public Map<String, Object> annulerApprobation(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody Annulation a) {
        if (a == null || a.commentaire() == null || a.commentaire().isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COMMENT_REQUIRED", "Un commentaire est obligatoire");
        List<Map<String, Object>> l = jdbc.queryForList("SELECT d.id, d.statut, d.personnel_id, d.date_debut, d.nb_jours, t.code FROM demande d JOIN type_demande t ON t.id = d.type_demande_id WHERE d.id = ? AND t.code LIKE 'CONGE\\_%'", id);
        if (l.isEmpty()) throw ApiException.notFound();
        Map<String, Object> d = l.get(0);
        if (!"approuvee".equals(d.get("statut"))) throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Seul un congé approuvé peut être annulé");
        if (d.get("personnel_id").equals(u.personnelId())) throw new ApiException(HttpStatus.FORBIDDEN, "SELF_VALIDATION_FORBIDDEN", "Vous ne pouvez pas traiter votre propre demande");
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("UPDATE demande SET statut = 'annulee', traite_par = ?, date_traitement = ?, commentaire_rh = ? WHERE id = ?", u.userId(), now, a.commentaire(), id);
        demandes.addHistorique(id, u.userId(), "approuvee", "annulee", a.commentaire(), now);
        if ("CONGE_ANNUEL".equals(d.get("code")) && d.get("nb_jours") != null && d.get("date_debut") != null)
            soldes.restituer((String) d.get("personnel_id"), annee(d.get("date_debut")), (BigDecimal) d.get("nb_jours"));
        audit.record(u.userId(), "CONGE_APPROBATION_ANNULEE", "demande", id, null, null);
        return Map.of("id", id, "statut", "annulee");
    }

    // ---- contrôle de cohérence ----

    /** Somme des jours approuvés = somme des jours « pris » du solde, par année, et liste des soldes négatifs à revoir. */
    @GetMapping("/conges/controle")
    public Map<String, Object> controle(@RequestParam(required = false) Integer annee) {
        int anneeNeg = annee == null ? LocalDate.now(clock).getYear() : annee;
        List<Map<String, Object>> annees = jdbc.queryForList("""
                SELECT a.annee, COALESCE(a.jours, 0) AS joursApprouves, COALESCE(s.pris, 0) AS joursPris, COALESCE(a.jours, 0) - COALESCE(s.pris, 0) AS ecart
                FROM (SELECT YEAR(d.date_debut) AS annee, SUM(d.nb_jours) AS jours FROM demande d JOIN type_demande t ON t.id = d.type_demande_id
                       WHERE t.code = 'CONGE_ANNUEL' AND d.statut = 'approuvee' AND d.date_debut IS NOT NULL GROUP BY YEAR(d.date_debut)) a
                LEFT JOIN (SELECT annee, SUM(pris) AS pris FROM solde_conge GROUP BY annee) s ON s.annee = a.annee
                UNION
                SELECT s.annee, 0, s.pris, -s.pris FROM (SELECT annee, SUM(pris) AS pris FROM solde_conge GROUP BY annee) s
                 WHERE s.annee NOT IN (SELECT YEAR(d.date_debut) FROM demande d JOIN type_demande t ON t.id = d.type_demande_id WHERE t.code = 'CONGE_ANNUEL' AND d.statut = 'approuvee' AND d.date_debut IS NOT NULL)
                ORDER BY 1 DESC""");
        boolean coherent = annees.stream().allMatch(m -> ((Number) m.get("ecart")).doubleValue() == 0.0);
        // soldes négatifs de l'année : calculés avec les règles en vigueur (droit par catégorie, report), pas avec la valeur stockée
        List<Map<String, Object>> negatifs = new java.util.ArrayList<>();
        for (Map<String, Object> c : jdbc.queryForList("SELECT s.personnel_id AS pid, p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr FROM solde_conge s JOIN personnel p ON p.id = s.personnel_id WHERE s.annee = ? AND s.pris > 0", anneeNeg)) {
            SoldeCongeService.Solde sd = soldes.get((String) c.get("pid"), anneeNeg, null);
            BigDecimal dispo = sd.droit().add(sd.reporte()).subtract(sd.pris());
            if (dispo.signum() < 0) {
                Map<String, Object> m = new java.util.LinkedHashMap<>(c);
                m.put("personnelId", m.remove("pid")); m.put("annee", anneeNeg); m.put("droit", sd.droit()); m.put("reporte", sd.reporte()); m.put("pris", sd.pris()); m.put("disponible", dispo); m.put("source", sd.source()); m.put("aVerifier", sd.aVerifier());
                negatifs.add(m);
            }
        }
        negatifs.sort(java.util.Comparator.comparing(m -> (BigDecimal) m.get("disponible")));
        long nbNeg = negatifs.size();
        if (negatifs.size() > 200) negatifs = new java.util.ArrayList<>(negatifs.subList(0, 200));
        List<Map<String, Object>> sources = jdbc.queryForList("SELECT source, COUNT(*) AS n, SUM(a_verifier) AS aVerifier FROM solde_conge GROUP BY source ORDER BY source");
        Long reg = jdbc.queryForObject("SELECT COUNT(*) FROM demande d JOIN type_demande t ON t.id = d.type_demande_id WHERE " + A_REGULARISER, Long.class, LocalDate.now(clock));
        return Map.of("parAnnee", annees, "coherent", coherent, "negatifsTotal", nbNeg, "anneeNegatifs", anneeNeg, "negatifs", negatifs, "parSource", sources, "aRegulariser", reg == null ? 0 : reg);
    }

    // ---- correction agent par agent ----

    @GetMapping("/conges/solde")
    public Map<String, Object> solde(@RequestParam String matricule, @RequestParam(required = false) Integer annee) {
        List<Map<String, Object>> p = jdbc.queryForList("SELECT id, matricule, nom_fr AS nomFr, prenom_fr AS prenomFr, nom_ar AS nomAr, prenom_ar AS prenomAr, categorie_agent AS categorieAgent FROM personnel WHERE matricule = ?", matricule.trim());
        if (p.isEmpty()) throw ApiException.notFound();
        int an = annee == null ? LocalDate.now(clock).getYear() : annee;
        Map<String, Object> out = new java.util.LinkedHashMap<>(p.get(0));
        out.put("solde", soldes.get((String) p.get(0).get("id"), an, null));
        return out;
    }

    /** Soldes d'un agent, année par année (fiche RH de l'agent). */
    @GetMapping("/conges/soldes/{personnelId}")
    public List<SoldeCongeService.Solde> soldesParAnnee(@PathVariable String personnelId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE id = ?", Integer.class, personnelId) == 0) throw ApiException.notFound();
        return soldes.parAnnee(personnelId);
    }

    @PutMapping("/conges/soldes/{personnelId}/{annee}")
    @Transactional
    public Map<String, Object> corriger(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String personnelId, @PathVariable int annee, @RequestBody SoldeSaisie s) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE id = ?", Integer.class, personnelId) == 0) throw ApiException.notFound();
        if (annee < 2000 || annee > 2100) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ANNEE_INVALIDE", "Année invalide");
        SoldeCongeService.Solde avant = soldes.get(personnelId, annee, null);
        BigDecimal droit = s.droit() == null ? avant.droit() : s.droit(), reporte = s.reporte() == null ? BigDecimal.ZERO : s.reporte(), pris = s.pris() == null ? avant.pris() : s.pris();
        if (droit.signum() < 0 || reporte.signum() < 0 || pris.signum() < 0 || droit.compareTo(BigDecimal.valueOf(365)) > 0 || reporte.compareTo(BigDecimal.valueOf(365)) > 0 || pris.compareTo(BigDecimal.valueOf(365)) > 0)
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALEUR_INVALIDE", "Valeurs entre 0 et 365 jours");
        jdbc.update("INSERT INTO solde_conge (id, personnel_id, annee, droit, pris, reporte, source, a_verifier) VALUES (?,?,?,?,?,?, 'saisi', 0) ON DUPLICATE KEY UPDATE droit = VALUES(droit), pris = VALUES(pris), reporte = VALUES(reporte), source = 'saisi', a_verifier = 0",
                UUID.randomUUID().toString(), personnelId, annee, droit, pris, reporte);
        if (s.categorieAgent() != null) {
            String c = s.categorieAgent().trim();
            if (!c.isEmpty() && !c.matches("[A-Za-z0-9_]{1,30}")) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CATEGORIE_INVALIDE", "Catégorie : lettres, chiffres et _ (30 max)");
            jdbc.update("UPDATE personnel SET categorie_agent = ? WHERE id = ?", c.isEmpty() ? null : c.toUpperCase(), personnelId);
        }
        audit.record(u.userId(), "SOLDE_CONGE_CORRIGE", "solde_conge", personnelId + "/" + annee,
                "{\"droit\":" + avant.droit() + ",\"reporte\":" + avant.reporte() + ",\"pris\":" + avant.pris() + "}", "{\"droit\":" + droit + ",\"reporte\":" + reporte + ",\"pris\":" + pris + "}");
        return Map.of("personnelId", personnelId, "annee", annee);
    }

    // ---- jours fériés tenus par la RH (entrent dans le décompte des jours ouvrables) ----

    @GetMapping("/jours-feries")
    public List<Map<String, Object>> joursFeries(@RequestParam(required = false) Integer annee) {
        int an = annee == null ? LocalDate.now(clock).getYear() : annee;
        return jdbc.queryForList("SELECT id, date_ferie AS date, libelle_fr AS libelleFr, libelle_ar AS libelleAr, type_ferie AS type, a_confirmer AS aConfirmer FROM jour_ferie WHERE YEAR(date_ferie) = ? ORDER BY date_ferie", an);
    }

    @PostMapping("/jours-feries")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> ajouterJourFerie(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody JourFerie j) {
        valider(j);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM jour_ferie WHERE date_ferie = ?", Integer.class, j.date()) > 0) throw new ApiException(HttpStatus.CONFLICT, "FERIE_EXISTANT", "Cette date est déjà un jour férié");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO jour_ferie (id, date_ferie, libelle_fr, libelle_ar, type_ferie, a_confirmer) VALUES (?,?,?,?,?,?)", id, j.date(), j.libelleFr().trim(), j.libelleAr().trim(), j.type(), Boolean.TRUE.equals(j.aConfirmer()) ? 1 : 0);
        audit.record(u.userId(), "JOUR_FERIE_AJOUTE", "jour_ferie", id, null, "{\"date\":\"" + j.date() + "\"}");
        return Map.of("id", id);
    }

    @PutMapping("/jours-feries/{id}")
    public Map<String, Object> modifierJourFerie(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody JourFerie j) {
        valider(j);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM jour_ferie WHERE date_ferie = ? AND id <> ?", Integer.class, j.date(), id) > 0) throw new ApiException(HttpStatus.CONFLICT, "FERIE_EXISTANT", "Cette date est déjà un jour férié");
        int n = jdbc.update("UPDATE jour_ferie SET date_ferie = ?, libelle_fr = ?, libelle_ar = ?, type_ferie = ?, a_confirmer = ? WHERE id = ?", j.date(), j.libelleFr().trim(), j.libelleAr().trim(), j.type(), Boolean.TRUE.equals(j.aConfirmer()) ? 1 : 0, id);
        if (n == 0) throw ApiException.notFound();
        audit.record(u.userId(), "JOUR_FERIE_MODIFIE", "jour_ferie", id, null, "{\"date\":\"" + j.date() + "\"}");
        return Map.of("id", id);
    }

    @DeleteMapping("/jours-feries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerJourFerie(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        if (jdbc.update("DELETE FROM jour_ferie WHERE id = ?", id) == 0) throw ApiException.notFound();
        audit.record(u.userId(), "JOUR_FERIE_SUPPRIME", "jour_ferie", id, null, null);
    }

    private static int annee(Object o) {
        if (o instanceof java.sql.Date d) return d.toLocalDate().getYear();
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime().getYear();
        if (o instanceof LocalDate l) return l.getYear();
        return LocalDate.parse(String.valueOf(o).substring(0, 10)).getYear();
    }

    private static void valider(JourFerie j) {
        if (j == null || j.date() == null || j.libelleFr() == null || j.libelleFr().isBlank() || j.libelleAr() == null || j.libelleAr().isBlank())
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FERIE_INCOMPLET", "Date et libellés (français et arabe) obligatoires");
        if (!List.of("civil", "religieux").contains(j.type())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FERIE_TYPE", "Type : civil ou religieux");
    }
}
