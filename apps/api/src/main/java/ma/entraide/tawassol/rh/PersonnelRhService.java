package ma.entraide.tawassol.rh;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion du dossier agent par la RH. Sémantique « patch » : un champ absent reste inchangé.
 * Tout changement d'affectation, de grade, d'échelon ou de position alimente l'historique de carrière ; l'audit ne garde pas
 * les valeurs sensibles (CIN, téléphone, e-mail, date de naissance), seulement le fait qu'elles ont changé.
 */
@Service
public class PersonnelRhService {
    public record Input(String matricule, String nomFr, String prenomFr, String nomAr, String prenomAr, String structureId, String gradeId,
                        LocalDate dateRecrutement, String email, String telephone, String cin, String sexe, String situationFamiliale,
                        LocalDate dateNaissance, String positionAdministrative, String echelon, Integer echelle, LocalDate dateEchelon,
                        String decisionRef, LocalDate dateEffet) {}

    private static final Set<String> SENSIBLES = Set.of("email", "telephone", "cin", "date_naissance");
    private static final Set<String> SEXES = Set.of("M", "F");
    private static final Set<String> SITUATIONS = Set.of("celibataire", "marie", "divorce", "veuf");
    private static final Set<String> POSITIONS = Set.of("activite", "detachement", "disponibilite", "mise_a_disposition", "autre");

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public PersonnelRhService(JdbcTemplate jdbc, AuditService audit, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc; this.audit = audit; this.json = json; this.clock = clock;
    }

    /** Fiche complète d'un agent (identité et dossier RH ; ni paie ni primes). Chaque consultation est journalisée. */
    public Map<String, Object> fiche(AuthenticatedUser actor, String id) {
        Map<String, Object> m = jdbc.queryForList("""
            SELECT p.id, p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr, p.grade_id AS gradeId, g.libelle_fr AS gradeFr, p.structure_id AS structureId,
                   s.nom_fr AS structureFr, p.date_recrutement AS dateRecrutement, p.email, p.telephone, p.cin, p.sexe, p.situation_familiale AS situationFamiliale, p.date_naissance AS dateNaissance,
                   p.position_administrative AS positionAdministrative, p.echelon, p.echelle, p.date_echelon AS dateEchelon, p.est_actif AS actif
              FROM personnel p JOIN structure s ON s.id = p.structure_id LEFT JOIN grade g ON g.id = p.grade_id WHERE p.id = ?
            """, id).stream().findFirst().orElseThrow(ma.entraide.tawassol.common.ApiException::notFound);
        audit.record(actor.userId(), "PERSONNEL_READ", "personnel", id, null, null);
        return m;
    }

    public Optional<String> idByMatricule(String matricule) {
        return jdbc.queryForList("SELECT id FROM personnel WHERE matricule = ?", String.class, matricule).stream().findFirst();
    }

    @Transactional
    public String create(AuthenticatedUser actor, Input in) {
        require(in.matricule(), "matricule"); require(in.nomFr(), "nomFr"); require(in.prenomFr(), "prenomFr");
        require(in.nomAr(), "nomAr"); require(in.prenomAr(), "prenomAr"); require(in.structureId(), "structureId");
        if (in.dateRecrutement() == null) throw invalid("dateRecrutement", "obligatoire");
        if (in.matricule().length() > 10) throw invalid("matricule", "10 caractères maximum");
        check(in);
        if (idByMatricule(in.matricule()).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_MATRICULE", "Ce matricule existe déjà");
        uniqueCin(in.cin(), null);
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO personnel (id, grade_id, structure_id, matricule, nom_ar, prenom_ar, nom_fr, prenom_fr, date_recrutement, email, echelon, echelle, date_echelon,
                                   date_naissance, cin, sexe, situation_familiale, telephone, position_administrative)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, id, in.gradeId(), in.structureId(), in.matricule().trim(), in.nomAr().trim(), in.prenomAr().trim(), in.nomFr().trim(), in.prenomFr().trim(),
                in.dateRecrutement(), blank(in.email()), blank(in.echelon()), in.echelle(), in.dateEchelon(), in.dateNaissance(), blank(in.cin()), blank(in.sexe()),
                blank(in.situationFamiliale()), blank(in.telephone()), in.positionAdministrative() == null ? "activite" : in.positionAdministrative());
        historique(id, actor, "affectation", null, structureCode(in.structureId()), in.dateEffet() != null ? in.dateEffet() : in.dateRecrutement(), in.decisionRef());
        audit.record(actor.userId(), "PERSONNEL_CREE", "personnel", id, null, "{\"matricule\":\"" + in.matricule().trim() + "\"}");
        return id;
    }

    @Transactional
    public void update(AuthenticatedUser actor, String id, Input in) {
        Map<String, Object> old = jdbc.queryForList("SELECT * FROM personnel WHERE id = ?", id).stream().findFirst().orElseThrow(ApiException::notFound);
        check(in);
        if (in.matricule() != null && !in.matricule().equals(old.get("matricule")) && idByMatricule(in.matricule()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_MATRICULE", "Ce matricule existe déjà");
        }
        uniqueCin(in.cin(), id);
        Map<String, Object> neu = new LinkedHashMap<>();
        put(neu, old, "matricule", in.matricule()); put(neu, old, "nom_fr", in.nomFr()); put(neu, old, "prenom_fr", in.prenomFr());
        put(neu, old, "nom_ar", in.nomAr()); put(neu, old, "prenom_ar", in.prenomAr()); put(neu, old, "structure_id", in.structureId());
        put(neu, old, "grade_id", in.gradeId()); put(neu, old, "date_recrutement", in.dateRecrutement()); put(neu, old, "email", blank(in.email()));
        put(neu, old, "telephone", blank(in.telephone())); put(neu, old, "cin", blank(in.cin())); put(neu, old, "sexe", blank(in.sexe()));
        put(neu, old, "situation_familiale", blank(in.situationFamiliale())); put(neu, old, "date_naissance", in.dateNaissance());
        put(neu, old, "position_administrative", in.positionAdministrative()); put(neu, old, "echelon", blank(in.echelon()));
        put(neu, old, "echelle", in.echelle()); put(neu, old, "date_echelon", in.dateEchelon());
        Map<String, Object> avant = new LinkedHashMap<>(), apres = new LinkedHashMap<>();
        for (var e : neu.entrySet()) {
            Object a = old.get(e.getKey()), b = e.getValue();
            if (java.util.Objects.equals(norm(a), norm(b))) continue;
            avant.put(e.getKey(), SENSIBLES.contains(e.getKey()) ? "***" : a == null ? null : a.toString());
            apres.put(e.getKey(), SENSIBLES.contains(e.getKey()) ? "***" : b == null ? null : b.toString());
        }
        if (avant.isEmpty()) return;
        jdbc.update("""
            UPDATE personnel SET matricule=?, nom_fr=?, prenom_fr=?, nom_ar=?, prenom_ar=?, structure_id=?, grade_id=?, date_recrutement=?, email=?, telephone=?, cin=?,
                   sexe=?, situation_familiale=?, date_naissance=?, position_administrative=?, echelon=?, echelle=?, date_echelon=? WHERE id=?
            """, neu.get("matricule"), neu.get("nom_fr"), neu.get("prenom_fr"), neu.get("nom_ar"), neu.get("prenom_ar"), neu.get("structure_id"), neu.get("grade_id"),
                neu.get("date_recrutement"), neu.get("email"), neu.get("telephone"), neu.get("cin"), neu.get("sexe"), neu.get("situation_familiale"), neu.get("date_naissance"),
                neu.get("position_administrative"), neu.get("echelon"), neu.get("echelle"), neu.get("date_echelon"), id);
        LocalDate effet = in.dateEffet() != null ? in.dateEffet() : LocalDate.now(clock);
        if (avant.containsKey("structure_id")) historique(id, actor, "affectation", structureCode((String) old.get("structure_id")), structureCode((String) neu.get("structure_id")), effet, in.decisionRef());
        if (avant.containsKey("grade_id")) historique(id, actor, "grade", gradeLabel((String) old.get("grade_id")), gradeLabel((String) neu.get("grade_id")), effet, in.decisionRef());
        if (avant.containsKey("echelon") || avant.containsKey("date_echelon") || avant.containsKey("echelle")) {
            historique(id, actor, "echelon", str(old.get("echelle")) + "/" + str(old.get("echelon")), str(neu.get("echelle")) + "/" + str(neu.get("echelon")), effet, in.decisionRef());
        }
        if (avant.containsKey("position_administrative")) historique(id, actor, "position", (String) old.get("position_administrative"), (String) neu.get("position_administrative"), effet, in.decisionRef());
        try {
            audit.record(actor.userId(), "PERSONNEL_MODIFIE", "personnel", id, json.writeValueAsString(avant), json.writeValueAsString(apres));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void deactivate(AuthenticatedUser actor, String id) {
        if (jdbc.update("UPDATE personnel SET est_actif = 0 WHERE id = ? AND est_actif = 1", id) == 0) throw ApiException.notFound();
        jdbc.update("UPDATE utilisateur SET est_actif = 0 WHERE personnel_id = ?", id);
        jdbc.update("UPDATE responsable_perimetre SET est_actif = 0, date_fin = COALESCE(date_fin, ?) WHERE personnel_id = ?", LocalDate.now(clock), id);
        audit.record(actor.userId(), "PERSONNEL_DESACTIVE", "personnel", id, null, null);
    }

    public List<Map<String, Object>> carriere(String id) {
        return jdbc.queryForList("SELECT id, type_evenement AS type, ancienne_valeur AS ancienneValeur, nouvelle_valeur AS nouvelleValeur, date_effet AS dateEffet, decision_ref AS decisionRef FROM historique_carriere WHERE personnel_id = ? ORDER BY date_effet DESC, cree_le DESC", id);
    }

    // ---- utilitaires ----

    private void check(Input in) {
        if (in.structureId() != null && jdbc.queryForObject("SELECT COUNT(*) FROM structure WHERE id = ? AND est_active = 1", Integer.class, in.structureId()) == 0) throw invalid("structureId", "structure inconnue ou inactive");
        if (in.gradeId() != null && jdbc.queryForObject("SELECT COUNT(*) FROM grade WHERE id = ?", Integer.class, in.gradeId()) == 0) throw invalid("gradeId", "grade inconnu");
        if (in.sexe() != null && !in.sexe().isBlank() && !SEXES.contains(in.sexe())) throw invalid("sexe", "M ou F");
        if (in.situationFamiliale() != null && !in.situationFamiliale().isBlank() && !SITUATIONS.contains(in.situationFamiliale())) throw invalid("situationFamiliale", "valeur inconnue");
        if (in.positionAdministrative() != null && !POSITIONS.contains(in.positionAdministrative())) throw invalid("positionAdministrative", "valeur inconnue");
        if (in.email() != null && !in.email().isBlank() && !in.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw invalid("email", "adresse invalide");
    }

    private void uniqueCin(String cin, String selfId) {
        if (cin == null || cin.isBlank()) return;
        List<String> ids = jdbc.queryForList("SELECT id FROM personnel WHERE cin = ?", String.class, cin.trim());
        if (!ids.isEmpty() && !ids.get(0).equals(selfId)) throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CIN", "Ce CIN existe déjà");
    }

    private void historique(String pid, AuthenticatedUser actor, String type, String ancienne, String nouvelle, LocalDate effet, String decision) {
        jdbc.update("INSERT INTO historique_carriere (id, personnel_id, type_evenement, ancienne_valeur, nouvelle_valeur, date_effet, decision_ref, cree_par) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), pid, type, ancienne, nouvelle == null ? "—" : nouvelle, effet, decision, actor == null ? null : actor.userId());
    }

    private String structureCode(String id) { return id == null ? null : jdbc.queryForList("SELECT code FROM structure WHERE id = ?", String.class, id).stream().findFirst().orElse(null); }
    private String gradeLabel(String id) { return id == null ? null : jdbc.queryForList("SELECT CONCAT(code, ' ', libelle_fr) FROM grade WHERE id = ?", String.class, id).stream().findFirst().orElse(null); }
    private static void put(Map<String, Object> neu, Map<String, Object> old, String k, Object v) { neu.put(k, v != null ? v : old.get(k)); }
    private static Object norm(Object o) { return o == null ? null : o.toString(); }
    private static String str(Object o) { return o == null ? "—" : o.toString(); }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static void require(String v, String f) { if (v == null || v.isBlank()) throw invalid(f, "obligatoire"); }
    private static ApiException invalid(String f, String m) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", f + " : " + m); }
}
