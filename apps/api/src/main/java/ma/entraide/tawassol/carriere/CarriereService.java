package ma.entraide.tawassol.carriere;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.param.ParametreService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrière et avancement d'échelon. La durée dans l'échelon est un paramètre daté (avancement.echelon.duree_mois, à valider) ;
 * un avancement n'est jamais automatique : la RH le valide avec la référence de la décision, et il est tracé.
 */
@Service
public class CarriereService {
    private final JdbcTemplate jdbc;
    private final ParametreService params;
    private final AuditService audit;
    private final Clock clock;

    public CarriereService(JdbcTemplate jdbc, ParametreService params, AuditService audit, Clock clock) { this.jdbc = jdbc; this.params = params; this.audit = audit; this.clock = clock; }

    public List<Map<String, Object>> historique(String personnelId) {
        return jdbc.queryForList("SELECT id, type_evenement AS type, ancienne_valeur AS ancienneValeur, nouvelle_valeur AS nouvelleValeur, date_effet AS dateEffet, decision_ref AS decisionRef FROM historique_carriere WHERE personnel_id = ? ORDER BY date_effet DESC, cree_le DESC", personnelId);
    }

    /** Agents actifs dont la durée dans l'échelon est atteinte et qui ne sont pas au dernier échelon de leur grade. */
    public Map<String, Object> propositions() {
        LocalDate today = LocalDate.now(clock);
        int mois = params.integer("avancement.echelon.duree_mois", today, 24);
        boolean valide = jdbc.queryForObject("SELECT COALESCE(MAX(statut_validation = 'valide'), 0) FROM parametre WHERE cle = 'avancement.echelon.duree_mois'", Boolean.class);
        List<Map<String, Object>> l = jdbc.queryForList("""
                SELECT p.id, p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, g.libelle_fr AS grade, g.nb_echelons AS nbEchelons, p.echelon, p.date_echelon AS dateEchelon,
                       DATE_ADD(p.date_echelon, INTERVAL ? MONTH) AS dateEligibilite
                FROM personnel p JOIN grade g ON g.id = p.grade_id
                WHERE p.est_actif = 1 AND p.date_echelon IS NOT NULL AND p.echelon REGEXP '^[0-9]+$' AND CAST(p.echelon AS UNSIGNED) < g.nb_echelons
                  AND DATE_ADD(p.date_echelon, INTERVAL ? MONTH) <= ? ORDER BY dateEligibilite, p.matricule LIMIT 500""", mois, mois, Date.valueOf(today));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dureeMois", mois); out.put("parametreValide", valide); out.put("propositions", l);
        return out;
    }

    @Transactional
    public Map<String, Object> avancer(AuthenticatedUser u, String personnelId, LocalDate dateEffet, String decisionRef) {
        if (decisionRef == null || decisionRef.isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DECISION_REQUISE", "La référence de la décision est obligatoire");
        LocalDate today = LocalDate.now(clock);
        if (dateEffet == null || dateEffet.isAfter(today.plusDays(1))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DATE_EFFET_INVALIDE", "La date d'effet ne peut pas être dans le futur");
        List<Map<String, Object>> l = jdbc.queryForList("SELECT p.echelon, p.date_echelon, p.est_actif, g.nb_echelons FROM personnel p JOIN grade g ON g.id = p.grade_id WHERE p.id = ?", personnelId);
        if (l.isEmpty()) throw ApiException.notFound();
        Map<String, Object> p = l.get(0);
        String ech = p.get("echelon") == null ? "" : p.get("echelon").toString();
        if (!ech.matches("\\d+")) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ECHELON_INCONNU", "L'échelon actuel n'est pas renseigné");
        int actuel = Integer.parseInt(ech), max = ((Number) p.get("nb_echelons")).intValue();
        if (actuel >= max) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DERNIER_ECHELON", "L'agent est au dernier échelon de son grade");
        if (p.get("date_echelon") != null && !dateEffet.isAfter(((Date) p.get("date_echelon")).toLocalDate()))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DATE_EFFET_ANTERIEURE", "La date d'effet doit suivre la date d'échelon actuelle");
        int n = jdbc.update("UPDATE personnel SET echelon = ?, date_echelon = ?, modifie_le = NOW() WHERE id = ? AND echelon = ?", String.valueOf(actuel + 1), Date.valueOf(dateEffet), personnelId, ech);
        if (n != 1) throw new ApiException(HttpStatus.CONFLICT, "CONFLIT", "La fiche a changé entre-temps");
        jdbc.update("INSERT INTO historique_carriere (id, personnel_id, type_evenement, ancienne_valeur, nouvelle_valeur, date_effet, decision_ref, cree_par) VALUES (UUID(),?,'echelon',?,?,?,?,?)",
                personnelId, ech, String.valueOf(actuel + 1), Date.valueOf(dateEffet), decisionRef.trim(), u.userId());
        audit.record(u.userId(), "CARRIERE_AVANCEMENT", "personnel", personnelId, "{\"echelon\":\"" + ech + "\"}", "{\"echelon\":\"" + (actuel + 1) + "\"}");
        return Map.of("echelon", String.valueOf(actuel + 1), "dateEchelon", dateEffet.toString());
    }

    // ---- examens ----

    public List<Map<String, Object>> examens() {
        return jdbc.queryForList("SELECT e.id, e.grade_cible_id AS gradeCibleId, g.libelle_fr AS gradeFr, g.libelle_ar AS gradeAr, e.annee, e.date_examen AS dateExamen, e.date_depot AS dateDepot, e.nb_postes AS nbPostes, e.lieu, e.details, e.resultats_ecrit AS resultatsEcrit, e.resultats_final AS resultatsFinal FROM examen_grade e LEFT JOIN grade g ON g.id = e.grade_cible_id ORDER BY e.date_examen DESC");
    }

    @Transactional
    public String creerExamen(AuthenticatedUser u, Map<String, Object> q) {
        String id = UUID.randomUUID().toString();
        LocalDate d = date(q.get("dateExamen"));
        if (d == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DATE_EXAMEN_REQUISE", "La date de l'examen est obligatoire");
        int postes = q.get("nbPostes") == null ? 0 : ((Number) q.get("nbPostes")).intValue();
        if (postes < 0) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NB_POSTES_INVALIDE", "Nombre de postes invalide");
        jdbc.update("INSERT INTO examen_grade (id, grade_cible_id, annee, date_examen, date_depot, nb_postes, lieu, details, resultats_ecrit, resultats_final) VALUES (?,?,?,?,?,?,?,?,?,?)", id, q.get("gradeCibleId"), d.getYear(), Date.valueOf(d),
                date(q.get("dateDepot")) == null ? null : Date.valueOf(date(q.get("dateDepot"))), postes, q.get("lieu"), q.get("details"), q.get("resultatsEcrit"), q.get("resultatsFinal"));
        audit.record(u.userId(), "EXAMEN_CREER", "examen_grade", id, null, null);
        return id;
    }

    @Transactional
    public void majExamen(AuthenticatedUser u, String id, Map<String, Object> q) {
        int n = jdbc.update("UPDATE examen_grade SET lieu = ?, details = ?, resultats_ecrit = ?, resultats_final = ?, nb_postes = COALESCE(?, nb_postes), modifie_le = NOW() WHERE id = ?", q.get("lieu"), q.get("details"), q.get("resultatsEcrit"), q.get("resultatsFinal"),
                q.get("nbPostes") == null ? null : ((Number) q.get("nbPostes")).intValue(), id);
        if (n == 0) throw ApiException.notFound();
        audit.record(u.userId(), "EXAMEN_MODIFIER", "examen_grade", id, null, null);
    }

    private static LocalDate date(Object o) { return o == null || o.toString().isBlank() ? null : LocalDate.parse(o.toString().substring(0, 10)); }
}
