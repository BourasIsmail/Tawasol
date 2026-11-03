package ma.entraide.tawassol.param;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Numéros uniques et séquentiels par année (ex. DEM-2026-000123). À appeler dans la transaction qui consomme le numéro. */
@Service
public class NumeroService {
    private final JdbcTemplate jdbc;

    public NumeroService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public String suivant(String cle, int annee) {
        jdbc.update("INSERT INTO compteur_numero (cle, annee, valeur) VALUES (?, ?, LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE valeur = LAST_INSERT_ID(valeur + 1)", cle, annee);
        Long v = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return "%s-%d-%06d".formatted(cle, annee, v);
    }
}
