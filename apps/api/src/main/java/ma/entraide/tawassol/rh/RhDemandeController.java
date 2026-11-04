package ma.entraide.tawassol.rh;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.demande.DemandeModels.DemandeRow;
import ma.entraide.tawassol.demande.DemandeRepository;
import ma.entraide.tawassol.demande.SoldeCongeService;
import ma.entraide.tawassol.notification.NotificationService;
import ma.entraide.tawassol.param.ParametreService;
import ma.entraide.tawassol.personnel.PageResult;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Second niveau de traitement des demandes (RH). Reçoit les demandes validées par un responsable et celles qui n'ont pas de responsable.
 * Une demande dont le responsable n'a pas encore décidé n'est pas traitable par la RH. La RH ne traite jamais sa propre demande.
 */
@RestController
@RequestMapping("/api/v1/rh/demandes")
@PreAuthorize("hasRole('RH')")
public class RhDemandeController {
    public record Decision(String commentaire) {}

    private static final String A_TRAITER = "(d.statut = 'validee_responsable' OR (d.statut = 'soumise' AND d.responsable_personnel_id IS NULL))";

    private final NamedParameterJdbcTemplate jdbc;
    private final DemandeRepository demandes;
    private final SoldeCongeService soldes;
    private final NotificationService notifs;
    private final ParametreService params;
    private final AuditService audit;
    private final Clock clock;

    public RhDemandeController(NamedParameterJdbcTemplate jdbc, DemandeRepository demandes, SoldeCongeService soldes, NotificationService notifs, ParametreService params, AuditService audit, Clock clock) {
        this.jdbc = jdbc; this.demandes = demandes; this.soldes = soldes; this.notifs = notifs; this.params = params; this.audit = audit; this.clock = clock;
    }

    @GetMapping
    public PageResult<Map<String, Object>> queue(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(defaultValue = "a_traiter") String statut, @RequestParam(required = false) String type,
                                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        LocalDate today = LocalDate.now(clock);
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("me", u.personnelId() == null ? "-" : u.personnelId()).addValue("today", today);
        String where = switch (statut) {
            case "a_traiter" -> " WHERE " + A_TRAITER;
            case "approuvee", "rejetee" -> " WHERE d.statut = '" + statut + "'";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Statut invalide");
        };
        where += " AND d.personnel_id <> :me";
        if (type != null && !type.isBlank()) { where += " AND t.code = :type"; p.addValue("type", type); }
        String from = " FROM demande d JOIN personnel p ON p.id = d.personnel_id JOIN type_demande t ON t.id = d.type_demande_id" + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, p, Long.class);
        int delai = params.integer("demande.delai_reponse_jours", today, 5);
        p.addValue("limit", size).addValue("offset", (long) Math.max(page, 0) * size).addValue("delai", delai);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT d.id, d.numero, t.code AS typeCode, t.libelle AS typeLibelle, d.statut, d.date_debut AS dateDebut, d.date_fin AS dateFin, d.nb_jours AS nbJours,
                   p.id AS agentId, p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr,
                   CASE WHEN d.statut = 'validee_responsable' THEN 'responsable' ELSE 'direct' END AS circuit,
                   DATEDIFF(:today, COALESCE(d.date_validation_responsable, d.cree_le)) AS ancienneteJours,
                   DATEDIFF(:today, COALESCE(d.date_validation_responsable, d.cree_le)) > :delai AS horsDelai
            """ + from + " ORDER BY COALESCE(d.date_validation_responsable, d.cree_le), d.id LIMIT :limit OFFSET :offset", p);
        rows.forEach(r -> r.put("horsDelai", r.get("horsDelai") instanceof Boolean b ? b : r.get("horsDelai") instanceof Number n && n.intValue() != 0));
        return new PageResult<>(rows, total == null ? 0 : total, Math.max(page, 0), size);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        DemandeRow d = demandes.findById(id).orElseThrow(ApiException::notFound);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", d.id()); out.put("numero", d.numero()); out.put("typeCode", d.typeCode()); out.put("typeLibelle", d.typeLibelle()); out.put("statut", d.statut());
        out.put("objet", d.objet()); out.put("description", d.description()); out.put("dateDebut", d.dateDebut()); out.put("dateFin", d.dateFin()); out.put("nbJours", d.nbJours());
        out.put("donneesJson", d.donneesJson()); out.put("commentaireResponsable", d.commentaireResponsable()); out.put("commentaireRh", d.commentaireRh());
        out.put("traitable", traitable(id));
        out.put("agent", jdbc.queryForMap("SELECT p.id, p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr, g.libelle_fr AS gradeFr, s.nom_fr AS structureFr, s.nom_ar AS structureAr FROM personnel p JOIN structure s ON s.id = p.structure_id LEFT JOIN grade g ON g.id = p.grade_id WHERE p.id = :p", new MapSqlParameterSource("p", d.personnelId())));
        if ("CONGE_ANNUEL".equals(d.typeCode()) && d.dateDebut() != null) {
            var s = soldes.get(d.personnelId(), d.dateDebut().getYear(), d.id());
            out.put("solde", Map.of("annee", s.annee(), "disponible", s.disponible(), "suffisant", d.nbJours() == null || s.disponible().compareTo(d.nbJours()) >= 0));
        }
        out.put("historique", demandes.historique(id));
        out.put("piecesJointes", demandes.pieces(id));
        return out;
    }

    @PostMapping("/{id}/approuver")
    @Transactional
    public Map<String, Object> approuver(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody(required = false) Decision dec) {
        return decider(u, id, true, dec == null ? null : dec.commentaire());
    }

    @PostMapping("/{id}/rejeter")
    @Transactional
    public Map<String, Object> rejeter(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody(required = false) Decision dec) {
        return decider(u, id, false, dec == null ? null : dec.commentaire());
    }

    private Map<String, Object> decider(AuthenticatedUser u, String id, boolean approuver, String commentaire) {
        DemandeRow d = demandes.findById(id).orElseThrow(ApiException::notFound);
        if (d.personnelId().equals(u.personnelId())) throw new ApiException(HttpStatus.FORBIDDEN, "SELF_VALIDATION_FORBIDDEN", "Vous ne pouvez pas traiter votre propre demande");
        String cible = approuver ? "approuvee" : "rejetee";
        if (cible.equals(d.statut())) return Map.of("id", id, "statut", d.statut());
        if (!traitable(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "soumise".equals(d.statut()) ? "Cette demande attend la décision du responsable" : "Cette demande n'est pas traitable");
        }
        if (!approuver && (commentaire == null || commentaire.isBlank())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COMMENT_REQUIRED", "Un commentaire est obligatoire pour rejeter une demande");
        if (approuver && "ORDRE_MISSION".equals(d.typeCode())) throw new ApiException(HttpStatus.CONFLICT, "USE_MISSION_MODULE", "Les ordres de mission sont approuvés depuis le module Missions (calcul de l'indemnité)");
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("UPDATE demande SET statut = :s, traite_par = :u, date_traitement = :t, commentaire_rh = :c WHERE id = :id", new MapSqlParameterSource("s", cible).addValue("u", u.userId()).addValue("t", now).addValue("c", commentaire).addValue("id", id));
        demandes.addHistorique(id, u.userId(), d.statut(), cible, commentaire, now);
        if (approuver && "CONGE_ANNUEL".equals(d.typeCode()) && d.nbJours() != null && d.dateDebut() != null) {
            soldes.deduire(d.personnelId(), d.dateDebut().getYear(), d.nbJours());
        }
        audit.record(u.userId(), approuver ? "DEMANDE_APPROUVEE" : "DEMANDE_REJETEE_RH", "demande", id, null, null);
        notifs.notifierAgent(d.personnelId(), approuver ? "DEMANDE_APPROUVEE" : "DEMANDE_REJETEE", approuver ? "Votre demande est approuvée" : "Votre demande est rejetée",
                d.typeLibelle() + " · " + d.numero() + (approuver || commentaire == null ? "" : " : " + commentaire), "/demandes/" + id);
        return Map.of("id", id, "statut", cible);
    }

    private boolean traitable(String id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM demande d WHERE d.id = :id AND " + A_TRAITER, new MapSqlParameterSource("id", id), Integer.class);
        return n != null && n > 0;
    }
}
