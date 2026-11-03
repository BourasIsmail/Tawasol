package ma.entraide.tawassol.deplacement;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Exports Excel et analytique des missions. Même périmètre que la liste (clause unique de MissionService), permissions
 * mission.etats (exports) et mission.analytics (tableaux de bord). Les cellules texte commençant par = + - @ sont neutralisées
 * (injection de formule).
 */
@RestController
@RequestMapping("/api/v1/missions")
public class MissionEtatsController {
    private final NamedParameterJdbcTemplate named;
    private final MissionService missions;
    private final PermissionService permissions;
    private final AuditService audit;

    public MissionEtatsController(NamedParameterJdbcTemplate named, MissionService missions, PermissionService permissions, AuditService audit) {
        this.named = named; this.missions = missions; this.permissions = permissions; this.audit = audit;
    }

    private static final String BASE = " FROM ordre_mission om JOIN personnel p ON p.id = om.personnel_id JOIN structure s ON s.id = p.structure_id JOIN delegation_province d ON d.id = s.delegation_id LEFT JOIN grade g ON g.id = p.grade_id LEFT JOIN programme_mission pm ON pm.id = om.programme_id ";

    private String filtres(String where, MapSqlParameterSource p, Integer annee, String statut, String programmeId, String delegationId) {
        StringBuilder w = new StringBuilder(where);
        if (annee != null) { w.append(" AND om.annee = :annee"); p.addValue("annee", annee); }
        if (statut != null && !statut.isBlank()) { w.append(" AND om.statut = :statut"); p.addValue("statut", statut); }
        if (programmeId != null && !programmeId.isBlank()) { w.append(" AND om.programme_id = :prog"); p.addValue("prog", programmeId); }
        if (delegationId != null && !delegationId.isBlank()) { w.append(" AND s.delegation_id = :deleg"); p.addValue("deleg", delegationId); }
        return w.toString();
    }

    private static String sur(String v) { return v != null && !v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 ? "'" + v : v; }

    private static ResponseEntity<byte[]> xlsx(Workbook wb, String nom) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); wb.write(o); wb.close();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=\"" + nom + ".xlsx\"").header("X-Content-Type-Options", "nosniff").body(o.toByteArray());
    }

    private static void entete(Sheet sh, String... titres) {
        Row r = sh.createRow(0);
        CellStyle st = sh.getWorkbook().createCellStyle(); Font f = sh.getWorkbook().createFont(); f.setBold(true); st.setFont(f);
        for (int i = 0; i < titres.length; i++) { Cell c = r.createCell(i); c.setCellValue(titres[i]); c.setCellStyle(st); sh.setColumnWidth(i, 18 * 256); }
    }

    /** Liste détaillée des missions (une ligne par mission). */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) Integer annee, @RequestParam(required = false) String statut,
                                         @RequestParam(required = false) String programmeId, @RequestParam(required = false) String delegationId) throws Exception {
        permissions.exiger(u, "mission.etats");
        MapSqlParameterSource p = new MapSqlParameterSource();
        String w = filtres(missions.clauseDePerimetre(u, p), p, annee, statut, programmeId, delegationId);
        List<Map<String, Object>> rows = named.queryForList("SELECT om.*, p.matricule, p.nom_fr, p.prenom_fr, g.libelle_fr AS grade, d.nom_deleg_fr AS delegation, pm.nom_fr AS programme" + BASE + w + " ORDER BY om.annee DESC, om.date_depart DESC LIMIT 20000", p);
        Workbook wb = new XSSFWorkbook(); Sheet sh = wb.createSheet("Missions");
        entete(sh, "N° ordre", "N° ordre (hérité)", "N° état", "N° état (hérité)", "Statut", "Matricule", "Nom", "Prénom", "Grade", "Délégation", "Programme", "Objet", "Départ", "Retour",
                "Repas taux 1", "Repas taux 2", "Indemnités", "Frais transport", "Km", "Total", "Montant historique non reproductible", "Anomalie de numéro");
        int i = 1;
        for (Map<String, Object> m : rows) {
            Row r = sh.createRow(i++); int c = 0;
            for (Object v : new Object[]{m.get("numero_ordm"), m.get("numero_ordm_legacy"), m.get("numero_etat"), m.get("numero_etat_legacy"), m.get("statut"), m.get("matricule"), m.get("nom_fr"), m.get("prenom_fr"), m.get("grade"), m.get("delegation"), m.get("programme"), m.get("objet"), m.get("date_depart"), m.get("date_retour")})
                r.createCell(c++).setCellValue(v == null ? "" : sur(v.toString()));
            for (String k : new String[]{"nombre_repas_taux_1", "nombre_repas_taux_2", "montant_indemnites", "frais_transport", "montant_km", "montant_total"})
                r.createCell(c++).setCellValue(m.get(k) == null ? 0 : ((Number) m.get(k)).doubleValue());
            r.createCell(c++).setCellValue(Boolean.TRUE.equals(m.get("ecart_moteur_historique")) || (m.get("ecart_moteur_historique") instanceof Number n && n.intValue() == 1) ? "oui" : "");
            r.createCell(c).setCellValue(String.valueOf(m.get("anomalie_numero")));
        }
        audit.record(u.userId(), "MISSION_EXPORT", "ordre_mission", null, null, "{\"lignes\":" + rows.size() + "}");
        return xlsx(wb, "missions");
    }

    /** « État global des sommes dues » : une ligne par agent (montants des missions finalisées ou payées), avec total par programme. */
    @GetMapping("/etat-global")
    public ResponseEntity<byte[]> etatGlobal(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam Integer annee, @RequestParam(required = false) String programmeId,
                                             @RequestParam(required = false) String delegationId) throws Exception {
        permissions.exiger(u, "mission.etats");
        MapSqlParameterSource p = new MapSqlParameterSource();
        String w = filtres(missions.clauseDePerimetre(u, p), p, annee, null, programmeId, delegationId) + " AND om.statut IN ('finalisee','payee')";
        List<Map<String, Object>> rows = named.queryForList("SELECT p.matricule, p.nom_fr, p.prenom_fr, g.libelle_fr AS grade, g.echelle, COALESCE(pm.nom_fr, 'Missions propres') AS programme, COUNT(*) AS nb, " +
                "SUM(om.montant_total) AS total, MAX(om.numero_etat) AS dernier_etat" + BASE + w + " GROUP BY p.id, p.matricule, p.nom_fr, p.prenom_fr, g.libelle_fr, g.echelle, pm.nom_fr ORDER BY programme, p.nom_fr", p);
        Workbook wb = new XSSFWorkbook(); Sheet sh = wb.createSheet("Sommes dues " + annee);
        entete(sh, "N°", "Dernier N° état", "Année", "Matricule", "Nom et prénom", "Grade", "Programme", "Missions", "Montant");
        int i = 1; BigDecimal total = BigDecimal.ZERO; Map<String, BigDecimal> parProg = new TreeMap<>();
        for (Map<String, Object> m : rows) {
            Row r = sh.createRow(i); int c = 0;
            r.createCell(c++).setCellValue(i); r.createCell(c++).setCellValue(sur(String.valueOf(m.getOrDefault("dernier_etat", "")).replace("null", ""))); r.createCell(c++).setCellValue(annee);
            r.createCell(c++).setCellValue(sur(String.valueOf(m.get("matricule")))); r.createCell(c++).setCellValue(sur(m.get("nom_fr") + " " + m.get("prenom_fr")));
            r.createCell(c++).setCellValue(sur(String.valueOf(m.get("grade")).replace("null", ""))); r.createCell(c++).setCellValue(sur(String.valueOf(m.get("programme"))));
            r.createCell(c++).setCellValue(((Number) m.get("nb")).intValue()); BigDecimal t = new BigDecimal(m.get("total").toString()); r.createCell(c).setCellValue(t.doubleValue());
            total = total.add(t); parProg.merge(String.valueOf(m.get("programme")), t, BigDecimal::add); i++;
        }
        i++;
        for (var e : parProg.entrySet()) { Row r = sh.createRow(i++); r.createCell(6).setCellValue("Total " + sur(e.getKey())); r.createCell(8).setCellValue(e.getValue().doubleValue()); }
        Row r = sh.createRow(i); r.createCell(6).setCellValue("TOTAL GÉNÉRAL"); r.createCell(8).setCellValue(total.doubleValue());
        audit.record(u.userId(), "MISSION_ETAT_GLOBAL", "ordre_mission", null, null, "{\"annee\":" + annee + ",\"lignes\":" + rows.size() + "}");
        return xlsx(wb, "etat-global-" + annee);
    }

    /** Tableau de bord : totaux par statut, par mois, par délégation et consommation des plafonds par programme. */
    @GetMapping("/analytique")
    public Map<String, Object> analytique(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam Integer annee) {
        permissions.exiger(u, "mission.analytics");
        MapSqlParameterSource p = new MapSqlParameterSource();
        String w = filtres(missions.clauseDePerimetre(u, p), p, annee, null, null, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("annee", annee);
        out.put("parStatut", named.queryForList("SELECT om.statut, COUNT(*) AS nombre, COALESCE(SUM(om.montant_total),0) AS montant" + BASE + w + " GROUP BY om.statut ORDER BY om.statut", p));
        out.put("parMois", named.queryForList("SELECT MONTH(om.date_depart) AS mois, COUNT(*) AS nombre, COALESCE(SUM(om.montant_total),0) AS montant" + BASE + w + " AND om.statut IN ('finalisee','payee') GROUP BY MONTH(om.date_depart) ORDER BY mois", p));
        out.put("parDelegation", named.queryForList("SELECT d.nom_deleg_fr AS delegation, COUNT(*) AS nombre, COALESCE(SUM(om.montant_total),0) AS montant" + BASE + w + " AND om.statut IN ('finalisee','payee') GROUP BY d.id, d.nom_deleg_fr ORDER BY montant DESC LIMIT 15", p));
        out.put("parProgramme", named.queryForList("SELECT COALESCE(pm.nom_fr, 'Missions propres') AS programme, COUNT(*) AS nombre, COALESCE(SUM(om.montant_total),0) AS montant" + BASE + w + " AND om.statut IN ('finalisee','payee') GROUP BY pm.id, pm.nom_fr ORDER BY montant DESC", p));
        out.put("anomalies", named.queryForMap("SELECT SUM(om.retour_non_renseigne) AS retourNonRenseigne, SUM(om.anomalie_depart_arrivee) AS departEgalArrivee, SUM(om.ecart_moteur_historique) AS montantHistoriqueNonReproductible, SUM(om.anomalie_numero <> 'aucune') AS anomaliesNumero" + BASE + w, p));
        return out;
    }
}
