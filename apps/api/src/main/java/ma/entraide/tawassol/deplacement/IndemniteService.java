package ma.entraide.tawassol.deplacement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.param.ParametreService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Calcul des indemnités d'une mission : repas (seuils et taux datés), frais de transport en commun, indemnité kilométrique.
 * Toutes les valeurs viennent de la base à la date de départ ; les montants sont figés dans la mission au moment du calcul
 * et ne sont jamais recalculés après coup (instantané).
 */
@Service
public class IndemniteService {
    public record Demande(Integer echelle, LocalDateTime depart, LocalDateTime retour, boolean remboursable, String moyenTransportCode,
                          BigDecimal fraisTransport, BigDecimal distanceKm, Integer nombreTrajets, String puissanceFiscaleId) {}

    public record Calcul(int repasTaux1, int repasTaux2, BigDecimal tauxRepas1, BigDecimal tauxRepas2, BigDecimal montantIndemnites,
                         BigDecimal fraisTransport, BigDecimal tauxKm, BigDecimal montantKm, BigDecimal total, List<String> avertissements) {}

    private final JdbcTemplate jdbc;
    private final ParametreService params;

    public IndemniteService(JdbcTemplate jdbc, ParametreService params) { this.jdbc = jdbc; this.params = params; }

    private static BigDecimal m(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    public RepasEngine.Seuils seuilsAu(LocalDate d) {
        int[] presence = new int[3], intervalle = new int[3];
        String[] codes = {"petit_dejeuner", "dejeuner", "diner"};
        int bascule = 15;
        for (int i = 0; i < 3; i++) {
            var l = jdbc.queryForList("SELECT seuil_presence_min, intervalle_min FROM bareme_repas WHERE repas = ? AND date_effet <= ? AND (date_fin IS NULL OR date_fin >= ?) ORDER BY date_effet DESC LIMIT 1", codes[i], d, d);
            if (l.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SEUILS_REPAS_ABSENTS", "Aucun seuil de repas applicable à la date de départ");
            presence[i] = ((Number) l.get(0).get("seuil_presence_min")).intValue();
            intervalle[i] = ((Number) l.get(0).get("intervalle_min")).intValue();
        }
        LocalTime petitApres = LocalTime.parse(params.get("mission.repas.creneau_petit_dejeuner_apres", d).orElse("18:00"));
        LocalTime dejJusqua = LocalTime.parse(params.get("mission.repas.creneau_dejeuner_jusqua", d).orElse("11:00"));
        LocalTime dinJusqua = LocalTime.parse(params.get("mission.repas.creneau_diner_jusqua", d).orElse("18:00"));
        var b = jdbc.queryForList("SELECT seuil_bascule_jours FROM bareme_indemnite WHERE date_effet <= ? ORDER BY date_effet DESC LIMIT 1", Integer.class, d);
        if (!b.isEmpty()) bascule = b.get(0);
        return new RepasEngine.Seuils(presence, intervalle, petitApres, dejJusqua, dinJusqua, bascule);
    }

    public Calcul calculer(Demande q) {
        if (q.depart() == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DEPART_REQUIS", "La date de départ est obligatoire");
        LocalDate effet = q.depart().toLocalDate();
        List<String> avert = new ArrayList<>();
        BigDecimal t1 = BigDecimal.ZERO, t2 = BigDecimal.ZERO;
        int r1 = 0, r2 = 0;
        if (q.remboursable()) {
            if (q.retour() == null) avert.add("RETOUR_NON_RENSEIGNE");
            else {
                if (q.echelle() == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ECHELLE_REQUISE", "L'échelle de l'agent est requise pour le calcul");
                var b = jdbc.queryForList("SELECT taux_repas_1, taux_repas_2, statut_validation FROM bareme_indemnite WHERE echelle = ? AND date_effet <= ? AND (date_fin IS NULL OR date_fin >= ?) ORDER BY date_effet DESC LIMIT 1", q.echelle(), effet, effet);
                if (b.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BAREME_ABSENT", "Aucun barème applicable pour l'échelle " + q.echelle() + " à cette date");
                t1 = (BigDecimal) b.get(0).get("taux_repas_1"); t2 = (BigDecimal) b.get(0).get("taux_repas_2");
                if (!"valide".equals(b.get(0).get("statut_validation"))) avert.add("BAREME_NON_VALIDE");
                var res = RepasEngine.calculer(q.depart(), q.retour(), seuilsAu(effet));
                r1 = res.repasTaux1(); r2 = res.repasTaux2();
            }
        }
        BigDecimal indemnites = m(t1.multiply(BigDecimal.valueOf(r1)).add(t2.multiply(BigDecimal.valueOf(r2))));

        BigDecimal frais = BigDecimal.ZERO;
        if ("transports_en_commun".equals(q.moyenTransportCode()) && q.fraisTransport() != null && q.fraisTransport().signum() > 0) frais = m(q.fraisTransport());

        BigDecimal tauxKm = null, km = BigDecimal.ZERO;
        if ("voiture_privee".equals(q.moyenTransportCode())) {
            if (q.puissanceFiscaleId() == null || q.distanceKm() == null || q.nombreTrajets() == null) avert.add("DONNEES_KILOMETRIQUES_INCOMPLETES");
            else {
                var p = jdbc.queryForList("SELECT taux_km FROM puissance_fiscale WHERE id = ? AND date_effet <= ? AND (date_fin IS NULL OR date_fin >= ?)", q.puissanceFiscaleId(), effet, effet);
                if (p.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PUISSANCE_FISCALE_INCONNUE", "Puissance fiscale inconnue ou sans taux à cette date");
                tauxKm = (BigDecimal) p.get(0).get("taux_km");
                km = m(q.distanceKm().multiply(BigDecimal.valueOf(q.nombreTrajets())).multiply(tauxKm));
            }
        }
        return new Calcul(r1, r2, t1, t2, indemnites, frais, tauxKm, km, m(indemnites.add(frais).add(km)), avert);
    }
}
