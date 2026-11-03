package ma.entraide.tawassol.piecejointe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ma.entraide.tawassol.config.TawassolProperties;
import org.springframework.stereotype.Service;

/** Liens de téléchargement signés (HMAC-SHA256) à durée limitée : le jeton nomme la pièce et son expiration. */
@Service
public class SignedLinkService {
    private final byte[] key;
    private final Clock clock;

    public SignedLinkService(TawassolProperties props, Clock clock) {
        this.key = ("files|" + props.security().jwtSecret()).getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String sign(String pieceId, Duration ttl) {
        String payload = pieceId + "|" + (clock.instant().plus(ttl).getEpochSecond());
        String p = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return p + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(p));
    }

    public Optional<String> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) return Optional.empty();
            byte[] given = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(given, mac(parts[0]))) return Optional.empty();
            String[] payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\\|");
            if (payload.length != 2 || Long.parseLong(payload[1]) < clock.instant().getEpochSecond()) return Optional.empty();
            return Optional.of(payload[0]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] mac(String data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return m.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
