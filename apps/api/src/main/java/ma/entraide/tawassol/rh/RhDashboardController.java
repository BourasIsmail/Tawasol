package ma.entraide.tawassol.rh;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.param.ParametreService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rh")
@PreAuthorize("hasRole('RH')")
public class RhDashboardController {
    private final JdbcTemplate jdbc;
    private final ParametreService params;
    private final Clock clock;

    public RhDashboardController(JdbcTemplate jdbc, ParametreService params, Clock clock) {
        this.jdbc = jdbc; this.params = params; this.clock = clock;
    }

    @GetMapping("/tableau-de-bord")
    public Map<String, Object> dashboard(@AuthenticationPrincipal AuthenticatedUser u) {
        LocalDate today = LocalDate.now(clock);
        int delai = params.integer("demande.delai_reponse_jours", today, 5);
        String me = u.personnelId() == null ? "-" : u.personnelId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("aTraiter", jdbc.queryForObject("SELECT COUNT(*) FROM demande d WHERE (d.statut = 'validee_responsable' OR (d.statut = 'soumise' AND d.responsable_personnel_id IS NULL)) AND d.personnel_id <> ?", Integer.class, me));
        out.put("horsDelai", jdbc.queryForObject("SELECT COUNT(*) FROM demande d WHERE (d.statut = 'validee_responsable' OR (d.statut = 'soumise' AND d.responsable_personnel_id IS NULL)) AND d.personnel_id <> ? AND DATEDIFF(?, COALESCE(d.date_validation_responsable, d.cree_le)) > ?", Integer.class, me, today, delai));
        out.put("effectif", jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE est_actif = 1", Integer.class));
        out.put("dossiersIncomplets", jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE est_actif = 1 AND (email IS NULL OR email = '' OR grade_id IS NULL)", Integer.class));
        out.put("reclamationsOuvertes", jdbc.queryForObject("SELECT COUNT(*) FROM reclamation WHERE statut IN ('nouvelle','en_cours')", Integer.class));
        out.put("lotsEnBrouillon", jdbc.queryForObject("SELECT COUNT(*) FROM import_lot WHERE statut = 'brouillon'", Integer.class));
        out.put("notesASoumettre", jdbc.queryForObject("SELECT COUNT(*) FROM note_annuelle WHERE statut = 'soumise'", Integer.class));
        out.put("demandesParStatut", jdbc.queryForList("SELECT statut, COUNT(*) AS n FROM demande WHERE statut <> 'brouillon' GROUP BY statut"));
        return out;
    }
}
