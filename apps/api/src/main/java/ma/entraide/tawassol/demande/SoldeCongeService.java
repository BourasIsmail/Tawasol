package ma.entraide.tawassol.demande;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import ma.entraide.tawassol.param.ParametreService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SoldeCongeService {
    public record Solde(int annee, BigDecimal droit, BigDecimal pris, BigDecimal reporte, BigDecimal reserve, BigDecimal disponible) {}

    private final JdbcTemplate jdbc;
    private final ParametreService params;
    private final DemandeRepository demandes;

    public SoldeCongeService(JdbcTemplate jdbc, ParametreService params, DemandeRepository demandes) {
        this.jdbc = jdbc;
        this.params = params;
        this.demandes = demandes;
    }

    /** Solde d'une année. Sans ligne en base, le droit est celui du paramètre « conge.annuel.droit_jours » (à valider). */
    public Solde get(String personnelId, int annee, String excludeDemandeId) {
        List<BigDecimal[]> rows = jdbc.query("SELECT droit, pris, reporte FROM solde_conge WHERE personnel_id = ? AND annee = ?",
                (rs, i) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3)}, personnelId, annee);
        BigDecimal droit = rows.isEmpty() ? params.decimal("conge.annuel.droit_jours", LocalDate.of(annee, 1, 1), BigDecimal.ZERO) : rows.get(0)[0];
        BigDecimal pris = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0)[1];
        BigDecimal reporte = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0)[2];
        BigDecimal reserve = demandes.joursReserves(personnelId, excludeDemandeId == null ? "-" : excludeDemandeId, annee);
        return new Solde(annee, droit, pris, reporte, reserve, droit.add(reporte).subtract(pris).subtract(reserve));
    }
}
