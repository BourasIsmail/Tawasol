package ma.entraide.tawassol.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/** Exécute une reprise Suivi RH (à blanc : tout est annulé à la fin ; réel : validé, avec trace dans migration_run). */
@Service
public class MigrationService {
    private final JdbcTemplate target;
    private final PlatformTransactionManager tx;
    @Value("${tawassol.migrate.cnie-key:}") String cnieKey;

    public MigrationService(JdbcTemplate target, PlatformTransactionManager tx) { this.target = target; this.tx = tx; }

    public record Resultat(String runId, Map<String, Object> rapport, LegacyMigrator migrator) {}

    /** Réutilisable par les tests : exécute la reprise, validée seulement en mode réel. */
    public Resultat executer(JdbcTemplate source, boolean reel, String validationEcrite) {
        if (reel && (validationEcrite == null || validationEcrite.isBlank())) throw new IllegalStateException("Migration réelle refusée : aucune validation écrite référencée.");
        if (reel && cnieKey.isBlank()) throw new IllegalStateException("Migration réelle refusée : clé de chiffrement de la CNIE absente.");
        LegacyMigrator m = new LegacyMigrator(source, target, new CnieProtector(cnieKey));
        TransactionTemplate t = new TransactionTemplate(tx);
        String runId = m.runId();
        try {
            t.executeWithoutResult(st -> {
                target.update("INSERT INTO migration_run (id, source, mode, statut) VALUES (?,?,?, 'en_cours')", runId, "suivirh", reel ? "reel" : "a_blanc");
                m.executer();
                if (!reel) st.setRollbackOnly();
            });
        } catch (RuntimeException e) {
            target.update("INSERT INTO migration_run (id, source, mode, statut, fin, rapport) VALUES (?,?,?, 'echouee', NOW(), ?)", runId, "suivirh", reel ? "reel" : "a_blanc", "{\"erreur\":\"" + e.getClass().getSimpleName() + "\"}");
            throw e;
        }
        Map<String, Object> rapport = m.rapport();
        rapport.put("mode", reel ? "reel" : "a_blanc");
        if (validationEcrite != null) rapport.put("validation_ecrite", validationEcrite);
        try {
            String json = new ObjectMapper().writeValueAsString(rapport);
            if (reel) target.update("UPDATE migration_run SET statut = 'terminee', fin = NOW(), rapport = ? WHERE id = ?", json, runId);
            else target.update("INSERT INTO migration_run (id, source, mode, statut, fin, rapport) VALUES (?,?,?, 'terminee', NOW(), ?)", runId, "suivirh", "a_blanc", json);
        } catch (Exception e) { throw new IllegalStateException(e); }
        return new Resultat(runId, rapport, m);
    }
}
