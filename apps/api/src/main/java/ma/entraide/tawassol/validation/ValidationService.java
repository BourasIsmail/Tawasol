package ma.entraide.tawassol.validation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ma.entraide.tawassol.access.AccessGuard;
import ma.entraide.tawassol.access.PerimeterService;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.demande.DemandeModels.DemandeRow;
import ma.entraide.tawassol.demande.DemandeRepository;
import ma.entraide.tawassol.demande.SoldeCongeService;
import ma.entraide.tawassol.notification.NotificationService;
import ma.entraide.tawassol.personnel.PageResult;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Premier niveau de validation. Le responsable (ou son suppléant pendant la suppléance) ne voit que les demandes qui lui sont
 * attribuées à la soumission, jamais la sienne. Les données de santé (motif et pièces d'un congé maladie) ne lui sont pas montrées.
 */
@Service
public class ValidationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final DemandeRepository demandes;
    private final PerimeterService perimeters;
    private final AccessGuard guard;
    private final SoldeCongeService soldes;
    private final NotificationService notifs;
    private final AuditService audit;
    private final Clock clock;
    private final ChaineValidationService chaine;

    public ValidationService(NamedParameterJdbcTemplate jdbc, DemandeRepository demandes, PerimeterService perimeters, AccessGuard guard,
                             SoldeCongeService soldes, NotificationService notifs, AuditService audit, Clock clock, ChaineValidationService chaine) {
        this.chaine = chaine;
        this.jdbc = jdbc; this.demandes = demandes; this.perimeters = perimeters; this.guard = guard;
        this.soldes = soldes; this.notifs = notifs; this.audit = audit; this.clock = clock;
    }

    private Set<String> acting(AuthenticatedUser u) {
        if (u.personnelId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "NO_PERSONNEL", "Ce compte n'est lié à aucun agent");
        return perimeters.effectivePersonnelIds(u.personnelId(), true);
    }

    public PageResult<Map<String, Object>> queue(AuthenticatedUser u, String statut, String type, int page, int size) {
        Set<String> ids = acting(u);
        String st = statut == null || statut.isBlank() ? "soumise" : statut;
        if (!Set.of("soumise", "validee_responsable", "rejetee_responsable").contains(st)) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Statut invalide");
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("ids", ids).addValue("me", u.personnelId());
        // « À valider » : demandes dont le niveau actif est le mien. « Validées » / « Rejetées » : celles où j'ai moi-même décidé à mon niveau.
        String where = switch (st) {
            case "soumise" -> " WHERE d.responsable_personnel_id IN (:ids) AND d.personnel_id <> :me AND d.statut = 'soumise'";
            case "validee_responsable" -> " WHERE d.personnel_id <> :me AND d.statut <> 'annulee' AND (EXISTS (SELECT 1 FROM etape_validation e WHERE e.objet_type = 'demande' AND e.objet_id = d.id AND e.responsable_personnel_id IN (:ids) AND e.statut = 'validee')"
                    + " OR (d.responsable_personnel_id IN (:ids) AND d.statut = 'validee_responsable'))";
            default -> " WHERE d.personnel_id <> :me AND (EXISTS (SELECT 1 FROM etape_validation e WHERE e.objet_type = 'demande' AND e.objet_id = d.id AND e.responsable_personnel_id IN (:ids) AND e.statut = 'rejetee')"
                    + " OR (d.responsable_personnel_id IN (:ids) AND d.statut = 'rejetee_responsable'))";
        };
        if (type != null && !type.isBlank()) { where += " AND t.code = :type"; p.addValue("type", type); }
        String from = " FROM demande d JOIN personnel p ON p.id = d.personnel_id JOIN type_demande t ON t.id = d.type_demande_id" + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, p, Long.class);
        p.addValue("limit", size).addValue("offset", (long) page * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT d.id, d.numero, t.code AS typeCode, t.libelle AS typeLibelle, d.statut, d.date_debut AS dateDebut, d.date_fin AS dateFin,
                   d.nb_jours AS nbJours, d.cree_le AS creeLe, p.id AS agentId, p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr,
                   p.nom_ar AS nomAr, p.prenom_ar AS prenomAr, DATEDIFF(:today, d.cree_le) AS ancienneteJours
            """ + from + " ORDER BY d.cree_le, d.id LIMIT :limit OFFSET :offset", p.addValue("today", LocalDate.now(clock)));
        return new PageResult<>(rows, total == null ? 0 : total, page, size);
    }

    public Map<String, Object> detail(AuthenticatedUser u, String id) {
        DemandeRow d = accessible(u, id);
        boolean sante = "CONGE_MALADIE".equals(d.typeCode());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", d.id()); out.put("numero", d.numero()); out.put("typeCode", d.typeCode()); out.put("typeLibelle", d.typeLibelle());
        out.put("statut", d.statut()); out.put("objet", sante ? d.typeLibelle() : d.objet()); out.put("description", sante ? null : d.description());
        out.put("dateDebut", d.dateDebut()); out.put("dateFin", d.dateFin()); out.put("nbJours", d.nbJours()); out.put("creeLe", d.creeLe());
        out.put("agent", jdbc.queryForMap("""
            SELECT p.id, p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr,
                   g.libelle_fr AS gradeFr, g.libelle_ar AS gradeAr, s.nom_fr AS structureFr, s.nom_ar AS structureAr
              FROM personnel p JOIN structure s ON s.id = p.structure_id LEFT JOIN grade g ON g.id = p.grade_id WHERE p.id = :p
            """, new MapSqlParameterSource("p", d.personnelId())));
        if ("CONGE_ANNUEL".equals(d.typeCode()) && d.dateDebut() != null) {
            var s = soldes.get(d.personnelId(), d.dateDebut().getYear(), d.id());
            out.put("solde", Map.of("annee", s.annee(), "disponible", s.disponible(), "suffisant", d.nbJours() == null || s.disponible().compareTo(d.nbJours()) >= 0));
        }
        out.put("equipe", d.dateDebut() == null ? List.of() : equipeAbsente(u, d));
        out.put("historique", demandes.historique(id));
        out.put("chaine", chaine.liste("demande", id));
        out.put("monTourDeDecider", "soumise".equals(d.statut()) && acting(u).contains(d.responsablePersonnelId()));
        out.put("nbPiecesJointes", sante ? null : demandes.countPieces(id));
        return out;
    }

    @Transactional
    public Map<String, Object> decider(AuthenticatedUser u, String id, boolean valider, String commentaire) {
        DemandeRow d = accessible(u, id);
        String cible = valider ? "validee_responsable" : "rejetee_responsable";
        if (cible.equals(d.statut())) return Map.of("id", id, "statut", d.statut());
        if (!"soumise".equals(d.statut())) throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Cette demande n'est plus à valider");
        if (!valider && (commentaire == null || commentaire.isBlank())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COMMENT_REQUIRED", "Un commentaire est obligatoire pour rejeter une demande");
        }
        if (!acting(u).contains(d.responsablePersonnelId())) throw new ApiException(HttpStatus.CONFLICT, "NOT_YOUR_TURN", "Ce n'est pas encore (ou plus) le tour de votre niveau de validation");
        LocalDateTime now = LocalDateTime.now(clock);
        var etape = chaine.active("demande", id);
        boolean parSuppleance = !d.responsablePersonnelId().equals(u.personnelId());
        String commentaireHisto = (parSuppleance ? "[par suppléance] " : "") + (commentaire == null ? "" : commentaire);
        if (etape.isEmpty()) {   // demande antérieure à la chaîne : un seul responsable
            return finaliser(u, d, cible, valider, commentaire, commentaireHisto, now);
        }
        var r = chaine.decider("demande", id, etape.get(), u.userId(), u.personnelId(), valider, commentaire);
        String niveau = " [niveau " + r.niveauDecide() + "/" + r.total() + " : " + etape.get().niveau() + "]";
        if (r.rejetee() || r.complete()) return finaliser(u, d, cible, valider, commentaire, commentaireHisto + niveau, now);
        // niveau franchi, la demande passe au responsable suivant sans changer de statut
        jdbc.update("UPDATE demande SET responsable_personnel_id = :r WHERE id = :id", new MapSqlParameterSource("r", r.suivantPersonnelId()).addValue("id", id));
        demandes.addHistorique(id, u.userId(), "soumise", "soumise", "Validée" + niveau + (commentaireHisto.isBlank() ? "" : " : " + commentaireHisto), now);
        audit.record(u.userId(), "DEMANDE_NIVEAU_VALIDE", "demande", id, null, "{\"niveau\":" + r.niveauDecide() + "}");
        notifs.notifierResponsable(r.suivantPersonnelId(), "VALIDATION_A_FAIRE", "Demande à valider", d.typeLibelle() + " · " + d.numero(), "/responsable/validation/" + id);
        notifs.notifierAgent(d.personnelId(), "DEMANDE_NIVEAU_VALIDE", "Étape de validation franchie", d.typeLibelle() + " · " + d.numero() + " : niveau " + r.niveauDecide() + " sur " + r.total(), "/demandes/" + id);
        return Map.of("id", id, "statut", "soumise", "niveau", r.niveauDecide(), "total", r.total());
    }

    private Map<String, Object> finaliser(AuthenticatedUser u, DemandeRow d, String cible, boolean valider, String commentaire, String commentaireHisto, LocalDateTime now) {
        String id = d.id();
        jdbc.update("UPDATE demande SET statut = :s, valide_par = :u, date_validation_responsable = :t, commentaire_responsable = :c WHERE id = :id",
                new MapSqlParameterSource("s", cible).addValue("u", u.userId()).addValue("t", now).addValue("c", commentaire).addValue("id", id));
        demandes.addHistorique(id, u.userId(), "soumise", cible, commentaireHisto, now);
        audit.record(u.userId(), valider ? "DEMANDE_VALIDEE_RESPONSABLE" : "DEMANDE_REJETEE_RESPONSABLE", "demande", id, null, null);
        notifs.notifierAgent(d.personnelId(), valider ? "DEMANDE_VALIDEE" : "DEMANDE_REJETEE",
                valider ? "Demande validée par tous vos responsables" : "Demande rejetée par votre responsable",
                d.typeLibelle() + " · " + d.numero() + (valider ? "" : " : " + commentaire), "/demandes/" + id);
        return Map.of("id", id, "statut", cible);
    }

    public Map<String, Object> tableauDeBord(AuthenticatedUser u) {
        Set<String> ids = acting(u);
        LocalDate today = LocalDate.now(clock);
        var scope = guard.personnelScope(u);
        Map<String, Object> out = new LinkedHashMap<>();
        // délai moyen de réponse du responsable (90 derniers jours), en jours
        out.put("delaiMoyenJours", jdbc.queryForObject("SELECT ROUND(AVG(DATEDIFF(date_validation_responsable, cree_le)), 1) FROM demande WHERE responsable_personnel_id IN (:ids) AND date_validation_responsable IS NOT NULL AND date_validation_responsable >= DATE_SUB(:today, INTERVAL 90 DAY)", new MapSqlParameterSource("ids", ids).addValue("today", today), java.math.BigDecimal.class));
        out.put("structure", scope.structureIds().isEmpty() ? null : jdbc.queryForList("SELECT nom_fr FROM structure WHERE id IN (:s) ORDER BY nom_fr LIMIT 1", new MapSqlParameterSource("s", scope.structureIds()), String.class).stream().findFirst().orElse(null));
        out.put("aValider", jdbc.queryForObject("SELECT COUNT(*) FROM demande WHERE responsable_personnel_id IN (:ids) AND personnel_id <> :me AND statut = 'soumise'",
                new MapSqlParameterSource("ids", ids).addValue("me", u.personnelId()), Integer.class));
        if (scope.structureIds().isEmpty()) {
            out.put("effectif", 0); out.put("absentsAujourdhui", List.of());
            return out;
        }
        MapSqlParameterSource p = new MapSqlParameterSource("sids", scope.structureIds()).addValue("me", u.personnelId()).addValue("today", today);
        out.put("effectif", jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE est_actif = 1 AND structure_id IN (:sids) AND id <> :me", p, Integer.class));
        out.put("absentsAujourdhui", jdbc.queryForList("""
            SELECT p.id, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr, d.date_fin AS dateFin,
                   CASE t.code WHEN 'CONGE_ANNUEL' THEN 'conge' WHEN 'ORDRE_MISSION' THEN 'mission' ELSE 'absence' END AS genre
              FROM demande d JOIN personnel p ON p.id = d.personnel_id JOIN type_demande t ON t.id = d.type_demande_id
             WHERE p.structure_id IN (:sids) AND p.id <> :me AND d.statut IN ('validee_responsable','approuvee')
               AND t.code IN ('CONGE_ANNUEL','CONGE_MALADIE','CONGE_EXCEPTION','ORDRE_MISSION') AND d.date_debut <= :today AND d.date_fin >= :today
             ORDER BY p.nom_fr
            """, p));
        return out;
    }

    /** Une demande n'est accessible qu'au responsable désigné (ou à son suppléant), jamais à son auteur. Sinon : introuvable. */
    private DemandeRow accessible(AuthenticatedUser u, String id) {
        Set<String> ids = acting(u);
        DemandeRow d = demandes.findById(id).orElseThrow(ApiException::notFound);
        String resp = jdbc.queryForObject("SELECT responsable_personnel_id FROM demande WHERE id = :id", new MapSqlParameterSource("id", id), String.class);
        if (resp == null || !(ids.contains(resp) || chaine.implique("demande", id, ids))) throw ApiException.notFound();
        if (d.personnelId().equals(u.personnelId())) throw new ApiException(HttpStatus.FORBIDDEN, "SELF_VALIDATION_FORBIDDEN", "Vous ne pouvez pas traiter votre propre demande");
        return new DemandeRow(d.id(), d.numero(), d.personnelId(), d.typeId(), d.typeCode(), d.typeLibelle(), d.validationResponsable(), d.objet(), d.description(),
                d.statut(), d.dateDebut(), d.dateFin(), d.nbJours(), d.donneesJson(), d.commentaireResponsable(), d.commentaireRh(), d.creeLe(), d.modifieLe(), resp);
    }

    private List<Map<String, Object>> equipeAbsente(AuthenticatedUser u, DemandeRow d) {
        var scope = guard.personnelScope(u);
        if (scope.structureIds().isEmpty()) return List.of();
        return jdbc.queryForList("""
            SELECT p.id, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr, x.date_debut AS dateDebut, x.date_fin AS dateFin, x.statut,
                   CASE t.code WHEN 'CONGE_ANNUEL' THEN 'conge' WHEN 'ORDRE_MISSION' THEN 'mission' ELSE 'absence' END AS genre
              FROM demande x JOIN personnel p ON p.id = x.personnel_id JOIN type_demande t ON t.id = x.type_demande_id
             WHERE p.structure_id IN (:sids) AND p.id <> :agent AND t.code IN ('CONGE_ANNUEL','CONGE_MALADIE','CONGE_EXCEPTION','ORDRE_MISSION')
               AND x.statut IN ('soumise','validee_responsable','approuvee') AND x.date_debut <= :fin AND x.date_fin >= :debut
             ORDER BY x.date_debut
            """, new MapSqlParameterSource("sids", scope.structureIds()).addValue("agent", d.personnelId()).addValue("debut", d.dateDebut()).addValue("fin", d.dateFin()));
    }
}
