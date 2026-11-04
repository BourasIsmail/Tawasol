package ma.entraide.tawassol.admin;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Création de comptes : admin (tous profils) et RH (agent et responsable seulement). */
class ComptesIT extends IntegrationTestBase {
    String tAdmin, tRh, tAgent, structure;

    @BeforeEach void fixture() throws Exception {
        structure = data.structure(data.delegation("CP"), null, "CPS");
        String pr = data.personnel(structure, "RhCpt"), pa = data.personnel(structure, "AgCpt");
        data.user("adm", "admin", null, false); data.user("rh", "rh", pr, false); data.user("ag", "agent", pa, false);
        tAdmin = bearer("adm"); tRh = bearer("rh"); tAgent = bearer("ag");
    }

    String role(String code) { return jdbc.queryForObject("SELECT id FROM role WHERE code = ?", String.class, code); }

    JsonNode post(String path, String token, Object body, int statut) throws Exception {
        return json.readTree(send(HttpMethod.POST, path, token, body).andExpect(status().is(statut)).andReturn().getResponse().getContentAsString());
    }

    @Test void lAdminCreeUnCompteEtLAgentRecoitUnLien() throws Exception {
        String p = data.personnel(structure, "Nouveau");
        JsonNode c = post("/api/v1/admin/utilisateurs", tAdmin, Map.of("personnelId", p, "roleId", role("agent_standard")), 201);
        assertTrue(c.get("lienEnvoye").asBoolean());
        assertTrue(c.path("motDePasseInitial").isMissingNode() || c.get("motDePasseInitial").isNull(), "le mot de passe n'est pas montré quand un e-mail existe");
        assertEquals(1, jdbc.queryForObject("SELECT doit_changer_mdp FROM utilisateur WHERE id = ?", Integer.class, c.get("id").asText()));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'COMPTE_CREE'", Integer.class) >= 1);
    }

    @Test void sansEmailLeMotDePasseInitialEstRenduUneSeuleFois() throws Exception {
        String p = data.personnel(structure, "SansMail");
        jdbc.update("UPDATE personnel SET email = NULL WHERE id = ?", p);
        JsonNode c = post("/api/v1/admin/utilisateurs", tAdmin, Map.of("personnelId", p, "roleId", role("agent_standard")), 201);
        assertFalse(c.get("lienEnvoye").asBoolean());
        String mdp = c.get("motDePasseInitial").asText();
        assertTrue(ma.entraide.tawassol.security.PasswordPolicy.violations(mdp, 12).isEmpty());
        // premier accès : connexion avec ce mot de passe, changement imposé
        String login = c.get("login").asText();
        JsonNode l = post("/api/v1/auth/login", null, Map.of("login", login, "password", mdp), 200);
        assertTrue(l.toString().contains("true"), "changement de mot de passe obligatoire");
    }

    @Test void controles() throws Exception {
        String p = data.personnel(structure, "Doublon");
        post("/api/v1/admin/utilisateurs", tAdmin, Map.of("personnelId", p, "roleId", role("agent_standard")), 201);
        post("/api/v1/admin/utilisateurs", tAdmin, Map.of("personnelId", p, "roleId", role("agent_standard")), 409);            // un seul compte par agent
        String q = data.personnel(structure, "Autre");
        post("/api/v1/admin/utilisateurs", tAdmin, Map.of("personnelId", q, "login", "adm", "roleId", role("agent_standard")), 409); // identifiant pris
        post("/api/v1/admin/utilisateurs", tAdmin, Map.of("personnelId", q, "login", "a b", "roleId", role("agent_standard")), 422);
        post("/api/v1/admin/utilisateurs", tAdmin, Map.of("personnelId", q, "roleId", "inconnu"), 422);
        post("/api/v1/admin/utilisateurs", tAdmin, Map.of("roleId", role("agent_standard")), 422);                              // agent obligatoire
        post("/api/v1/admin/utilisateurs", tAgent, Map.of("personnelId", q, "roleId", role("agent_standard")), 403);
    }

    @Test void laRhNeCreePasDeCompteRhNiAdmin() throws Exception {
        String p = data.personnel(structure, "ParRh");
        post("/api/v1/rh/comptes", tRh, Map.of("personnelId", p, "roleId", role("rh_generaliste")), 403);
        post("/api/v1/rh/comptes", tRh, Map.of("personnelId", p, "roleId", role("responsable_standard")), 201);
        assertEquals("responsable", jdbc.queryForObject("SELECT role FROM utilisateur WHERE personnel_id = ?", String.class, p));
        post("/api/v1/rh/comptes", tAgent, Map.of("personnelId", p, "roleId", role("agent_standard")), 403);
        JsonNode roles = json.readTree(send(HttpMethod.GET, "/api/v1/rh/comptes/roles", tRh, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode r : roles) assertTrue(r.get("profil").asText().matches("agent|responsable"));
    }

    @Test void candidatsSansCompte() throws Exception {
        String p = data.personnel(structure, "Cherche");
        JsonNode l = json.readTree(send(HttpMethod.GET, "/api/v1/admin/utilisateurs/candidats?q=Cherche", tAdmin, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, l.size());
        assertEquals(p, l.get(0).get("id").asText());
        assertFalse(l.get(0).has("salaire"));
        post("/api/v1/rh/comptes", tRh, Map.of("personnelId", p, "roleId", role("agent_standard")), 201);
        assertEquals(0, json.readTree(send(HttpMethod.GET, "/api/v1/rh/comptes/candidats?q=Cherche", tRh, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).size());
    }
}
