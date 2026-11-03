package ma.entraide.tawassol.rh;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.piecejointe.FileTypeDetector;
import ma.entraide.tawassol.piecejointe.StorageService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Publication d'annonces et de documents, ciblables (tous, coordination, délégation, structure), avec expiration. */
@RestController
@RequestMapping("/api/v1/rh")
@PreAuthorize("hasRole('RH')")
public class RhPublicationController {
    public record AnnonceRequest(String titre, String message, String cibleType, String cibleId, LocalDateTime publieLe, LocalDateTime expireLe, Boolean active) {}

    private static final Map<String, String> TABLES = Map.of("coordination", "coordination_region", "delegation", "delegation_province", "structure", "structure");

    private final JdbcTemplate jdbc;
    private final StorageService storage;
    private final AuditService audit;
    private final Clock clock;

    public RhPublicationController(JdbcTemplate jdbc, StorageService storage, AuditService audit, Clock clock) {
        this.jdbc = jdbc; this.storage = storage; this.audit = audit; this.clock = clock;
    }

    @GetMapping("/annonces")
    public List<Map<String, Object>> annonces() {
        return jdbc.queryForList("SELECT id, titre, message, est_active AS active, publie_le AS publieLe, expire_le AS expireLe, cible_type AS cibleType, cible_id AS cibleId FROM annonce ORDER BY publie_le DESC, id LIMIT 200");
    }

    @PostMapping("/annonces")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> creerAnnonce(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody AnnonceRequest r) {
        if (r.titre() == null || r.titre().isBlank() || r.message() == null || r.message().isBlank()) throw invalid("titre et message obligatoires");
        cible(r.cibleType(), r.cibleId());
        dates(r.publieLe(), r.expireLe());
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO annonce (id, cree_par, titre, message, est_active, publie_le, expire_le, cible_type, cible_id) VALUES (?,?,?,?,?,?,?,?,?)", id, u.userId(), r.titre().trim(), r.message(),
                r.active() == null || r.active(), r.publieLe() == null ? LocalDateTime.now(clock) : r.publieLe(), r.expireLe(), r.cibleType() == null ? "tous" : r.cibleType(), "tous".equals(r.cibleType()) || r.cibleType() == null ? null : r.cibleId());
        audit.record(u.userId(), "ANNONCE_PUBLIEE", "annonce", id, null, null);
        return Map.of("id", id);
    }

    @PutMapping("/annonces/{id}")
    public Map<String, Object> majAnnonce(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody AnnonceRequest r) {
        if (r.cibleType() != null) cible(r.cibleType(), r.cibleId());
        dates(r.publieLe(), r.expireLe());
        if (jdbc.update("UPDATE annonce SET titre = COALESCE(?, titre), message = COALESCE(?, message), est_active = COALESCE(?, est_active), expire_le = COALESCE(?, expire_le) WHERE id = ?", r.titre(), r.message(), r.active(), r.expireLe(), id) == 0) throw ApiException.notFound();
        audit.record(u.userId(), "ANNONCE_MODIFIEE", "annonce", id, null, null);
        return Map.of("id", id);
    }

    @GetMapping("/types-document")
    public List<Map<String, Object>> typesDocument() { return jdbc.queryForList("SELECT id, libelle FROM type_document ORDER BY libelle"); }

    @GetMapping("/documents")
    public List<Map<String, Object>> documents() {
        return jdbc.queryForList("SELECT d.id, d.titre, t.libelle AS type, d.est_publie AS publie, d.publie_le AS publieLe, d.expire_le AS expireLe, d.cible_type AS cibleType, d.accuse_lecture AS accuseLecture, (d.url_fichier IS NOT NULL) AS aFichier FROM document d JOIN type_document t ON t.id = d.type_doc_id ORDER BY d.cree_le DESC, d.id LIMIT 200");
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> publierDocument(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam String typeDocId, @RequestParam String titre, @RequestParam(required = false) String contenu,
                                               @RequestParam(required = false) MultipartFile fichier, @RequestParam(defaultValue = "tous") String cibleType, @RequestParam(required = false) String cibleId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publieLe,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expireLe,
                                               @RequestParam(defaultValue = "false") boolean accuseLecture) throws IOException {
        if (titre.isBlank()) throw invalid("titre obligatoire");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM type_document WHERE id = ?", Integer.class, typeDocId) == 0) throw invalid("type de document inconnu");
        cible(cibleType, cibleId);
        dates(publieLe, expireLe);
        String cle = null;
        if (fichier != null && !fichier.isEmpty()) {
            byte[] b = fichier.getBytes();
            if (b.length > 10 * 1024 * 1024) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Fichier trop volumineux");
            if (!"application/pdf".equals(FileTypeDetector.detect(b))) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "Seuls les PDF sont acceptés pour les documents publiés");
            cle = storage.save(b);
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO document (id, type_doc_id, cree_par, titre, url_fichier, contenu, est_publie, publie_le, expire_le, cible_type, cible_id, accuse_lecture) VALUES (?,?,?,?,?,?,1,?,?,?,?,?)", id, typeDocId, u.userId(), titre.trim(), cle, contenu,
                publieLe == null ? LocalDateTime.now(clock) : publieLe, expireLe, cibleType, "tous".equals(cibleType) ? null : cibleId, accuseLecture);
        audit.record(u.userId(), "DOCUMENT_PUBLIE", "document", id, null, null);
        return Map.of("id", id);
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void depublier(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        if (jdbc.update("UPDATE document SET est_publie = 0 WHERE id = ?", id) == 0) throw ApiException.notFound();
        audit.record(u.userId(), "DOCUMENT_DEPUBLIE", "document", id, null, null);
    }

    private void cible(String type, String id) {
        if (type == null || "tous".equals(type)) return;
        String table = TABLES.get(type);
        if (table == null || id == null || jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id) == 0) throw invalid("cible invalide");
    }

    private static void dates(LocalDateTime pub, LocalDateTime exp) {
        if (pub != null && exp != null && exp.isBefore(pub)) throw invalid("l'expiration précède la publication");
    }

    private static ApiException invalid(String m) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", m); }
}
