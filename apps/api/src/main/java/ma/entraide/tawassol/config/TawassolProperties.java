package ma.entraide.tawassol.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Réglages applicatifs. Aucun secret n'est défini ici : ils viennent de l'environnement. */
@ConfigurationProperties(prefix = "tawassol")
public record TawassolProperties(Security security, Cors cors, Bootstrap bootstrap, Duration hierarchyCacheTtl, String webUrl) {
    public record Security(String jwtSecret, Duration accessTtl, Duration refreshTtl, int maxFailedAttempts,
                           Duration lockDuration, int loginRatePerMinute, int passwordMinLength) {}
    public record Cors(List<String> allowedOrigins) {}
    public record Bootstrap(String adminPassword, String demoPassword) {}
}
