package ma.entraide.tawassol.piecejointe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.demande.DemandeModels.DemandeRow;
import ma.entraide.tawassol.demande.DemandeModels.PieceDto;
import ma.entraide.tawassol.demande.DemandeRepository;
import ma.entraide.tawassol.param.ParametreService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PieceJointeService {
    public record Fichier(Resource resource, String nom, String typeMime) {}

    private final DemandeRepository demandes;
    private final StorageService storage;
    private final SignedLinkService links;
    private final ParametreService params;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PieceJointeService(DemandeRepository demandes, StorageService storage, SignedLinkService links, ParametreService params, JdbcTemplate jdbc, Clock clock) {
        this.demandes = demandes; this.storage = storage; this.links = links; this.params = params; this.jdbc = jdbc; this.clock = clock;
    }

    @Transactional
    public PieceDto upload(AuthenticatedUser u, String demandeId, MultipartFile file) {
        DemandeRow d = own(u, demandeId);
        if (!"brouillon".equals(d.statut())) throw new ApiException(HttpStatus.CONFLICT, "ATTACHMENTS_LOCKED", "Les pièces jointes ne se modifient que sur un brouillon");
        LocalDate today = LocalDate.now(clock);
        long maxBytes = params.integer("piece_jointe.taille_max_mo", today, 5) * 1024L * 1024L;
        if (file.isEmpty() || file.getSize() > maxBytes) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Fichier vide ou supérieur à la taille maximale");
        if (demandes.countPieces(demandeId) >= params.integer("demande.pj.max_par_demande", today, 5)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOO_MANY_ATTACHMENTS", "Nombre maximal de pièces jointes atteint");
        }
        byte[] content;
        try { content = file.getBytes(); } catch (IOException e) { throw new UncheckedIOException(e); }
        String mime = FileTypeDetector.detect(content);
        if (mime == null) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "Formats acceptés : PDF, JPG, PNG");
        String key = storage.save(content);
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("INSERT INTO piece_jointe (id, demande_id, nom_fichier, url_fichier, type_mime, taille_kb, sha256, cree_le) VALUES (?,?,?,?,?,?,?,?)",
                id, demandeId, sanitize(file.getOriginalFilename()), key, mime, (int) Math.ceil(content.length / 1024.0), sha256(content), now);
        return new PieceDto(id, sanitize(file.getOriginalFilename()), mime, (int) Math.ceil(content.length / 1024.0), now);
    }

    @Transactional
    public void delete(AuthenticatedUser u, String demandeId, String pieceId) {
        DemandeRow d = own(u, demandeId);
        if (!"brouillon".equals(d.statut())) throw new ApiException(HttpStatus.CONFLICT, "ATTACHMENTS_LOCKED", "Les pièces jointes ne se modifient que sur un brouillon");
        List<String> keys = jdbc.queryForList("SELECT url_fichier FROM piece_jointe WHERE id = ? AND demande_id = ?", String.class, pieceId, demandeId);
        if (keys.isEmpty()) throw ApiException.notFound();
        jdbc.update("DELETE FROM piece_jointe WHERE id = ?", pieceId);
        storage.delete(keys.get(0));
    }

    public Map<String, Object> lien(AuthenticatedUser u, String demandeId, String pieceId) {
        own(u, demandeId);
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM piece_jointe WHERE id = ? AND demande_id = ?", Integer.class, pieceId, demandeId);
        if (n == null || n == 0) throw ApiException.notFound();
        int ttl = params.integer("lien_signe.duree_secondes", LocalDate.now(clock), 300);
        return Map.of("url", "/api/v1/fichiers/" + links.sign(pieceId, Duration.ofSeconds(ttl)), "expireDansSecondes", ttl);
    }

    public Fichier ouvrir(String token) {
        String pieceId = links.verify(token).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "INVALID_LINK", "Lien invalide ou expiré"));
        if (pieceId.startsWith("doc:")) {
            List<String[]> docs = jdbc.query("SELECT url_fichier, titre FROM document WHERE id = ? AND url_fichier IS NOT NULL", (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, pieceId.substring(4));
            if (docs.isEmpty()) throw ApiException.notFound();
            Resource res = storage.open(docs.get(0)[0]);
            if (res == null) throw ApiException.notFound();
            return new Fichier(res, sanitize(docs.get(0)[1]) + ".pdf", "application/pdf");
        }
        List<String[]> rows = jdbc.query("SELECT url_fichier, nom_fichier, type_mime FROM piece_jointe WHERE id = ?",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, pieceId);
        if (rows.isEmpty()) throw ApiException.notFound();
        Resource r = storage.open(rows.get(0)[0]);
        if (r == null) throw ApiException.notFound();
        return new Fichier(r, rows.get(0)[1], rows.get(0)[2]);
    }

    private DemandeRow own(AuthenticatedUser u, String id) {
        DemandeRow d = demandes.findById(id).orElseThrow(ApiException::notFound);
        if (u.personnelId() == null || !d.personnelId().equals(u.personnelId())) throw ApiException.notFound();
        return d;
    }

    static String sanitize(String name) {
        String n = name == null ? "fichier" : name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (n.isEmpty()) n = "fichier";
        return n.length() > 190 ? n.substring(n.length() - 190) : n;
    }

    private static String sha256(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
