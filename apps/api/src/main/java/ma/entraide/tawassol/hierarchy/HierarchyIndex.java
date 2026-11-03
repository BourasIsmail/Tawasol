package ma.entraide.tawassol.hierarchy;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ma.entraide.tawassol.config.TawassolProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Vue en mémoire de la hiérarchie (structures et périmètres actifs) pour trouver le responsable compétent d'une structure.
 * Ordre de recherche : structure, puis ses parents de proche en proche, puis la délégation, puis la coordination.
 * Le responsable lui-même est écarté : sa demande ou sa note remonte au niveau suivant.
 * Instantané renouvelé toutes les quelques secondes (réglage tawassol.hierarchy-cache-ttl).
 */
@Service
public class HierarchyIndex {
    record Node(String id, String parentId, String delegationId, String coordinationId) {}
    record Perim(String personnelId, String type, String targetId, LocalDate debut) {}
    private record Snapshot(Map<String, Node> nodes, List<Perim> perims, long at, LocalDate day) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration ttl;
    private volatile Snapshot snapshot;

    public HierarchyIndex(JdbcTemplate jdbc, Clock clock, TawassolProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ttl = props.hierarchyCacheTtl() == null ? Duration.ofSeconds(20) : props.hierarchyCacheTtl();
    }

    public void invalidate() { snapshot = null; }

    /** Responsables candidats pour une structure, du plus proche au plus lointain. */
    public List<String> candidates(String structureId) {
        Snapshot s = snap();
        Node start = s.nodes().get(structureId);
        if (start == null) return List.of();
        Set<String> out = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        for (Node n = start; n != null && seen.add(n.id()); n = n.parentId() == null ? null : s.nodes().get(n.parentId())) {
            add(out, s, "structure", n.id());
        }
        add(out, s, "delegation", start.delegationId());
        add(out, s, "coordination", start.coordinationId());
        return new ArrayList<>(out);
    }

    public record Niveau(String niveau, String personnelId) {}

    /**
     * Chaîne complète de validation : un responsable par niveau (chaque structure en remontant, puis la délégation, puis la coordination),
     * du plus proche au plus haut. Le demandeur est écarté à tous les niveaux et une même personne n'apparaît qu'une fois.
     */
    public List<Niveau> chaine(String requesterPersonnelId, String structureId) {
        Snapshot s = snap();
        Node start = s.nodes().get(structureId);
        if (start == null) return List.of();
        List<Niveau> out = new ArrayList<>();
        Set<String> deja = new HashSet<>();
        deja.add(requesterPersonnelId);
        Set<String> seen = new HashSet<>();
        for (Node n = start; n != null && seen.add(n.id()); n = n.parentId() == null ? null : s.nodes().get(n.parentId())) premier(out, deja, s, "structure", n.id());
        premier(out, deja, s, "delegation", start.delegationId());
        premier(out, deja, s, "coordination", start.coordinationId());
        return out;
    }

    private void premier(List<Niveau> out, Set<String> deja, Snapshot s, String type, String target) {
        if (target == null) return;
        s.perims().stream().filter(p -> p.type().equals(type) && p.targetId().equals(target) && !deja.contains(p.personnelId()))
                .min(Comparator.comparing(Perim::debut).thenComparing(Perim::personnelId))
                .ifPresent(p -> { deja.add(p.personnelId()); out.add(new Niveau(type, p.personnelId())); });
    }

    /** Responsable compétent pour un agent d'une structure, en écartant l'agent lui-même. */
    public Optional<String> resolve(String requesterPersonnelId, String structureId) {
        return candidates(structureId).stream().filter(p -> !p.equals(requesterPersonnelId)).findFirst();
    }

    private void add(Set<String> out, Snapshot s, String type, String target) {
        if (target == null) return;
        s.perims().stream().filter(p -> p.type().equals(type) && p.targetId().equals(target))
                .sorted(Comparator.comparing(Perim::debut).thenComparing(Perim::personnelId)).forEach(p -> out.add(p.personnelId()));
    }

    private Snapshot snap() {
        Snapshot s = snapshot;
        LocalDate today = LocalDate.now(clock);
        if (s != null && s.day().equals(today) && clock.millis() - s.at() < ttl.toMillis()) return s;
        Map<String, Node> nodes = new HashMap<>();
        jdbc.query("SELECT s.id, s.parent_id, s.delegation_id, d.coordination_id FROM structure s JOIN delegation_province d ON d.id = s.delegation_id",
                rs -> { nodes.put(rs.getString(1), new Node(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))); });
        List<Perim> perims = jdbc.query("""
            SELECT rp.personnel_id, rp.type_perimetre, COALESCE(rp.structure_id, rp.delegation_id, rp.coordination_id) AS target, rp.date_debut
              FROM responsable_perimetre rp JOIN personnel p ON p.id = rp.personnel_id
             WHERE rp.est_actif = 1 AND p.est_actif = 1 AND rp.date_debut <= ? AND (rp.date_fin IS NULL OR rp.date_fin >= ?)
            """, (rs, i) -> new Perim(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4, LocalDate.class)), today, today);
        snapshot = new Snapshot(nodes, perims, clock.millis(), today);
        return snapshot;
    }
}
