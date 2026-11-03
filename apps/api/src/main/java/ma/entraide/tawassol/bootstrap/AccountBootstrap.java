package ma.entraide.tawassol.bootstrap;

import ma.entraide.tawassol.config.TawassolProperties;
import ma.entraide.tawassol.security.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Remplace le hash factice du compte admin du dump par un mot de passe initial fourni par l'environnement,
 * et force son changement à la première connexion. En profil demo, initialise aussi les comptes de démonstration.
 */
@Component
public class AccountBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AccountBootstrap.class);
    static final String PLACEHOLDER = "$2b$12$CHANGEZ%";
    static final String DEMO_PLACEHOLDER = "$DEMO$%";

    private final JdbcTemplate jdbc;
    private final PasswordService passwords;
    private final TawassolProperties props;
    private final Environment env;

    public AccountBootstrap(JdbcTemplate jdbc, PasswordService passwords, TawassolProperties props, Environment env) {
        this.jdbc = jdbc; this.passwords = passwords; this.props = props; this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        String pw = props.bootstrap() == null ? null : props.bootstrap().adminPassword();
        int pending = jdbc.queryForObject("SELECT COUNT(*) FROM utilisateur WHERE login = 'admin' AND mot_de_passe LIKE ?", Integer.class, PLACEHOLDER);
        if (pending > 0) {
            if (pw == null || pw.isBlank()) {
                log.warn("Compte admin inactif : définissez TAWASSOL_ADMIN_INITIAL_PASSWORD pour l'activer.");
            } else {
                jdbc.update("UPDATE utilisateur SET mot_de_passe = ?, doit_changer_mdp = 1 WHERE login = 'admin' AND mot_de_passe LIKE ?",
                        passwords.hash(pw), PLACEHOLDER);
                log.info("Compte admin initialisé : changement de mot de passe obligatoire à la première connexion.");
            }
        }
        if (env.acceptsProfiles(Profiles.of("demo"))) {
            String demo = props.bootstrap() == null ? null : props.bootstrap().demoPassword();
            if (demo != null && !demo.isBlank()) {
                int n = jdbc.update("UPDATE utilisateur SET mot_de_passe = ?, doit_changer_mdp = 0 WHERE mot_de_passe LIKE ?",
                        passwords.hash(demo), DEMO_PLACEHOLDER);
                if (n > 0) log.info("{} comptes de démonstration initialisés.", n);
            }
        }
    }
}
