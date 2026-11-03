package ma.entraide.tawassol.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    public static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

    @Bean
    public Clock clock() {
        return Clock.system(CASABLANCA);
    }
}
