package ma.entraide.tawassol.support;

import java.time.LocalDate;
import java.util.UUID;
import ma.entraide.tawassol.security.PasswordService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fabrique de données de test (SQL direct, indépendante du code testé). */
@Component
public class TestData {
    public static final String PASSWORD = "Valid-Password-1!";
    private final JdbcTemplate jdbc;
    private final PasswordService passwords;
    private int seq = 1000;

    public TestData(JdbcTemplate jdbc, PasswordService passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    public void reset() {
        for (String t : new String[]{"etape_validation", "document_officiel", "examen_grade", "ordre_mission_historique", "ordre_mission", "personnel_programme", "signataire_autorisation", "delegation_compteur", "piece_jointe", "reclamation", "salaire_ligne", "salaire", "prime", "credit",
                "note_annuelle", "campagne_notation", "reset_mdp_token", "document_lecture", "document", "annonce", "import_lot", "compteur_numero", "demande",
                "journal_audit", "import_modele", "refresh_token", "notification", "suppleance", "responsable_perimetre",
                "demande_historique", "historique_carriere", "solde_conge", "note_critere"}) jdbc.update("DELETE FROM " + t);
        jdbc.update("DELETE FROM utilisateur WHERE login <> 'admin'");
        jdbc.update("DELETE FROM personnel");
        jdbc.update("DELETE FROM grade WHERE code >= 500");
        jdbc.update("UPDATE structure SET parent_id = NULL");
        jdbc.update("DELETE FROM structure");
        jdbc.update("DELETE FROM delegation_province");
    }

    public String delegation(String code) {
        String id = UUID.randomUUID().toString();
        String coord = jdbc.queryForObject("SELECT id FROM coordination_region WHERE code_region = 'R04'", String.class);
        jdbc.update("INSERT INTO delegation_province (id, coordination_id, code_province, nom_province_ar, nom_province_fr, nom_deleg_ar, nom_deleg_fr) VALUES (?,?,?,?,?,?,?)",
                id, coord, code, "إقليم " + code, "Province " + code, "مندوبية " + code, "Délégation " + code);
        return id;
    }

    public String structure(String delegationId, String parentId, String code) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO structure (id, delegation_id, parent_id, type, code, nom_ar, nom_fr) VALUES (?,?,?,?,?,?,?)",
                id, delegationId, parentId, "centre", code, "مركز " + code, "Centre " + code);
        return id;
    }

    public String personnel(String structureId, String nom) {
        String id = UUID.randomUUID().toString();
        String matricule = String.valueOf(++seq);
        jdbc.update("INSERT INTO personnel (id, structure_id, matricule, nom_ar, prenom_ar, nom_fr, prenom_fr, date_recrutement, email) VALUES (?,?,?,?,?,?,?,?,?)",
                id, structureId, matricule, "الاسم", "اللقب", nom, "Prenom", LocalDate.of(2015, 9, 1), nom.toLowerCase() + "@example.ma");
        return id;
    }

    public String user(String login, String role, String personnelId, boolean mustChange) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO utilisateur (id, personnel_id, login, mot_de_passe, role, doit_changer_mdp) VALUES (?,?,?,?,?,?)",
                id, personnelId, login, passwords.hash(PASSWORD), role, mustChange);
        return id;
    }

    public void perimeter(String personnelId, String type, String targetId, LocalDate debut, LocalDate fin, boolean actif) {
        String col = switch (type) { case "structure" -> "structure_id"; case "delegation" -> "delegation_id"; default -> "coordination_id"; };
        jdbc.update("INSERT INTO responsable_perimetre (personnel_id, type_perimetre, " + col + ", date_debut, date_fin, est_actif) VALUES (?,?,?,?,?,?)",
                personnelId, type, targetId, debut, fin, actif);
    }

    public void suppleance(String responsableId, String suppleantId, LocalDate debut, LocalDate fin) {
        jdbc.update("INSERT INTO suppleance (responsable_id, suppleant_id, date_debut, date_fin) VALUES (?,?,?,?)", responsableId, suppleantId, debut, fin);
    }
}
