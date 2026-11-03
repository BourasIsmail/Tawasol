package ma.entraide.tawassol.piecejointe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Stockage sur disque, hors du webroot, sous un nom aléatoire. Interface volontairement étroite :
 * un stockage MinIO/S3 pourra la remplacer sans toucher au reste.
 */
@Service
public class StorageService {
    private final Path root;

    public StorageService(@Value("${tawassol.storage.path}") String path) {
        this.root = Path.of(path).toAbsolutePath().normalize();
    }

    public String save(byte[] content) {
        String key = UUID.randomUUID().toString();
        try {
            Files.createDirectories(root);
            Files.write(root.resolve(key), content);
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Resource open(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) return null;
        return new FileSystemResource(p);
    }

    public void delete(String key) {
        try { Files.deleteIfExists(root.resolve(key).normalize()); } catch (IOException ignored) { /* nettoyage best effort */ }
    }
}
