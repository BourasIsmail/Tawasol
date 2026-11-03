package ma.entraide.tawassol.deplacement;

import ma.entraide.tawassol.param.ParametreService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Numéros d'ordre de mission et d'état, par délégation, par année et par type. Le verrou de ligne InnoDB pris par
 * l'UPDATE garantit l'unicité sous charge ; le numéro est consommé dans la transaction de l'appelant : si elle est annulée,
 * le compteur l'est aussi, donc aucun trou. À appeler à l'ÉMISSION (validation du document), jamais au brouillon.
 * Les numéros hérités de Suivi RH ne passent pas ici : ils sont conservés dans numero_*_legacy.
 * Le gabarit est un paramètre daté (mission.numero.gabarit_ordm / gabarit_etat), non validé par défaut.
 */
@Service
public class NumerotationMissionService {
    public enum Type { ORDM("ordm", "{symbole}-{annee}-{n:5}"), ETAT("etat", "E{symbole}-{annee}-{n:5}");
        final String code, gabarit;
        Type(String code, String gabarit) { this.code = code; this.gabarit = gabarit; }
    }

    private final JdbcTemplate jdbc;
    private final ParametreService params;

    public NumerotationMissionService(JdbcTemplate jdbc, ParametreService params) { this.jdbc = jdbc; this.params = params; }

    @Transactional(propagation = Propagation.MANDATORY)
    public int suivant(String delegationId, int annee, Type type) {
        jdbc.update("INSERT INTO delegation_compteur (delegation_id, annee, type_numero, dernier_numero) VALUES (?,?,?,LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE dernier_numero = LAST_INSERT_ID(dernier_numero + 1)", delegationId, annee, type.code);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String prochainNumero(String delegationId, int annee, Type type) {
        int n = suivant(delegationId, annee, type);
        String symbole = jdbc.queryForObject("SELECT COALESCE(symbole, code_province) FROM delegation_province WHERE id = ?", String.class, delegationId);
        String gabarit = params.get("mission.numero.gabarit_" + type.code, LocalDate.of(annee, 12, 31)).orElse(type.gabarit);
        return formater(gabarit, symbole, annee, n);
    }

    public static String formater(String gabarit, String symbole, int annee, int n) {
        String out = gabarit.replace("{symbole}", symbole).replace("{annee}", String.valueOf(annee));
        var m = java.util.regex.Pattern.compile("\\{n(?::(\\d+))?}").matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, m.group(1) == null ? String.valueOf(n) : String.format("%0" + m.group(1) + "d", n));
        m.appendTail(sb);
        return sb.toString();
    }
}
