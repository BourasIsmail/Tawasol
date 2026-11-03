package ma.entraide.tawassol.reclamation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.notification.NotificationService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reclamations")
public class ReclamationController {
    public record ReclamationRequest(@NotBlank @Size(max = 500) String objet, @NotBlank String description) {}

    private static final String COLS = "id, type_reclamation AS typeReclamation, champ_concerne AS champConcerne, valeur_proposee AS valeurProposee, objet, description, statut, reponse, cree_le AS creeLe, date_traitement AS dateTraitement";

    private final JdbcTemplate jdbc;
    private final NotificationService notifs;
    private final Clock clock;

    public ReclamationController(JdbcTemplate jdbc, NotificationService notifs, Clock clock) {
        this.jdbc = jdbc; this.notifs = notifs; this.clock = clock;
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("SELECT " + COLS + " FROM reclamation WHERE personnel_id = ? ORDER BY cree_le DESC, id", pid(u));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        return jdbc.queryForList("SELECT " + COLS + " FROM reclamation WHERE id = ? AND personnel_id = ?", id, pid(u)).stream().findFirst().orElseThrow(ApiException::notFound);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedUser u, @Valid @RequestBody ReclamationRequest req) {
        String id = insert(u, "generale", null, null, req.objet().trim(), req.description());
        return get(u, id);
    }

    public String insert(AuthenticatedUser u, String type, String champ, String valeur, String objet, String description) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO reclamation (id, personnel_id, type_reclamation, champ_concerne, valeur_proposee, objet, description, cree_le) VALUES (?,?,?,?,?,?,?,?)",
                id, pid(u), type, champ, valeur, objet, description, LocalDateTime.now(clock));
        notifs.notifier(u.userId(), "RECLAMATION_CREEE", "Réclamation enregistrée", objet, "/reclamations/" + id);
        return id;
    }

    private String pid(AuthenticatedUser u) {
        if (u.personnelId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "NO_PERSONNEL", "Ce compte n'est lié à aucun agent");
        return u.personnelId();
    }
}
