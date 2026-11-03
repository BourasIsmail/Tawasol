package ma.entraide.tawassol.publication;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Annonces et documents publiés, filtrés selon la cible (tous, coordination, délégation, structure ou l'un de ses parents)
 * et selon les dates de publication et d'expiration. Le filtrage se fait dans la requête.
 */
@RestController
@RequestMapping("/api/v1")
public class PublicationController {
    private static final String CIBLE = """
        (x.cible_type = 'tous'
         OR (x.cible_type = 'structure' AND x.cible_id IN (:chaine))
         OR (x.cible_type = 'delegation' AND x.cible_id = :deleg)
         OR (x.cible_type = 'coordination' AND x.cible_id = :coord))
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ma.entraide.tawassol.piecejointe.SignedLinkService links;

    public PublicationController(NamedParameterJdbcTemplate jdbc, Clock clock, ma.entraide.tawassol.piecejointe.SignedLinkService links) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.links = links;
    }

    /** Lien signé de 5 minutes vers le PDF d'un document visible par l'agent. */
    @GetMapping("/documents/{id}/lien")
    public Map<String, Object> lien(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        boolean visible = documents(u, null).stream().anyMatch(d -> id.equals(d.get("id")));
        Integer file = visible ? jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE id = :id AND url_fichier IS NOT NULL", new MapSqlParameterSource("id", id), Integer.class) : 0;
        if (file == null || file == 0) throw ApiException.notFound();
        return Map.of("url", "/api/v1/fichiers/" + links.sign("doc:" + id, java.time.Duration.ofMinutes(5)), "expireDansSecondes", 300);
    }

    @GetMapping("/annonces")
    public List<Map<String, Object>> annonces(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("""
            SELECT x.id, x.titre, x.message, x.publie_le AS publieLe, x.expire_le AS expireLe FROM annonce x
             WHERE x.est_active = 1 AND x.publie_le <= :now AND (x.expire_le IS NULL OR x.expire_le > :now) AND
            """ + CIBLE + " ORDER BY x.publie_le DESC, x.id", params(u));
    }

    @GetMapping("/documents")
    public List<Map<String, Object>> documents(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) String type) {
        MapSqlParameterSource p = params(u);
        String filtre = "";
        if (type != null && !type.isBlank()) { filtre = " AND t.libelle = :type"; p.addValue("type", type); }
        p.addValue("uid", u.userId());
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT x.id, x.titre, x.contenu, x.publie_le AS publieLe, x.expire_le AS expireLe, t.libelle AS type, x.accuse_lecture AS accuseLecture,
                   (SELECT COUNT(*) FROM document_lecture l WHERE l.document_id = x.id AND l.utilisateur_id = :uid) > 0 AS lu, (x.url_fichier IS NOT NULL) AS aFichier
              FROM document x JOIN type_document t ON t.id = x.type_doc_id
             WHERE x.est_publie = 1 AND (x.publie_le IS NULL OR x.publie_le <= :now) AND (x.expire_le IS NULL OR x.expire_le > :now) AND
            """ + CIBLE + filtre + " ORDER BY x.publie_le DESC, x.id", p);
        rows.forEach(r -> { r.put("lu", bool(r.get("lu"))); r.put("accuseLecture", bool(r.get("accuseLecture"))); r.put("aFichier", bool(r.get("aFichier"))); });
        return rows;
    }

    @PostMapping("/documents/{id}/lecture")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lecture(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        boolean visible = documents(u, null).stream().anyMatch(d -> id.equals(d.get("id")));
        if (!visible) throw ApiException.notFound();
        jdbc.update("INSERT IGNORE INTO document_lecture (document_id, utilisateur_id, lu_le) VALUES (:d, :u, :now)",
                new MapSqlParameterSource("d", id).addValue("u", u.userId()).addValue("now", LocalDateTime.now(clock)));
    }

    /** Selon le pilote, un booléen SQL arrive en Boolean ou en nombre. */
    private static boolean bool(Object o) {
        return o instanceof Boolean b ? b : o instanceof Number n && n.intValue() != 0;
    }

    private MapSqlParameterSource params(AuthenticatedUser u) {
        if (u.personnelId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "NO_PERSONNEL", "Ce compte n'est lié à aucun agent");
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT p.structure_id AS sid, s.delegation_id AS did, d.coordination_id AS cid
              FROM personnel p JOIN structure s ON s.id = p.structure_id JOIN delegation_province d ON d.id = s.delegation_id WHERE p.id = :p
            """, new MapSqlParameterSource("p", u.personnelId()));
        if (rows.isEmpty()) throw ApiException.notFound();
        List<String> chaine = jdbc.queryForList("""
            WITH RECURSIVE anc AS (SELECT id, parent_id FROM structure WHERE id = :sid
                                   UNION SELECT s.id, s.parent_id FROM structure s JOIN anc a ON s.id = a.parent_id)
            SELECT id FROM anc
            """, new MapSqlParameterSource("sid", rows.get(0).get("sid")), String.class);
        return new MapSqlParameterSource().addValue("chaine", chaine).addValue("deleg", rows.get(0).get("did"))
                .addValue("coord", rows.get(0).get("cid")).addValue("now", LocalDateTime.now(clock));
    }
}
