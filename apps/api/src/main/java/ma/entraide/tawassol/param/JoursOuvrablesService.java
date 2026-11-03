package ma.entraide.tawassol.param;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Décompte des jours ouvrables : hors jours non ouvrés (paramètre) et jours fériés (table jour_ferie). */
@Service
public class JoursOuvrablesService {
    private final JdbcTemplate jdbc;
    private final ParametreService params;

    public JoursOuvrablesService(JdbcTemplate jdbc, ParametreService params) {
        this.jdbc = jdbc;
        this.params = params;
    }

    public int compter(LocalDate debut, LocalDate fin) {
        Set<DayOfWeek> nonOuvres = params.get("conge.jours_non_ouvres", debut).map(v -> Arrays.stream(v.split(","))
                .map(String::trim).map(DayOfWeek::valueOf).collect(Collectors.toSet()))
                .orElse(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        List<LocalDate> feries = jdbc.queryForList("SELECT date_ferie FROM jour_ferie WHERE date_ferie BETWEEN ? AND ?", LocalDate.class, debut, fin);
        Set<LocalDate> ferie = new HashSet<>(feries);
        int n = 0;
        for (LocalDate d = debut; !d.isAfter(fin); d = d.plusDays(1)) {
            if (!nonOuvres.contains(d.getDayOfWeek()) && !ferie.contains(d)) n++;
        }
        return n;
    }
}
