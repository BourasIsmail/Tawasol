package ma.entraide.tawassol.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Commande « tawassol-migrate » (phase M2) : référentiels, personnel, comptes d'entité.
 * <pre>
 * java -jar tawassol-api.jar --spring.profiles.active=migrate \
 *   --tawassol.migrate.source.url=jdbc:mysql://hote/suivirh --tawassol.migrate.source.user=... --tawassol.migrate.source.password=... \
 *   --tawassol.migrate.mode=a_blanc|reel --tawassol.migrate.output-dir=./migration-out
 * </pre>
 * Le mode « reel » exige --tawassol.migrate.validation-ecrite=&lt;référence du document&gt; : aucune migration réelle sans validation écrite.
 */
@Component
@ConditionalOnProperty(name = "tawassol.migrate.enabled", havingValue = "true")
public class MigrateCommand implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MigrateCommand.class);

    private final MigrationService service;
    @Value("${tawassol.migrate.source.url:}") String sourceUrl;
    @Value("${tawassol.migrate.source.user:}") String sourceUser;
    @Value("${tawassol.migrate.source.password:}") String sourcePassword;
    @Value("${tawassol.migrate.mode:a_blanc}") String mode;
    @Value("${tawassol.migrate.validation-ecrite:}") String validation;
    @Value("${tawassol.migrate.cnie-key:}") String cnieKey;
    @Value("${tawassol.migrate.output-dir:./migration-out}") String outputDir;

    private final org.springframework.context.ConfigurableApplicationContext ctx;
    public MigrateCommand(MigrationService service, org.springframework.context.ConfigurableApplicationContext ctx) { this.service = service; this.ctx = ctx; }

    @Override public void run(ApplicationArguments args) {
        int code = 0;
        try { executerCommande(); }
        catch (Exception e) { log.error("Migration refusée ou échouée", e); code = 1; }
        final int sortie = code;
        System.exit(org.springframework.boot.SpringApplication.exit(ctx, () -> sortie));
    }

    private void executerCommande() throws Exception {
        boolean reel = "reel".equals(mode);
        if (!reel && !"a_blanc".equals(mode)) throw new IllegalArgumentException("Mode inconnu : " + mode);
        if (reel && validation.isBlank()) throw new IllegalStateException("Migration réelle refusée : aucune validation écrite référencée (tawassol.migrate.validation-ecrite).");
        if (reel && cnieKey.isBlank()) throw new IllegalStateException("Migration réelle refusée : clé de chiffrement de la CNIE absente (tawassol.migrate.cnie-key).");
        if (sourceUrl.isBlank()) throw new IllegalStateException("Source absente (tawassol.migrate.source.url).");

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(sourceUrl); hc.setUsername(sourceUser); hc.setPassword(sourcePassword);
        hc.setReadOnly(true); hc.setMaximumPoolSize(1);
        try (HikariDataSource ds = new HikariDataSource(hc)) {
            MigrationService.Resultat res = service.executer(new JdbcTemplate(ds), reel, reel ? validation : null);
            Path dir = Path.of(outputDir); Files.createDirectories(dir);
            ecrire(dir, res);
            log.info("Migration {} terminée : rapport dans {}", mode, dir.toAbsolutePath());
        }
    }

    private void ecrire(Path dir, MigrationService.Resultat r) throws Exception {
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(dir.resolve("rapport-" + r.runId() + ".json").toFile(), r.rapport());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("controle-noms-" + r.runId() + ".csv"), StandardCharsets.UTF_8))) {
            w.println("matricule;nom_prenom_fr;nom_fr;prenom_fr;nom_prenom_ar;nom_ar;prenom_ar");
            for (String[] l : r.migrator().controleNoms()) w.println(String.join(";", java.util.Arrays.stream(l).map(s -> s.replace(";", ",")).toList()));
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("anomalies-" + r.runId() + ".csv"), StandardCharsets.UTF_8))) {
            w.println("code;table;id_legacy;detail");
            for (Map<String, Object> a : r.migrator().anomalies()) w.println(a.get("code") + ";" + a.get("table") + ";" + a.get("id_legacy") + ";" + String.valueOf(a.get("detail")).replace(";", ","));
        }
        if (!r.migrator().identifiants().isEmpty() && "reel".equals(r.rapport().get("mode"))) {
            Path f = dir.resolve("IDENTIFIANTS-COMPTES-TEST-" + r.runId() + ".csv");
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(f, StandardCharsets.UTF_8))) {
                w.println("login;mot_de_passe_initial;matricule;agent;role");
                for (String[] l : r.migrator().identifiants()) w.println(String.join(";", l));
            }
            try { Files.setPosixFilePermissions(f, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); } catch (Exception ignore) { /* système de fichiers sans permissions POSIX */ }
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("comptes-perimetres-" + r.runId() + ".csv"), StandardCharsets.UTF_8))) {
            w.println("login;role_origine;role_cible;coordination_legacy;delegation_legacy;unite_legacy");
            for (Map<String, Object> p : r.migrator().comptesPerimetres()) w.println(p.get("login") + ";" + p.get("role_origine") + ";" + p.get("role_cible") + ";" + p.get("coordination") + ";" + p.get("delegation") + ";" + p.get("unite_administrative"));
        }
    }
}
