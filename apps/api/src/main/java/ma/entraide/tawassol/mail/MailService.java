package ma.entraide.tawassol.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envoi d'e-mails. Sans serveur SMTP configuré (spring.mail.host), rien n'est envoyé : le message est seulement journalisé,
 * corps compris uniquement en profil dev (le corps peut contenir un lien de réinitialisation, donnée sensible).
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private final ObjectProvider<JavaMailSender> sender;
    private final Environment env;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> sender, Environment env, @Value("${tawassol.mail-from:no-reply@tawassol.example.ma}") String from) {
        this.sender = sender;
        this.env = env;
        this.from = from;
    }

    public void send(String to, String subject, String body) {
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) {
            log.warn("SMTP non configuré : e-mail « {} » non envoyé.{}", subject, env.acceptsProfiles(Profiles.of("dev")) ? " [dev] à " + to + " : " + body : "");
            return;
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(to);
        m.setSubject(subject);
        m.setText(body);
        s.send(m);
    }
}
