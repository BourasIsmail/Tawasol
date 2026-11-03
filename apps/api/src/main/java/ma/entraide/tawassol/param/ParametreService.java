package ma.entraide.tawassol.param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Lit les paramètres versionnés : la valeur applicable est la plus récente dont la date d'effet est atteinte. */
@Service
public class ParametreService {
    private final JdbcTemplate jdbc;

    public ParametreService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<String> get(String cle, LocalDate at) {
        return jdbc.queryForList("SELECT valeur FROM parametre WHERE cle = ? AND date_effet <= ? ORDER BY date_effet DESC LIMIT 1",
                String.class, cle, at).stream().findFirst();
    }

    public BigDecimal decimal(String cle, LocalDate at, BigDecimal defaut) {
        return get(cle, at).map(BigDecimal::new).orElse(defaut);
    }

    public int integer(String cle, LocalDate at, int defaut) {
        return get(cle, at).map(Integer::parseInt).orElse(defaut);
    }
}
