package ma.entraide.tawassol.rh;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * Campagne de notation côté RH : ouverture, contrôle des notes soumises par les responsables, renvoi pour correction, validation (verrouillage) puis
 * publication à l'agent. La RH ne valide pas sa propre note. Calcul d'éligibilité à l'avancement : phase 4B.
 */
@RestController
@RequestMapping("/api/v1/rh/notation")
@PreAuthorize("hasRole('RH')")
public class RhNotationController {
    public record Campagne(@NotNull Integer annee, @NotNull LocalDate dateOuverture, @NotNull LocalDate dateCloture, BigDecimal baremeMax) {}
    public record Saisie(@NotNull @DecimalMin("0.00") BigDecimal note, String appreciation, String observations) {}
    public record Commentaire(String commentaire) {}

    private final JdbcTemplate jdbc;
    private final NotificationService notifs;
    private final AuditService audit;
    private final Clock clock;

    public RhNotationController(JdbcTemplate jdbc, NotificationService notifs, AuditService audit, Clock clock) {
        this.jdbc = jdbc; this.notifs = notifs; this.audit = audit; this.clock = clock;
    }

    @GetMapping("/campagnes")
    public List<Map<String, Object>> campagnes() {
        return jdbc.queryForList("SELECT annee, date_ouverture AS dateOuverture, date_cloture AS dateCloture, bareme_max AS baremeMax, est_active AS active FROM campagne_notation ORDER BY annee DESC");
    }

    @PostMapping("/campagnes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> ouvrir(@AuthenticationPrincipal AuthenticatedUser u, @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid Campagne c) {
        if (c.dateCloture().isBefore(c.dateOuverture())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DATES", "La clôture précède l'ouverture");
        try { jdbc.update("INSERT INTO campagne_notation (id, annee, date_ouverture, date_cloture, bareme_max) VALUES (?,?,?,?,?)", UUID.randomUUID().toString(), c.annee(), c.dateOuverture(), c.dateCloture(), c.baremeMax() == null ? new BigDecimal("20.00") : c.baremeMax()); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new ApiException(HttpStatus.CONFLICT, "CAMPAIGN_EXISTS", "Une campagne existe déjà pour cette année"); }
        audit.record(u.userId(), "CAMPAGNE_OUVERTE", "campagne_notation", String.valueOf(c.annee()), null, null);
        return Map.of("annee", c.annee());
    }

    @PutMapping("/campagnes/{annee}")
    public Map<String, Object> modifier(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable int annee, @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid Campagne c) {
        if (c.dateCloture().isBefore(c.dateOuverture())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DATES", "La clôture précède l'ouverture");
        if (jdbc.update("UPDATE campagne_notation SET date_ouverture = ?, date_cloture = ?, bareme_max = COALESCE(?, bareme_max) WHERE annee = ?", c.dateOuverture(), c.dateCloture(), c.baremeMax(), annee) == 0) throw ApiException.notFound();
        audit.record(u.userId(), "CAMPAGNE_MODIFIEE", "campagne_notation", String.valueOf(annee), null, null);
        return Map.of("annee", annee);
    }

    @GetMapping("/{annee}/notes")
    public List<Map<String, Object>> notes(@PathVariable int annee, @RequestParam(defaultValue = "soumise") String statut) {
        return jdbc.queryForList("""
            SELECT n.personnel_id AS personnelId, p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr, s.nom_fr AS structureFr,
                   n.note, n.appreciation, n.observations, n.statut, n.commentaire_rh AS commentaireRh,
                   TRIM(CONCAT(COALESCE(r.prenom_fr,''), ' ', COALESCE(r.nom_fr,''))) AS saisiePar
              FROM note_annuelle n JOIN personnel p ON p.id = n.personnel_id JOIN structure s ON s.id = p.structure_id
              LEFT JOIN utilisateur uu ON uu.id = n.saisie_par LEFT JOIN personnel r ON r.id = uu.personnel_id
             WHERE n.annee = ? AND n.statut = ? ORDER BY p.nom_fr, p.prenom_fr
            """, annee, statut);
    }

    @PutMapping("/{annee}/notes/{pid}")
    @Transactional
    public Map<String, Object> saisir(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable int annee, @PathVariable String pid, @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid Saisie s) {
        BigDecimal max = jdbc.queryForList("SELECT bareme_max FROM campagne_notation WHERE annee = ?", BigDecimal.class, annee).stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CAMPAIGN_CLOSED", "Aucune campagne pour cette année"));
        if (s.note().compareTo(max) > 0) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NOTE_OUT_OF_RANGE", "La note dépasse le barème (" + max + ")");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE id = ? AND est_actif = 1", Integer.class, pid) == 0) throw ApiException.notFound();
        String statut = jdbc.queryForList("SELECT statut FROM note_annuelle WHERE personnel_id = ? AND annee = ?", String.class, pid, annee).stream().findFirst().orElse(null);
        if (statut == null) {
            jdbc.update("INSERT INTO note_annuelle (id, personnel_id, saisie_par, annee, note, appreciation, observations, statut) VALUES (?,?,?,?,?,?,?,'soumise')", UUID.randomUUID().toString(), pid, u.userId(), annee, s.note(), s.appreciation(), s.observations());
        } else {
            if (!List.of("brouillon", "soumise").contains(statut)) throw new ApiException(HttpStatus.CONFLICT, "NOTE_LOCKED", "Cette note est verrouillée");
            jdbc.update("UPDATE note_annuelle SET note = ?, appreciation = ?, observations = ?, saisie_par = ?, statut = 'soumise' WHERE personnel_id = ? AND annee = ?", s.note(), s.appreciation(), s.observations(), u.userId(), pid, annee);
        }
        audit.record(u.userId(), "NOTE_SAISIE_RH", "note_annuelle", pid + ":" + annee, null, null);
        return Map.of("statut", "soumise");
    }

    @PostMapping("/{annee}/notes/{pid}/valider")
    @Transactional
    public Map<String, Object> valider(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable int annee, @PathVariable String pid) {
        if (pid.equals(u.personnelId())) throw new ApiException(HttpStatus.FORBIDDEN, "SELF_VALIDATION_FORBIDDEN", "Vous ne pouvez pas valider votre propre note");
        String statut = statut(pid, annee);
        if ("validee".equals(statut)) return Map.of("statut", "validee");
        if (!"soumise".equals(statut)) throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Seule une note soumise peut être validée");
        jdbc.update("UPDATE note_annuelle SET statut = 'validee', valide_par = ?, date_validation = ? WHERE personnel_id = ? AND annee = ?", u.userId(), LocalDateTime.now(clock), pid, annee);
        audit.record(u.userId(), "NOTE_VALIDEE", "note_annuelle", pid + ":" + annee, null, null);
        return Map.of("statut", "validee");
    }

    @PostMapping("/{annee}/notes/{pid}/renvoyer")
    @Transactional
    public Map<String, Object> renvoyer(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable int annee, @PathVariable String pid, @RequestBody(required = false) Commentaire c) {
        if (c == null || c.commentaire() == null || c.commentaire().isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COMMENT_REQUIRED", "Un commentaire est obligatoire pour renvoyer une note");
        if (!"soumise".equals(statut(pid, annee))) throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Seule une note soumise peut être renvoyée");
        String rater = jdbc.queryForList("SELECT saisie_par FROM note_annuelle WHERE personnel_id = ? AND annee = ?", String.class, pid, annee).stream().findFirst().orElse(null);
        jdbc.update("UPDATE note_annuelle SET statut = 'brouillon', commentaire_rh = ? WHERE personnel_id = ? AND annee = ?", c.commentaire(), pid, annee);
        audit.record(u.userId(), "NOTE_RENVOYEE", "note_annuelle", pid + ":" + annee, null, null);
        notifs.notifier(rater, "NOTE_RENVOYEE", "Note renvoyée pour correction", c.commentaire(), "/responsable/notation/" + pid);
        return Map.of("statut", "brouillon");
    }

    /** Publie à leurs agents toutes les notes validées de l'année. */
    @PostMapping("/{annee}/publier")
    @Transactional
    public Map<String, Object> publier(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable int annee) {
        LocalDateTime now = LocalDateTime.now(clock);
        int n = jdbc.update("UPDATE note_annuelle SET statut = 'publiee', publie_le = ? WHERE annee = ? AND statut = 'validee'", now, annee);
        jdbc.update("INSERT INTO notification (id, utilisateur_id, type, titre, message, lien, cree_le) SELECT UUID(), uu.id, 'NOTE_PUBLIEE', 'Votre note annuelle est publiée', ?, '/note', ? FROM note_annuelle n JOIN utilisateur uu ON uu.personnel_id = n.personnel_id AND uu.est_actif = 1 WHERE n.annee = ? AND n.publie_le = ?", String.valueOf(annee), now, annee, now);
        audit.record(u.userId(), "NOTES_PUBLIEES", "campagne_notation", String.valueOf(annee), null, "{\"notes\":" + n + "}");
        return Map.of("publiees", n);
    }

    /** Taux de notation par structure : agents actifs et notes déjà soumises (ou au-delà). */
    @GetMapping("/{annee}/taux")
    public List<Map<String, Object>> taux(@PathVariable int annee) {
        return jdbc.queryForList("""
            SELECT s.id AS structureId, s.code, s.nom_fr AS nomFr, s.nom_ar AS nomAr, COUNT(p.id) AS agents,
                   SUM(CASE WHEN n.statut IN ('soumise','validee','publiee') THEN 1 ELSE 0 END) AS notes
              FROM structure s JOIN personnel p ON p.structure_id = s.id AND p.est_actif = 1
              LEFT JOIN note_annuelle n ON n.personnel_id = p.id AND n.annee = ?
             WHERE s.est_active = 1 GROUP BY s.id, s.code, s.nom_fr, s.nom_ar ORDER BY s.code
            """, annee);
    }

    private String statut(String pid, int annee) {
        return jdbc.queryForList("SELECT statut FROM note_annuelle WHERE personnel_id = ? AND annee = ?", String.class, pid, annee).stream().findFirst().orElseThrow(ApiException::notFound);
    }
}
