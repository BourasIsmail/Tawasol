package ma.entraide.tawassol.access;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Calcule les structures qu'un responsable couvre : ses périmètres actifs (structure, délégation ou coordination),
 * étendus récursivement aux structures enfants, plus ceux des responsables qu'il supplée aujourd'hui.
 */
@Service
public class PerimeterService {
    private static final String STRUCTURES_SQL = """
        WITH RECURSIVE cibles AS (
            SELECT s.id FROM structure s
              JOIN responsable_perimetre rp ON rp.type_perimetre = 'structure' AND rp.structure_id = s.id
             WHERE rp.personnel_id IN (:pids) AND rp.est_actif = 1 AND rp.date_debut <= :today
               AND (rp.date_fin IS NULL OR rp.date_fin >= :today)
            UNION
            SELECT s.id FROM structure s
              JOIN responsable_perimetre rp ON rp.type_perimetre = 'delegation' AND rp.delegation_id = s.delegation_id
             WHERE rp.personnel_id IN (:pids) AND rp.est_actif = 1 AND rp.date_debut <= :today
               AND (rp.date_fin IS NULL OR rp.date_fin >= :today)
            UNION
            SELECT s.id FROM structure s
              JOIN delegation_province d ON d.id = s.delegation_id
              JOIN responsable_perimetre rp ON rp.type_perimetre = 'coordination' AND rp.coordination_id = d.coordination_id
             WHERE rp.personnel_id IN (:pids) AND rp.est_actif = 1 AND rp.date_debut <= :today
               AND (rp.date_fin IS NULL OR rp.date_fin >= :today)
        ), arbre AS (
            SELECT id FROM cibles
            UNION
            SELECT c.id FROM structure c JOIN arbre a ON c.parent_id = a.id
        )
        SELECT DISTINCT id FROM arbre
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public PerimeterService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Le responsable lui-même (si includeOwn) et les responsables qu'il supplée aujourd'hui. */
    public Set<String> effectivePersonnelIds(String personnelId, boolean includeOwn) {
        Set<String> ids = new HashSet<>();
        if (includeOwn) ids.add(personnelId);
        ids.addAll(jdbc.queryForList("""
            SELECT responsable_id FROM suppleance
             WHERE suppleant_id = :pid AND est_actif = 1 AND :today BETWEEN date_debut AND date_fin
            """, Map.of("pid", personnelId, "today", LocalDate.now(clock)), String.class));
        return ids;
    }

    /** includeOwn = false pour un agent : seule une suppléance en cours lui ouvre un périmètre. */
    public Set<String> structureIdsInPerimeter(String personnelId, boolean includeOwn) {
        if (personnelId == null) return Set.of();
        Set<String> pids = effectivePersonnelIds(personnelId, includeOwn);
        if (pids.isEmpty()) return Set.of();
        List<String> ids = jdbc.queryForList(STRUCTURES_SQL, new MapSqlParameterSource()
                .addValue("pids", pids).addValue("today", LocalDate.now(clock)), String.class);
        return new HashSet<>(ids);
    }
}
