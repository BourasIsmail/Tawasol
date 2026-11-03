package ma.entraide.tawassol.rh;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.notification.NotificationService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rh/reclamations")
@PreAuthorize("hasRole('RH')")
public class RhReclamationController {
    public record Reponse(String reponse, String statut) {}

    private final JdbcTemplate jdbc;
    private final NotificationService notifs;
    private final AuditService audit;
    private final Clock clock;

    public RhReclamationController(JdbcTemplate jdbc, NotificationService notifs, AuditService audit, Clock clock) {
        this.jdbc = jdbc; this.notifs = notifs; this.audit = audit; this.clock = clock;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String statut) {
        String sql = "SELECT r.id, r.type_reclamation AS typeReclamation, r.champ_concerne AS champConcerne, r.valeur_proposee AS valeurProposee, r.objet, r.description, r.statut, r.reponse, r.cree_le AS creeLe, p.id AS personnelId, p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr FROM reclamation r JOIN personnel p ON p.id = r.personnel_id";
        return statut == null || statut.isBlank() ? jdbc.queryForList(sql + " ORDER BY r.cree_le DESC, r.id LIMIT 200") : jdbc.queryForList(sql + " WHERE r.statut = ? ORDER BY r.cree_le DESC, r.id LIMIT 200", statut);
    }

    @PostMapping("/{id}/prendre")
    @Transactional
    public Map<String, Object> prendre(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        String st = statut(id);
        if ("en_cours".equals(st)) return Map.of("statut", st);
        if (!"nouvelle".equals(st)) throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Cette réclamation est déjà clôturée");
        jdbc.update("UPDATE reclamation SET statut = 'en_cours', traite_par = ? WHERE id = ?", u.userId(), id);
        audit.record(u.userId(), "RECLAMATION_PRISE", "reclamation", id, null, null);
        return Map.of("statut", "en_cours");
    }

    @PostMapping("/{id}/repondre")
    @Transactional
    public Map<String, Object> repondre(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody Reponse r) {
        if (r == null || r.reponse() == null || r.reponse().isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COMMENT_REQUIRED", "La réponse est obligatoire");
        if (!List.of("resolue", "rejetee").contains(r.statut())) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Statut attendu : resolue ou rejetee");
        String st = statut(id);
        if (!List.of("nouvelle", "en_cours").contains(st)) throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Cette réclamation est déjà clôturée");
        jdbc.update("UPDATE reclamation SET statut = ?, reponse = ?, traite_par = ?, date_traitement = ? WHERE id = ?", r.statut(), r.reponse(), u.userId(), LocalDateTime.now(clock), id);
        String pid = jdbc.queryForObject("SELECT personnel_id FROM reclamation WHERE id = ?", String.class, id);
        notifs.notifierAgent(pid, "RECLAMATION_REPONSE", "Réponse à votre réclamation", r.reponse(), "/reclamations");
        audit.record(u.userId(), "RECLAMATION_CLOTUREE", "reclamation", id, null, null);
        return Map.of("statut", r.statut());
    }

    private String statut(String id) { return jdbc.queryForList("SELECT statut FROM reclamation WHERE id = ?", String.class, id).stream().findFirst().orElseThrow(ApiException::notFound); }
}
