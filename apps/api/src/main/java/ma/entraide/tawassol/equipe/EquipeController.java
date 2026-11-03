package ma.entraide.tawassol.equipe;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.access.AccessGuard;
import ma.entraide.tawassol.access.Scope;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Calendrier d'équipe : absences et missions du périmètre. Le motif des congés maladie n'est jamais exposé (genre « absence »). */
@RestController
@RequestMapping("/api/v1/equipe")
public class EquipeController {
    private final NamedParameterJdbcTemplate jdbc;
    private final AccessGuard guard;

    public EquipeController(NamedParameterJdbcTemplate jdbc, AccessGuard guard) {
        this.jdbc = jdbc;
        this.guard = guard;
    }

    @GetMapping("/calendrier")
    public Map<String, Object> calendrier(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam LocalDate du, @RequestParam LocalDate au) {
        if (au.isBefore(du) || du.plusDays(92).isBefore(au)) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Période invalide (93 jours maximum)");
        Scope scope = guard.personnelScope(u);
        if (scope.structureIds().isEmpty()) return Map.of("membres", List.of(), "evenements", List.of(), "feries", feries(du, au));
        MapSqlParameterSource p = new MapSqlParameterSource("sids", scope.structureIds()).addValue("me", u.personnelId()).addValue("du", du).addValue("au", au);
        List<Map<String, Object>> membres = jdbc.queryForList("SELECT id, nom_fr AS nomFr, prenom_fr AS prenomFr, nom_ar AS nomAr, prenom_ar AS prenomAr FROM personnel WHERE est_actif = 1 AND structure_id IN (:sids) AND id <> :me ORDER BY nom_fr, prenom_fr", p);
        List<Map<String, Object>> ev = jdbc.queryForList("""
            SELECT d.personnel_id AS personnelId, d.date_debut AS dateDebut, d.date_fin AS dateFin, d.statut,
                   CASE t.code WHEN 'CONGE_ANNUEL' THEN 'conge' WHEN 'ORDRE_MISSION' THEN 'mission' ELSE 'absence' END AS genre
              FROM demande d JOIN personnel p ON p.id = d.personnel_id JOIN type_demande t ON t.id = d.type_demande_id
             WHERE p.structure_id IN (:sids) AND p.est_actif = 1 AND p.id <> :me AND t.code IN ('CONGE_ANNUEL','CONGE_MALADIE','CONGE_EXCEPTION','ORDRE_MISSION')
               AND d.statut IN ('soumise','validee_responsable','approuvee') AND d.date_debut <= :au AND d.date_fin >= :du
             ORDER BY d.date_debut
            """, p);
        return Map.of("membres", membres, "evenements", ev, "feries", feries(du, au));
    }

    private List<Map<String, Object>> feries(LocalDate du, LocalDate au) {
        return jdbc.queryForList("SELECT date_ferie AS date, libelle_fr AS libelleFr, libelle_ar AS libelleAr FROM jour_ferie WHERE date_ferie BETWEEN :du AND :au ORDER BY date_ferie",
                new MapSqlParameterSource("du", du).addValue("au", au));
    }
}
