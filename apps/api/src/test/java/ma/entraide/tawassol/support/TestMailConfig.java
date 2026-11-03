package ma.entraide.tawassol.support;

import java.util.ArrayList;
import java.util.List;
import ma.entraide.tawassol.mail.MailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@TestConfiguration
public class TestMailConfig {
    public record Mail(String to, String subject, String body) {}

    public static class CapturingMailService extends MailService {
        public final List<Mail> sent = new ArrayList<>();

        public CapturingMailService(Environment env) { super(null, env, "test@example.ma"); }

        @Override public void send(String to, String subject, String body) { sent.add(new Mail(to, subject, body)); }
    }

    @Bean @Primary
    CapturingMailService capturingMailService(Environment env) { return new CapturingMailService(env); }
}
