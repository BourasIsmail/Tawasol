package ma.entraide.tawassol.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import ma.entraide.tawassol.config.TawassolProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUnitTest {
    static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    static TawassolProperties props(String secret, int rate) {
        return new TawassolProperties(new TawassolProperties.Security(secret, Duration.ofMinutes(15), Duration.ofDays(30), 5,
                Duration.ofMinutes(15), rate, 12), new TawassolProperties.Cors(List.of()), new TawassolProperties.Bootstrap("", ""), null, null);
    }

    @Test void secretTropCourtRefuse() {
        assertThrows(IllegalStateException.class, () -> new JwtService(props("court", 10), Clock.systemUTC()));
        assertThrows(IllegalStateException.class, () -> new JwtService(props(null, 10), Clock.systemUTC()));
    }

    @Test void jetonValideExpireOuFalsifie() {
        Clock t0 = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneId.of("UTC"));
        JwtService jwt = new JwtService(props(SECRET, 10), t0);
        String token = jwt.issueAccessToken("user-1");
        assertEquals("user-1", jwt.parseSubject(token).orElseThrow());
        JwtService later = new JwtService(props(SECRET, 10), Clock.offset(t0, Duration.ofMinutes(16)));
        assertTrue(later.parseSubject(token).isEmpty());
        assertTrue(jwt.parseSubject(token.substring(0, token.length() - 2) + "xx").isEmpty());
        assertTrue(new JwtService(props(SECRET + "autre", 10), t0).parseSubject(token).isEmpty());
        assertTrue(jwt.parseSubject("n'importe quoi").isEmpty());
    }

    @Test void politiqueDeMotDePasse() {
        assertTrue(PasswordPolicy.violations("Nouveau-Mdp-2026!", 12).isEmpty());
        assertFalse(PasswordPolicy.violations("trop court", 12).isEmpty());
        assertTrue(PasswordPolicy.violations("sansmajuscule-2026!", 12).contains("UPPERCASE"));
        assertTrue(PasswordPolicy.violations("SansSymbole2026Abc", 12).contains("SYMBOL"));
    }

    @Test void argon2idEtBcryptLegacy() {
        PasswordService p = new PasswordService();
        String h = p.hash("Secret-2026!x");
        assertTrue(h.startsWith("$argon2id$"));
        assertTrue(p.matches("Secret-2026!x", h));
        assertFalse(p.matches("autre", h));
        assertFalse(p.matches("x", "$2b$12$CHANGEZ_CE_HASH_IMMEDIATEMENT"));
        String legacy = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Legacy-2026!x");
        assertTrue(p.matches("Legacy-2026!x", legacy));
        assertTrue(p.needsUpgrade(legacy));
    }

    @Test void limiteurDeConnexions() {
        Clock t0 = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneId.of("UTC"));
        LoginRateLimiter l = new LoginRateLimiter(props(SECRET, 3), t0);
        assertTrue(l.allow("ip")); assertTrue(l.allow("ip")); assertTrue(l.allow("ip"));
        assertFalse(l.allow("ip"));
        assertTrue(l.allow("autre-ip"));
        LoginRateLimiter later = new LoginRateLimiter(props(SECRET, 3), Clock.offset(t0, Duration.ofSeconds(61)));
        assertTrue(later.allow("ip"));
    }
}
