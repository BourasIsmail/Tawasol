package ma.entraide.tawassol.personnel;

import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.access.AccessGuard;
import ma.entraide.tawassol.access.Scope;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/structures")
public class StructureController {
    private final NamedParameterJdbcTemplate jdbc;
    private final AccessGuard guard;

    public StructureController(NamedParameterJdbcTemplate jdbc, AccessGuard guard) {
        this.jdbc = jdbc;
        this.guard = guard;
    }

    /** Structures du périmètre de l'utilisateur (toutes pour la RH), avec le nombre d'agents actifs. */
    @GetMapping("/mine")
    public List<Map<String, Object>> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        Scope scope = guard.personnelScope(user);
        String base = """
            SELECT st.id, st.code, st.type, st.nom_fr AS nomFr, st.nom_ar AS nomAr, st.parent_id AS parentId,
                   (SELECT COUNT(*) FROM personnel p WHERE p.structure_id = st.id AND p.est_actif = 1) AS effectif
              FROM structure st WHERE st.est_active = 1
            """;
        if (scope.all()) return jdbc.queryForList(base + " ORDER BY st.code", new MapSqlParameterSource());
        if (scope.structureIds().isEmpty()) return List.of();
        return jdbc.queryForList(base + " AND st.id IN (:ids) ORDER BY st.code", new MapSqlParameterSource("ids", scope.structureIds()));
    }
}
