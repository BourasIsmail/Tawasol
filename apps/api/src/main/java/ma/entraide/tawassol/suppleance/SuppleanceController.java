package ma.entraide.tawassol.suppleance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.access.AccessGuard;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.notification.NotificationService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Suppléance d'un responsable. Seul un responsable ayant un périmètre actif peut désigner un suppléant, qui doit appartenir à son
 * périmètre. Le suppléant n'acquiert que la file de validation et la notation de ce périmètre, jamais le droit de traiter sa propre demande.
 */
@RestController
@RequestMapping("/api/v1/suppleances")
public class SuppleanceController {
    public record SuppleanceRequest(@NotBlank String suppleantId, @NotNull LocalDate dateDebut, @NotNull LocalDate dateFin) {}

    private final JdbcTemplate jdbc;
    private final AccessGuard guard;
    private final NotificationService notifs;
    private final AuditService audit;
    private final Clock clock;

    public SuppleanceController(JdbcTemplate jdbc, AccessGuard guard, NotificationService notifs, AuditService audit, Clock clock) {
        this.jdbc = jdbc; this.guard = guard; this.notifs = notifs; this.audit = audit; this.clock = clock;
    }

    @GetMapping
    public Map<String, Object> mine(@AuthenticationPrincipal AuthenticatedUser u) {
        String sel = "SELECT s.id, s.date_debut AS dateDebut, s.date_fin AS dateFin, s.est_actif AS actif, p.id AS personnelId, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr FROM suppleance s JOIN personnel p ON p.id = ";
        String me = u.personnelId() == null ? "-" : u.personnelId();
        return Map.of("designes", jdbc.queryForList(sel + "s.suppleant_id WHERE s.responsable_id = ? ORDER BY s.date_debut DESC", me),
                "aSupplier", jdbc.queryForList(sel + "s.responsable_id WHERE s.suppleant_id = ? AND s.est_actif = 1 ORDER BY s.date_debut DESC", me));
    }

    @GetMapping("/candidats")
    public List<Map<String, Object>> candidats(@AuthenticationPrincipal AuthenticatedUser u) {
        var scope = guard.personnelScope(u);
        if (!estResponsable(u) || scope.structureIds().isEmpty()) return List.of();
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).queryForList(
                "SELECT id, nom_fr AS nomFr, prenom_fr AS prenomFr, nom_ar AS nomAr, prenom_ar AS prenomAr FROM personnel WHERE est_actif = 1 AND structure_id IN (:sids) AND id <> :me ORDER BY nom_fr",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("sids", scope.structureIds()).addValue("me", u.personnelId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedUser u, @Valid @RequestBody SuppleanceRequest req) {
        if (!estResponsable(u)) throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Réservé aux responsables");
        if (req.dateFin().isBefore(req.dateDebut())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DATES", "La date de fin précède la date de début");
        if (req.dateFin().isBefore(LocalDate.now(clock))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DATES", "La période est déjà passée");
        if (req.suppleantId().equals(u.personnelId())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SUPPLEANT", "Vous ne pouvez pas être votre propre suppléant");
        if (!guard.personnelScope(u).covers(req.suppleantId(), jdbc.queryForList("SELECT structure_id FROM personnel WHERE id = ? AND est_actif = 1", String.class, req.suppleantId()).stream().findFirst().orElse(null))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SUPPLEANT", "Le suppléant doit appartenir à votre périmètre");
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO suppleance (id, responsable_id, suppleant_id, date_debut, date_fin) VALUES (?,?,?,?,?)", id, u.personnelId(), req.suppleantId(), req.dateDebut(), req.dateFin());
        audit.record(u.userId(), "SUPPLEANCE_CREEE", "suppleance", id, null, null);
        notifs.notifierAgent(req.suppleantId(), "SUPPLEANCE", "Vous êtes désigné suppléant", "Du " + req.dateDebut() + " au " + req.dateFin(), "/responsable/validation");
        return Map.of("id", id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        int n = jdbc.update("UPDATE suppleance SET est_actif = 0 WHERE id = ? AND responsable_id = ?", id, u.personnelId());
        if (n == 0) throw ApiException.notFound();
        audit.record(u.userId(), "SUPPLEANCE_TERMINEE", "suppleance", id, null, null);
    }

    private boolean estResponsable(AuthenticatedUser u) {
        if (u.personnelId() == null) return false;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM responsable_perimetre WHERE personnel_id = ? AND est_actif = 1 AND date_debut <= ? AND (date_fin IS NULL OR date_fin >= ?)",
                Integer.class, u.personnelId(), LocalDate.now(clock), LocalDate.now(clock));
        return n != null && n > 0;
    }
}
