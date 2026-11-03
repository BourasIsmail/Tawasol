package ma.entraide.tawassol.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import ma.entraide.tawassol.config.TawassolProperties;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final TawassolProperties props;
    private final Clock clock;

    public JwtService(TawassolProperties props, Clock clock) {
        byte[] secret = props.security().jwtSecret() == null ? new byte[0] : props.security().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("tawassol.security.jwt-secret (JWT_SECRET) doit contenir au moins 32 caractères");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.props = props;
        this.clock = clock;
    }

    public String issueAccessToken(String userId) {
        Date now = Date.from(clock.instant());
        Date exp = Date.from(clock.instant().plus(props.security().accessTtl()));
        return Jwts.builder().subject(userId).issuedAt(now).expiration(exp).signWith(key).compact();
    }

    public Optional<String> parseSubject(String token) {
        try {
            return Optional.ofNullable(Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build()
                    .parseSignedClaims(token).getPayload().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
