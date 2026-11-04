package ma.entraide.tawassol.demande;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.demande.DemandeModels.*;
import ma.entraide.tawassol.notification.NotificationService;
import ma.entraide.tawassol.param.JoursOuvrablesService;
import ma.entraide.tawassol.param.NumeroService;
import ma.entraide.tawassol.param.ParametreService;
import ma.entraide.tawassol.personnel.PageResult;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Circuit des demandes de l'agent (phase 2) : brouillon, soumission avec règles par type, annulation. */
@Service
public class DemandeService {
    private static final Set<String> ATTESTATIONS = Set.of("travail", "salaire");

    private final DemandeRepository repo;
    private final ParametreService params;
    private final JoursOuvrablesService jours;
    private final NumeroService numeros;
    private final NotificationService notifs;
    private final SoldeCongeService soldes;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final ma.entraide.tawassol.hierarchy.HierarchyIndex hierarchy;
    private final ma.entraide.tawassol.validation.ChaineValidationService chaine;

    public DemandeService(DemandeRepository repo, ParametreService params, JoursOuvrablesService jours, NumeroService numeros,
                          NotificationService notifs, SoldeCongeService soldes, JdbcTemplate jdbc, ObjectMapper json, Clock clock,
                          ma.entraide.tawassol.hierarchy.HierarchyIndex hierarchy, ma.entraide.tawassol.validation.ChaineValidationService chaine) {
        this.chaine = chaine;
        this.repo = repo; this.params = params; this.jours = jours; this.numeros = numeros; this.notifs = notifs;
        this.soldes = soldes; this.jdbc = jdbc; this.json = json; this.clock = clock; this.hierarchy = hierarchy;
    }

    public List<Map<String, Object>> types() { return repo.typesActifs(); }

    public PageResult<DemandeDto> list(AuthenticatedUser u, String statut, int page, int size) {
        PageResult<DemandeRow> r = repo.listForPersonnel(requirePersonnel(u), statut, page, size);
        return new PageResult<>(r.content().stream().map(row -> toDto(row, false, null)).toList(), r.total(), r.page(), r.size());
    }

    public DemandeDto get(AuthenticatedUser u, String id) { return toDto(own(u, id), true, null); }

    @Transactional
    public DemandeDto create(AuthenticatedUser u, DemandeRequest req) {
        String pid = requirePersonnel(u);
        String typeId = repo.typeIdByCode(req.typeCode()).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_TYPE", "Type de demande inconnu"));
        checkDates(req.dateDebut(), req.dateFin());
        LocalDateTime now = LocalDateTime.now(clock);
        String objet = blank(req.objet()) ? libelleType(req.typeCode()) : req.objet().trim();
        String id = repo.insert(pid, typeId, objet, req.description(), req.dateDebut(), req.dateFin(), write(req.donnees()), now);
        repo.addHistorique(id, u.userId(), null, "brouillon", null, now);
        return toDto(repo.findById(id).orElseThrow(), true, null);
    }

    @Transactional
    public DemandeDto update(AuthenticatedUser u, String id, DemandeRequest req) {
        DemandeRow row = own(u, id);
        if (!"brouillon".equals(row.statut())) throw new ApiException(HttpStatus.CONFLICT, "NOT_EDITABLE", "Seul un brouillon peut être modifié");
        if (!row.typeCode().equals(req.typeCode())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TYPE_IMMUTABLE", "Le type d'une demande ne peut pas changer");
        checkDates(req.dateDebut(), req.dateFin());
        repo.updateContent(id, blank(req.objet()) ? row.typeLibelle() : req.objet().trim(), req.description(), req.dateDebut(), req.dateFin(), write(req.donnees()));
        return toDto(repo.findById(id).orElseThrow(), true, null);
    }

    @Transactional
    public void delete(AuthenticatedUser u, String id) {
        DemandeRow row = own(u, id);
        if (!"brouillon".equals(row.statut())) throw new ApiException(HttpStatus.CONFLICT, "NOT_EDITABLE", "Seul un brouillon peut être supprimé");
        repo.delete(id);
    }

    /** Idempotent : soumettre une demande déjà soumise renvoie son état sans créer de doublon. */
    @Transactional
    public DemandeDto submit(AuthenticatedUser u, String id) {
        DemandeRow row = own(u, id);
        if ("soumise".equals(row.statut())) return toDto(row, true, null);
        if (!"brouillon".equals(row.statut())) throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Cette demande ne peut plus être soumise");
        List<String> avertissements = new ArrayList<>();
        Map<String, Object> donnees = read(row.donneesJson());
        BigDecimal nbJours = validerPourSoumission(row, donnees, avertissements);
        LocalDateTime now = LocalDateTime.now(clock);
        String numero = numeros.suivant("DEM", now.getYear());
        repo.setStatut(id, "soumise", numero, nbJours);
        repo.addHistorique(id, u.userId(), "brouillon", "soumise", null, now);
        if ("ORDRE_MISSION".equals(row.typeCode())) creerOrdreMission(row, donnees, now);
        notifs.notifier(u.userId(), "DEMANDE_SOUMISE", "Demande soumise", row.typeLibelle() + " · " + numero, "/demandes/" + id);
        // Responsable compétent : remontée dans l'arbre, le demandeur lui-même écarté. Aucun responsable (ou type sans validation) : RH directe.
        if (row.validationResponsable()) {
            String structureId = jdbc.queryForObject("SELECT structure_id FROM personnel WHERE id = ?", String.class, row.personnelId());
            // chaîne complète : tous les responsables de la hiérarchie valident, dans l'ordre ; le premier est notifié
            var etapes = chaine.construire("demande", id, row.personnelId(), structureId);
            String resp = etapes.isEmpty() ? null : etapes.get(0).responsablePersonnelId();
            repo.setResponsable(id, resp);
            notifs.notifierResponsable(resp, "VALIDATION_A_FAIRE", "Demande à valider", row.typeLibelle() + " · " + numero, "/responsable/validation/" + id);
        }
        return toDto(repo.findById(id).orElseThrow(), true, avertissements);
    }

    @Transactional
    public DemandeDto cancel(AuthenticatedUser u, String id, String commentaire) {
        DemandeRow row = own(u, id);
        if ("annulee".equals(row.statut())) return toDto(row, true, null);
        if (!Set.of("brouillon", "soumise", "validee_responsable").contains(row.statut())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Une demande approuvée ou rejetée ne peut plus être annulée");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        repo.setStatut(id, "annulee", null, null);
        jdbc.update("UPDATE etape_validation SET statut = 'ignoree' WHERE objet_type = 'demande' AND objet_id = ? AND statut = 'en_attente'", id);
        repo.addHistorique(id, u.userId(), row.statut(), "annulee", commentaire, now);
        jdbc.update("UPDATE ordre_mission SET statut = 'annulee' WHERE demande_id = ?", id);
        notifs.notifier(u.userId(), "DEMANDE_ANNULEE", "Demande annulée", row.typeLibelle() + (row.numero() == null ? "" : " · " + row.numero()), "/demandes/" + id);
        return toDto(repo.findById(id).orElseThrow(), true, null);
    }

    // ---- règles par type ----

    private BigDecimal validerPourSoumission(DemandeRow row, Map<String, Object> donnees, List<String> avert) {
        switch (row.typeCode()) {
            case "CONGE_ANNUEL", "CONGE_MALADIE", "CONGE_EXCEPTION" -> {
                requireDates(row);
                int n = jours.compter(row.dateDebut(), row.dateFin());
                if (n == 0) throw unprocessable("NO_WORKING_DAYS", "La période ne contient aucun jour ouvrable");
                if (repo.chevauche(row.personnelId(), row.id(), row.dateDebut(), row.dateFin())) {
                    throw new ApiException(HttpStatus.CONFLICT, "LEAVE_OVERLAP", "Cette période chevauche un autre congé");
                }
                int pieces = repo.countPieces(row.id());
                switch (row.typeCode()) {
                    case "CONGE_ANNUEL" -> {
                        var solde = soldes.get(row.personnelId(), row.dateDebut().getYear(), row.id());
                        if (solde.utilisable().compareTo(BigDecimal.valueOf(n)) < 0) {
                            throw unprocessable("INSUFFICIENT_BALANCE", "Solde insuffisant : " + solde.utilisable() + " jour(s) disponible(s) pour " + n + " demandé(s)");
                        }
                    }
                    case "CONGE_MALADIE" -> { if (pieces < 1) throw unprocessable("ATTACHMENT_REQUIRED", "Le certificat médical est obligatoire"); }
                    default -> {
                        if (blank(row.description())) throw unprocessable("REASON_REQUIRED", "Le motif est obligatoire");
                        if (pieces < 1) throw unprocessable("ATTACHMENT_REQUIRED", "Un justificatif est obligatoire");
                    }
                }
                return BigDecimal.valueOf(n);
            }
            case "ATTESTATION" -> {
                if (!ATTESTATIONS.contains(String.valueOf(donnees.get("typeAttestation")))) {
                    throw unprocessable("INVALID_ATTESTATION_TYPE", "Type d'attestation attendu : travail ou salaire");
                }
            }
            case "AVANCE_SALAIRE" -> verifierAvance(row, donnees, avert);
            case "FORMATION" -> {
                if (blank(String.valueOf(donnees.getOrDefault("intitule", "")).replace("null", ""))) throw unprocessable("TITLE_REQUIRED", "L'intitulé de la formation est obligatoire");
                requireDates(row);
                Object cout = donnees.get("cout");
                if (cout != null && new BigDecimal(cout.toString()).signum() < 0) throw unprocessable("INVALID_COST", "Le coût ne peut pas être négatif");
            }
            case "ORDRE_MISSION" -> {
                requireDates(row);
                for (String k : List.of("departDelegationId", "arrivalDelegationId")) {
                    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM delegation_province WHERE id = ? AND est_active = 1", Integer.class, String.valueOf(donnees.get(k)));
                    if (n == null || n == 0) throw unprocessable("INVALID_" + k.toUpperCase(), "Délégation invalide : " + k);
                }
                Integer t = jdbc.queryForObject("SELECT COUNT(*) FROM moyen_transport WHERE id = ? AND est_actif = 1", Integer.class, String.valueOf(donnees.get("moyenTransportId")));
                if (t == null || t == 0) throw unprocessable("INVALID_TRANSPORT", "Moyen de transport invalide");
            }
            default -> { if (blank(row.description())) throw unprocessable("DESCRIPTION_REQUIRED", "La description est obligatoire"); }
        }
        return null;
    }

    private void verifierAvance(DemandeRow row, Map<String, Object> donnees, List<String> avert) {
        BigDecimal montant;
        try { montant = new BigDecimal(String.valueOf(donnees.get("montant"))); }
        catch (NumberFormatException e) { throw unprocessable("INVALID_AMOUNT", "Montant invalide"); }
        if (montant.signum() <= 0) throw unprocessable("INVALID_AMOUNT", "Le montant doit être positif");
        LocalDate today = LocalDate.now(clock);
        BigDecimal plafond = params.decimal("avance.plafond_mad", today, null);
        if (plafond != null && montant.compareTo(plafond) > 0) {
            throw unprocessable("ADVANCE_CEILING_EXCEEDED", "Le montant dépasse le plafond de " + plafond.toPlainString() + " MAD");
        }
        BigDecimal mensualites = jdbc.queryForObject("SELECT COALESCE(SUM(mensualite),0) FROM credit WHERE personnel_id = ? AND statut = 'publie' AND nb_mois_restants > 0", BigDecimal.class, row.personnelId());
        List<BigDecimal> net = jdbc.queryForList("SELECT salaire_net FROM salaire WHERE personnel_id = ? AND statut = 'publie' ORDER BY annee DESC, mois DESC LIMIT 1", BigDecimal.class, row.personnelId());
        if (net.isEmpty() || net.get(0).signum() == 0) {
            avert.add("DEBT_CAPACITY_NOT_ASSESSED");
            return;
        }
        BigDecimal max = params.decimal("avance.endettement_max_pct", today, null);
        if (max != null && mensualites.multiply(BigDecimal.valueOf(100)).divide(net.get(0), 2, java.math.RoundingMode.HALF_UP).compareTo(max) > 0) {
            throw unprocessable("DEBT_CAPACITY_EXCEEDED", "La capacité d'endettement est dépassée");
        }
    }

    private void creerOrdreMission(DemandeRow row, Map<String, Object> d, LocalDateTime now) {
        String programme = d.get("programmeId") == null ? null : String.valueOf(d.get("programmeId"));
        jdbc.update("""
            INSERT INTO ordre_mission (id, personnel_id, programme_id, depart_delegation_id, arrival_delegation_id, moyen_transport_id,
                                       objet, date_depart, date_retour, annee, demande_id, statut, cree_le)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,'soumise',?)
            """, UUID.randomUUID().toString(), row.personnelId(), programme, String.valueOf(d.get("departDelegationId")),
                String.valueOf(d.get("arrivalDelegationId")), String.valueOf(d.get("moyenTransportId")), row.objet(),
                row.dateDebut().atStartOfDay(), row.dateFin().atStartOfDay(), row.dateDebut().getYear(), row.id(), now);
    }

    // ---- utilitaires ----

    private String requirePersonnel(AuthenticatedUser u) {
        if (u.personnelId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "NO_PERSONNEL", "Ce compte n'est lié à aucun agent");
        return u.personnelId();
    }

    private DemandeRow own(AuthenticatedUser u, String id) {
        DemandeRow row = repo.findById(id).orElseThrow(ApiException::notFound);
        if (!row.personnelId().equals(requirePersonnel(u))) throw ApiException.notFound();
        return row;
    }

    DemandeDto toDto(DemandeRow r, boolean detail, List<String> avert) {
        return new DemandeDto(r.id(), r.numero(), r.typeCode(), r.typeLibelle(), r.objet(), r.description(), r.statut(), r.dateDebut(),
                r.dateFin(), r.nbJours(), read(r.donneesJson()), r.commentaireResponsable(), r.commentaireRh(), r.creeLe(), r.modifieLe(),
                detail ? repo.historique(r.id()) : null, detail ? repo.pieces(r.id()) : null, avert == null || avert.isEmpty() ? null : avert,
                detail ? chaine.liste("demande", r.id()) : null);
    }

    private String libelleType(String code) {
        return jdbc.queryForObject("SELECT libelle FROM type_demande WHERE code = ?", String.class, code);
    }

    private void requireDates(DemandeRow r) {
        if (r.dateDebut() == null || r.dateFin() == null) throw unprocessable("DATES_REQUIRED", "Les dates de début et de fin sont obligatoires");
        checkDates(r.dateDebut(), r.dateFin());
    }

    private void checkDates(LocalDate d, LocalDate f) {
        if (d != null && f != null && f.isBefore(d)) throw unprocessable("INVALID_DATES", "La date de fin précède la date de début");
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static ApiException unprocessable(String code, String message) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message); }

    private String write(Map<String, Object> m) {
        try { return m == null ? null : json.writeValueAsString(m); } catch (JsonProcessingException e) { throw unprocessable("INVALID_DATA", "Données invalides"); }
    }

    private Map<String, Object> read(String s) {
        try { return s == null ? Map.of() : json.readValue(s, new TypeReference<Map<String, Object>>() {}); } catch (JsonProcessingException e) { return Map.of(); }
    }
}
