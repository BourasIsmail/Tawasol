package ma.entraide.tawassol.notation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.entraide.tawassol.access.AccessGuard;
import ma.entraide.tawassol.access.PerimeterService;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.hierarchy.HierarchyIndex;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Notation annuelle côté responsable : brouillon puis soumission à la RH. L'agent noté est celui dont le responsable compétent
 * (même règle de remontée que pour les demandes) est l'utilisateur ou la personne qu'il supplée : on ne se note jamais soi-même.
 * Une note soumise ou validée n'est plus modifiable par le responsable.
 */
@RestController
@RequestMapping("/api/v1/notation")
public class NotationController {
    public record NoteRequest(@NotNull @DecimalMin("0.00") BigDecimal note, @Size(max = 100) String appreciation, String observations) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final AccessGuard guard;
    private final PerimeterService perimeters;
    private final HierarchyIndex hierarchy;
    private final AuditService audit;
    private final Clock clock;

    public NotationController(NamedParameterJdbcTemplate jdbc, AccessGuard guard, PerimeterService perimeters, HierarchyIndex hierarchy, AuditService audit, Clock clock) {
        this.jdbc = jdbc; this.guard = guard; this.perimeters = perimeters; this.hierarchy = hierarchy; this.audit = audit; this.clock = clock;
    }

    @GetMapping("/campagne")
    public Map<String, Object> campagne() {
        LocalDate today = LocalDate.now(clock);
        return jdbc.queryForList("SELECT annee, date_ouverture AS dateOuverture, date_cloture AS dateCloture, bareme_max AS baremeMax FROM campagne_notation WHERE est_active = 1 AND date_ouverture <= :t AND date_cloture >= :t ORDER BY annee DESC LIMIT 1",
                new MapSqlParameterSource("t", today)).stream().findFirst().orElse(null);
    }

    @GetMapping("/{annee}/agents")
    public Map<String, Object> agents(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable int annee) {
        List<Map<String, Object>> agents = mesAgents(u);
        Map<String, Map<String, Object>> notes = new HashMap<>();
        if (!agents.isEmpty()) {
            jdbc.queryForList("SELECT personnel_id AS pid, note, appreciation, observations, statut FROM note_annuelle WHERE annee = :a AND personnel_id IN (:ids)",
                    new MapSqlParameterSource("a", annee).addValue("ids", agents.stream().map(a -> a.get("id")).toList())).forEach(n -> notes.put((String) n.get("pid"), n));
        }
        int saisies = 0;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> a : agents) {
            Map<String, Object> row = new java.util.LinkedHashMap<>(a);
            Map<String, Object> n = notes.get((String) a.get("id"));
            row.put("statut", n == null ? "a_saisir" : n.get("statut"));
            row.put("note", n == null ? null : n.get("note"));
            row.put("appreciation", n == null ? null : n.get("appreciation"));
            if (n != null && !"brouillon".equals(n.get("statut"))) saisies++;
            out.add(row);
        }
        return Map.of("annee", annee, "total", out.size(), "soumises", saisies, "agents", out);
    }

    @PutMapping("/{annee}/agents/{personnelId}")
    @Transactional
    public Map<String, Object> saisir(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable int annee, @PathVariable String personnelId, @Valid @RequestBody NoteRequest req) {
        BigDecimal max = verifierCampagne(annee);
        verifierCompetence(u, personnelId);
        if (req.note().compareTo(max) > 0) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NOTE_OUT_OF_RANGE", "La note dépasse le barème (" + max + ")");
        Map<String, Object> existing = jdbc.queryForList("SELECT id, statut FROM note_annuelle WHERE personnel_id = :p AND annee = :a", new MapSqlParameterSource("p", personnelId).addValue("a", annee)).stream().findFirst().orElse(null);
        MapSqlParameterSource p = new MapSqlParameterSource("p", personnelId).addValue("a", annee).addValue("n", req.note()).addValue("ap", req.appreciation())
                .addValue("o", req.observations()).addValue("u", u.userId());
        if (existing == null) {
            jdbc.update("INSERT INTO note_annuelle (id, personnel_id, saisie_par, annee, note, appreciation, observations, statut) VALUES (:id, :p, :u, :a, :n, :ap, :o, 'brouillon')", p.addValue("id", UUID.randomUUID().toString()));
        } else {
            if (!"brouillon".equals(existing.get("statut"))) throw new ApiException(HttpStatus.CONFLICT, "NOTE_LOCKED", "Cette note n'est plus modifiable");
            jdbc.update("UPDATE note_annuelle SET note = :n, appreciation = :ap, observations = :o, saisie_par = :u WHERE personnel_id = :p AND annee = :a", p);
        }
        audit.record(u.userId(), "NOTE_SAISIE", "note_annuelle", personnelId + ":" + annee, null, null);
        return Map.of("statut", "brouillon");
    }

    @PostMapping("/{annee}/agents/{personnelId}/soumettre")
    @Transactional
    public Map<String, Object> soumettre(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable int annee, @PathVariable String personnelId) {
        verifierCampagne(annee);
        verifierCompetence(u, personnelId);
        String statut = jdbc.queryForList("SELECT statut FROM note_annuelle WHERE personnel_id = :p AND annee = :a", new MapSqlParameterSource("p", personnelId).addValue("a", annee), String.class)
                .stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NOTE_MISSING", "Saisissez la note avant de la soumettre"));
        if ("soumise".equals(statut)) return Map.of("statut", "soumise");
        if (!"brouillon".equals(statut)) throw new ApiException(HttpStatus.CONFLICT, "NOTE_LOCKED", "Cette note n'est plus modifiable");
        jdbc.update("UPDATE note_annuelle SET statut = 'soumise' WHERE personnel_id = :p AND annee = :a", new MapSqlParameterSource("p", personnelId).addValue("a", annee));
        audit.record(u.userId(), "NOTE_SOUMISE", "note_annuelle", personnelId + ":" + annee, null, null);
        return Map.of("statut", "soumise");
    }

    @GetMapping("/{annee}/agents/{personnelId}")
    public Map<String, Object> detail(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable int annee, @PathVariable String personnelId) {
        Map<String, Object> agent = verifierCompetence(u, personnelId);
        Map<String, Object> out = new java.util.LinkedHashMap<>(agent);
        Map<String, Object> n = jdbc.queryForList("SELECT note, appreciation, observations, statut FROM note_annuelle WHERE personnel_id = :p AND annee = :a", new MapSqlParameterSource("p", personnelId).addValue("a", annee)).stream().findFirst().orElse(null);
        out.put("statut", n == null ? "a_saisir" : n.get("statut"));
        out.put("note", n == null ? null : n.get("note")); out.put("appreciation", n == null ? null : n.get("appreciation")); out.put("observations", n == null ? null : n.get("observations"));
        return out;
    }

    // ---- règles ----

    private BigDecimal verifierCampagne(int annee) {
        LocalDate today = LocalDate.now(clock);
        return jdbc.queryForList("SELECT bareme_max FROM campagne_notation WHERE annee = :a AND est_active = 1 AND date_ouverture <= :t AND date_cloture >= :t",
                new MapSqlParameterSource("a", annee).addValue("t", today), BigDecimal.class).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CAMPAIGN_CLOSED", "La campagne de notation n'est pas ouverte"));
    }

    /** L'agent doit relever de l'utilisateur (ou de la personne qu'il supplée) : sinon introuvable. */
    private Map<String, Object> verifierCompetence(AuthenticatedUser u, String personnelId) {
        return mesAgents(u).stream().filter(a -> personnelId.equals(a.get("id"))).findFirst().orElseThrow(ApiException::notFound);
    }

    private List<Map<String, Object>> mesAgents(AuthenticatedUser u) {
        if (u.personnelId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "NO_PERSONNEL", "Ce compte n'est lié à aucun agent");
        var scope = guard.personnelScope(u);
        if (scope.structureIds().isEmpty()) return List.of();
        Set<String> acting = perimeters.effectivePersonnelIds(u.personnelId(), true);
        List<Map<String, Object>> candidates = jdbc.queryForList("""
            SELECT p.id, p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr, p.structure_id AS structureId, g.libelle_fr AS gradeFr, g.libelle_ar AS gradeAr
              FROM personnel p LEFT JOIN grade g ON g.id = p.grade_id WHERE p.est_actif = 1 AND p.structure_id IN (:sids) AND p.id <> :me ORDER BY p.nom_fr, p.prenom_fr
            """, new MapSqlParameterSource("sids", scope.structureIds()).addValue("me", u.personnelId()));
        List<Map<String, Object>> mine = new ArrayList<>();
        for (Map<String, Object> a : candidates) {
            var resp = hierarchy.resolve((String) a.get("id"), (String) a.get("structureId"));
            if (resp.isPresent() && acting.contains(resp.get())) mine.add(a);
        }
        return mine;
    }
}
