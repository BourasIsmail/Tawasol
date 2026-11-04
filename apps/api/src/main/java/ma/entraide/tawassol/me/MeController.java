package ma.entraide.tawassol.me;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.demande.SoldeCongeService;
import ma.entraide.tawassol.notification.NotificationService;
import ma.entraide.tawassol.reclamation.ReclamationController;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Espace personnel de l'agent : confirmation de fiche, signalement d'erreur, note annuelle, tableau de bord. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    public record ErreurFiche(@NotBlank @Size(max = 60) String champ, @Size(max = 250) String valeurProposee, @NotBlank String description) {}
    public record Observations(@NotBlank String observations) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ReclamationController reclamations;
    private final SoldeCongeService soldes;
    private final NotificationService notifs;
    private final Clock clock;

    public MeController(JdbcTemplate jdbc, AuditService audit, ReclamationController reclamations, SoldeCongeService soldes, NotificationService notifs, Clock clock) {
        this.jdbc = jdbc; this.audit = audit; this.reclamations = reclamations; this.soldes = soldes; this.notifs = notifs; this.clock = clock;
    }

    /** « Tout est correct » : la confirmation est horodatée dans le journal d'audit. */
    @PostMapping("/fiche/confirmer")
    public Map<String, Object> confirmer(@AuthenticationPrincipal AuthenticatedUser u) {
        pid(u);
        audit.record(u.userId(), "FICHE_CONFIRMEE", "personnel", u.personnelId(), null, null);
        return Map.of("confirmeLe", LocalDateTime.now(clock));
    }

    /** « Signaler une erreur » : crée une réclamation de type correction de données (droit de rectification, loi 09-08). */
    @PostMapping("/fiche/signaler-erreur")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signaler(@AuthenticationPrincipal AuthenticatedUser u, @Valid @RequestBody ErreurFiche req) {
        pid(u);
        String id = reclamations.insert(u, "correction_donnees", req.champ(), req.valeurProposee(), "Correction de données : " + req.champ(), req.description());
        return Map.of("reclamationId", id);
    }

    @GetMapping("/notes")
    public List<Map<String, Object>> notes(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("SELECT CAST(annee AS UNSIGNED) AS annee, note, appreciation, date_validation AS dateValidation, observations_agent AS observationsAgent FROM note_annuelle WHERE personnel_id = ? AND statut = 'publiee' ORDER BY annee DESC", pid(u));
    }

    @PutMapping("/notes/{annee}/observations")
    public Map<String, Object> observations(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable int annee, @Valid @RequestBody Observations req) {
        int n = jdbc.update("UPDATE note_annuelle SET observations_agent = ? WHERE personnel_id = ? AND annee = ? AND statut = 'publiee'", req.observations(), pid(u), annee);
        if (n == 0) throw ApiException.notFound();
        audit.record(u.userId(), "NOTE_OBSERVATIONS", "note_annuelle", String.valueOf(annee), null, null);
        return Map.of("annee", annee, "enregistre", true);
    }

    @GetMapping("/tableau-de-bord")
    public Map<String, Object> tableauDeBord(@AuthenticationPrincipal AuthenticatedUser u) {
        String pid = pid(u);
        LocalDate today = LocalDate.now(clock);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("soldeConges", soldes.get(pid, today.getYear(), null));
        out.put("soldeAnneePrecedente", soldes.get(pid, today.getYear() - 1, null));
        out.put("demandesEnCours", jdbc.queryForObject("SELECT COUNT(*) FROM demande WHERE personnel_id = ? AND statut IN ('soumise','validee_responsable')", Integer.class, pid));
        out.put("notificationsNonLues", notifs.nonLues(u.userId()));
        out.put("dernierBulletin", jdbc.queryForList("SELECT annee, mois FROM salaire WHERE personnel_id = ? AND statut = 'publie' ORDER BY annee DESC, mois DESC LIMIT 1", pid).stream().findFirst().orElse(null));
        out.put("derniereNote", jdbc.queryForList("SELECT CAST(annee AS UNSIGNED) AS annee, note, appreciation FROM note_annuelle WHERE personnel_id = ? AND statut = 'publiee' ORDER BY annee DESC LIMIT 1", pid).stream().findFirst().orElse(null));
        out.put("ficheConfirmeeLe", jdbc.queryForObject("SELECT MAX(cree_le) FROM journal_audit WHERE utilisateur_id = ? AND action = 'FICHE_CONFIRMEE'", LocalDateTime.class, u.userId()));
        return out;
    }

    private String pid(AuthenticatedUser u) {
        if (u.personnelId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "NO_PERSONNEL", "Ce compte n'est lié à aucun agent");
        return u.personnelId();
    }
}
