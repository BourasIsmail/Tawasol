package ma.entraide.tawassol.demande;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.param.ParametreService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Solde de congé annuel d'un agent, année par année, selon le modèle de Suivi RH :
 * <pre>reste d'une année = droit + report saisi − jours imputés à cette année</pre>
 * Un congé consomme d'abord le reste des années précédentes autorisées, puis l'année courante ; l'imputation de chaque congé
 * (table conge_imputation) dit combien de jours sont pris sur quelle année. Règles à valider par la RH et le juridique (H-CONGE-*) :
 * <ul>
 *   <li>droit annuel : conge.annuel.droit_jours, ou conge.annuel.droit_jours.&lt;CATEGORIE&gt; ;</li>
 *   <li>années antérieures utilisables : conge.report.annees_max (0 = aucune) et plafond conge.report.plafond_jours ;</li>
 *   <li>jours réservés : demandes en cours, période non passée et pas encore comptées dans le solde.</li>
 * </ul>
 */
@Service
public class SoldeCongeService {
    /** disponible = reste de l'année après réservations ; utilisable = disponible + reste des années précédentes autorisées. */
    public record Solde(int annee, BigDecimal droit, BigDecimal pris, BigDecimal reporte, BigDecimal reserve, BigDecimal disponible, BigDecimal utilisable, String source, boolean aVerifier) {}
    public record Imputation(int annee, BigDecimal jours) {}

    private final JdbcTemplate jdbc;
    private final ParametreService params;
    private final DemandeRepository demandes;
    private final Clock clock;

    public SoldeCongeService(JdbcTemplate jdbc, ParametreService params, DemandeRepository demandes, Clock clock) {
        this.jdbc = jdbc;
        this.params = params;
        this.demandes = demandes;
        this.clock = clock;
    }

    /** Droit annuel de l'agent : valeur de sa catégorie si elle est paramétrée, sinon valeur par défaut (à valider). */
    public BigDecimal droit(String personnelId, int annee) {
        LocalDate debut = LocalDate.of(annee, 1, 1);
        List<String> cat = jdbc.queryForList("SELECT categorie_agent FROM personnel WHERE id = ? AND categorie_agent IS NOT NULL AND categorie_agent <> ''", String.class, personnelId);
        if (!cat.isEmpty()) {
            String cle = "conge.annuel.droit_jours." + cat.get(0).trim().toUpperCase();
            var specifique = params.get(cle, debut).or(() -> params.premiere(cle));
            if (specifique.isPresent()) return new BigDecimal(specifique.get());
        }
        // une année antérieure à la première date d'effet du paramètre prend la valeur la plus ancienne (H-CONGE-1) : jamais 0 par simple défaut de date
        return params.get("conge.annuel.droit_jours", debut).or(() -> params.premiere("conge.annuel.droit_jours")).map(BigDecimal::new).orElse(BigDecimal.ZERO);
    }

    private record Ligne(BigDecimal droit, BigDecimal pris, BigDecimal reporte, String source, boolean aVerifier) {
        /** Un droit saisi ou officiel fait foi ; sinon (ligne reconstituée ou tenue par le système) le droit suit les paramètres. */
        boolean droitFige() { return "saisi".equals(source) || "officiel".equals(source); }
    }

    private Ligne ligne(String personnelId, int annee) {
        List<Ligne> rows = jdbc.query("SELECT droit, pris, reporte, source, a_verifier FROM solde_conge WHERE personnel_id = ? AND annee = ?",
                (rs, i) -> new Ligne(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getString(4), rs.getBoolean(5)), personnelId, annee);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Reste d'une année avant réservations : droit + report saisi − pris (peut être négatif). */
    private BigDecimal reste(String personnelId, int annee) {
        Ligne l = ligne(personnelId, annee);
        BigDecimal droit = l != null && l.droitFige() ? l.droit() : droit(personnelId, annee);
        return droit.add(l == null ? BigDecimal.ZERO : l.reporte()).subtract(l == null ? BigDecimal.ZERO : l.pris());
    }

    private int anneesReport(int annee) { return params.integer("conge.report.annees_max", LocalDate.of(annee, 1, 1), 0); }

    private BigDecimal plafondReport(int annee) { return params.decimal("conge.report.plafond_jours", LocalDate.of(annee, 1, 1), BigDecimal.ZERO); }

    /** Jours des années précédentes encore utilisables pour l'année donnée (0 si le report est désactivé). */
    BigDecimal reportUtilisable(String personnelId, int annee) {
        int n = anneesReport(annee);
        BigDecimal budget = plafondReport(annee);
        if (n <= 0 || budget.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (int k = n; k >= 1; k--) total = total.add(reste(personnelId, annee - k).max(BigDecimal.ZERO));
        return total.min(budget);
    }

    public Solde get(String personnelId, int annee, String excludeDemandeId) {
        Ligne l = ligne(personnelId, annee);
        BigDecimal droit = l != null && l.droitFige() ? l.droit() : droit(personnelId, annee);
        BigDecimal pris = l == null ? BigDecimal.ZERO : l.pris();
        BigDecimal reporte = l == null ? BigDecimal.ZERO : l.reporte();
        BigDecimal reserve = demandes.joursReserves(personnelId, excludeDemandeId == null ? "-" : excludeDemandeId, annee, LocalDate.now(clock));
        BigDecimal disponible = droit.add(reporte).subtract(pris).subtract(reserve);
        return new Solde(annee, droit, pris, reporte, reserve, disponible, disponible.add(reportUtilisable(personnelId, annee)),
                l == null ? "calcule" : l.source(), l != null && l.aVerifier());
    }

    /**
     * Soldes année par année (plus récente d'abord) : années ayant une ligne de solde, une imputation ou un congé annuel en cours,
     * plus l'année courante.
     */
    public List<Solde> parAnnee(String personnelId) {
        java.util.TreeSet<Integer> annees = new java.util.TreeSet<>(java.util.Comparator.reverseOrder());
        annees.add(LocalDate.now(clock).getYear());
        annees.addAll(jdbc.queryForList("SELECT annee FROM solde_conge WHERE personnel_id = ?", Integer.class, personnelId));
        annees.addAll(jdbc.queryForList("SELECT DISTINCT ci.annee FROM conge_imputation ci JOIN demande d ON d.id = ci.demande_id WHERE d.personnel_id = ?", Integer.class, personnelId));
        annees.addAll(jdbc.queryForList("""
                SELECT DISTINCT YEAR(d.date_debut) FROM demande d JOIN type_demande t ON t.id = d.type_demande_id
                 WHERE d.personnel_id = ? AND t.code = 'CONGE_ANNUEL' AND d.date_debut IS NOT NULL AND d.statut IN ('soumise','validee_responsable')""", Integer.class, personnelId));
        return annees.stream().map(a -> get(personnelId, a, null)).toList();
    }

    /** Répartition d'un congé : reste des années précédentes autorisées d'abord (plus ancienne en premier), puis l'année courante. */
    List<Imputation> allouer(String personnelId, int annee, BigDecimal jours) {
        List<Imputation> out = new ArrayList<>();
        BigDecimal restant = jours;
        int n = anneesReport(annee);
        BigDecimal budget = plafondReport(annee);
        if (n > 0 && budget.signum() > 0) {
            for (int k = n; k >= 1 && restant.signum() > 0 && budget.signum() > 0; k--) {
                BigDecimal prise = reste(personnelId, annee - k).max(BigDecimal.ZERO).min(restant).min(budget);
                if (prise.signum() > 0) { out.add(new Imputation(annee - k, prise)); restant = restant.subtract(prise); budget = budget.subtract(prise); }
            }
        }
        if (restant.signum() > 0) out.add(new Imputation(annee, restant));
        return out;
    }

    public boolean aImputation(String demandeId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM conge_imputation WHERE demande_id = ?", Integer.class, demandeId);
        return n != null && n > 0;
    }

    /** Un congé annuel approuvé décompte ses jours : répartition, écriture des imputations et mise à jour du solde de chaque année. */
    public List<Imputation> imputer(String demandeId, String personnelId, int annee, BigDecimal jours) {
        if (aImputation(demandeId)) return List.of();   // déjà compté (ex. congé repris de Suivi RH) : jamais deux fois
        List<Imputation> repartition = allouer(personnelId, annee, jours);
        for (Imputation i : repartition) {
            jdbc.update("INSERT INTO conge_imputation (demande_id, annee, jours) VALUES (?,?,?) ON DUPLICATE KEY UPDATE jours = VALUES(jours)", demandeId, i.annee(), i.jours());
            deduire(personnelId, i.annee(), i.jours());
        }
        return repartition;
    }

    /** Un congé compté puis annulé ou rejeté restitue ses jours, année par année. Retourne faux s'il n'avait aucune imputation. */
    public boolean liberer(String demandeId, String personnelId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT annee, jours FROM conge_imputation WHERE demande_id = ?", demandeId);
        for (Map<String, Object> m : l) restituer(personnelId, ((Number) m.get("annee")).intValue(), (BigDecimal) m.get("jours"));
        jdbc.update("DELETE FROM conge_imputation WHERE demande_id = ?", demandeId);
        return !l.isEmpty();
    }

    void deduire(String personnelId, int annee, BigDecimal jours) {
        jdbc.update("INSERT INTO solde_conge (id, personnel_id, annee, droit, pris, reporte, source) VALUES (?,?,?,?,?,0,'systeme') ON DUPLICATE KEY UPDATE pris = pris + VALUES(pris)",
                UUID.randomUUID().toString(), personnelId, annee, droit(personnelId, annee), jours);
    }

    void restituer(String personnelId, int annee, BigDecimal jours) {
        jdbc.update("UPDATE solde_conge SET pris = GREATEST(0, pris - ?) WHERE personnel_id = ? AND annee = ?", jours, personnelId, annee);
    }

    /** Jours imputés par année, tous agents confondus (contrôle de cohérence). */
    public Map<Integer, BigDecimal> imputesParAnnee() {
        Map<Integer, BigDecimal> m = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT annee, SUM(jours) AS j FROM conge_imputation GROUP BY annee")) m.put(((Number) r.get("annee")).intValue(), (BigDecimal) r.get("j"));
        return m;
    }
}
