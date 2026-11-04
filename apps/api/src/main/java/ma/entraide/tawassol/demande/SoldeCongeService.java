package ma.entraide.tawassol.demande;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.entraide.tawassol.param.ParametreService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Solde de congé annuel d'un agent pour une année : droit + report − pris − jours réservés.
 * Toutes les règles sont des paramètres datés, à valider par la RH et le juridique (voir HYPOTHESES.md, H-CONGE-*) :
 *  - droit annuel : conge.annuel.droit_jours, ou conge.annuel.droit_jours.&lt;CATEGORIE&gt; selon la catégorie de l'agent ;
 *  - report : désactivé par défaut (conge.report.annees_max = 0), plafonné par conge.report.plafond_jours ;
 *  - jours réservés : seulement les demandes en cours dont la période n'est pas passée.
 */
@Service
public class SoldeCongeService {
    public record Solde(int annee, BigDecimal droit, BigDecimal pris, BigDecimal reporte, BigDecimal reserve, BigDecimal disponible, String source, boolean aVerifier) {}

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
        // une année antérieure à la première date d'effet du paramètre prend la valeur la plus ancienne (H-CONGE-1) : jamais 0 par défaut de date
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

    private BigDecimal droitEffectif(String personnelId, int annee, Ligne l) {
        return l != null && l.droitFige() ? l.droit() : droit(personnelId, annee);
    }

    /**
     * Report automatique vers l'année donnée : jours non pris de l'année précédente, plafonnés, sur au plus N années.
     * N = 0 (défaut) : aucun report automatique ; seul le report saisi par la RH dans solde_conge compte.
     */
    BigDecimal reportAuto(String personnelId, int annee, int profondeur, BigDecimal plafond) {
        if (profondeur <= 0 || plafond.signum() <= 0) return BigDecimal.ZERO;
        int prec = annee - 1;
        Ligne l = ligne(personnelId, prec);
        BigDecimal droit = droitEffectif(personnelId, prec, l);
        BigDecimal pris = l == null ? BigDecimal.ZERO : l.pris();
        BigDecimal rep = (l == null ? BigDecimal.ZERO : l.reporte()).add(reportAuto(personnelId, prec, profondeur - 1, plafond));
        BigDecimal reste = droit.add(rep).subtract(pris);
        return reste.signum() <= 0 ? BigDecimal.ZERO : reste.min(plafond);
    }

    public Solde get(String personnelId, int annee, String excludeDemandeId) {
        LocalDate debut = LocalDate.of(annee, 1, 1);
        Ligne l = ligne(personnelId, annee);
        BigDecimal droit = droitEffectif(personnelId, annee, l);
        BigDecimal pris = l == null ? BigDecimal.ZERO : l.pris();
        int profondeur = params.integer("conge.report.annees_max", debut, 0);
        BigDecimal plafond = params.decimal("conge.report.plafond_jours", debut, BigDecimal.ZERO);
        BigDecimal reporte = (l == null ? BigDecimal.ZERO : l.reporte()).add(reportAuto(personnelId, annee, profondeur, plafond));
        BigDecimal reserve = demandes.joursReserves(personnelId, excludeDemandeId == null ? "-" : excludeDemandeId, annee, LocalDate.now(clock));
        return new Solde(annee, droit, pris, reporte, reserve, droit.add(reporte).subtract(pris).subtract(reserve),
                l == null ? "calcule" : l.source(), l != null && l.aVerifier());
    }

    /**
     * Soldes année par année (plus récente d'abord) : années ayant une ligne de solde ou un congé annuel en cours ou approuvé, plus l'année courante.
     */
    public List<Solde> parAnnee(String personnelId) {
        java.util.TreeSet<Integer> annees = new java.util.TreeSet<>(java.util.Comparator.reverseOrder());
        annees.add(LocalDate.now(clock).getYear());
        annees.addAll(jdbc.queryForList("SELECT annee FROM solde_conge WHERE personnel_id = ?", Integer.class, personnelId));
        annees.addAll(jdbc.queryForList("""
                SELECT DISTINCT YEAR(d.date_debut) FROM demande d JOIN type_demande t ON t.id = d.type_demande_id
                 WHERE d.personnel_id = ? AND t.code = 'CONGE_ANNUEL' AND d.date_debut IS NOT NULL AND d.statut IN ('soumise','validee_responsable','approuvee')""", Integer.class, personnelId));
        return annees.stream().map(a -> get(personnelId, a, null)).toList();
    }

    /** Un congé annuel approuvé décompte ses jours de l'année de sa date de début. */
    public void deduire(String personnelId, int annee, BigDecimal jours) {
        jdbc.update("INSERT INTO solde_conge (id, personnel_id, annee, droit, pris, reporte, source) VALUES (?,?,?,?,?,0,'systeme') ON DUPLICATE KEY UPDATE pris = pris + VALUES(pris)",
                UUID.randomUUID().toString(), personnelId, annee, droit(personnelId, annee), jours);
    }

    /** Un congé approuvé puis annulé ou rejeté restitue ses jours (jamais en dessous de zéro). */
    public void restituer(String personnelId, int annee, BigDecimal jours) {
        jdbc.update("UPDATE solde_conge SET pris = GREATEST(0, pris - ?) WHERE personnel_id = ? AND annee = ?", jours, personnelId, annee);
    }
}
