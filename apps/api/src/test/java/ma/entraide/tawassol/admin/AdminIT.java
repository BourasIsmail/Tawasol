package ma.entraide.tawassol.admin;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminIT extends IntegrationTestBase {
    String adminId, agentId, tAdmin, tAgent, rhId, tRh;

    @BeforeEach void fixture() throws Exception {
        String s = data.structure(data.delegation("AD"), null, "ADS");
        String pa = data.personnel(s, "Agent"), pr = data.personnel(s, "Rh");
        adminId = data.user("adm", "admin", null, false); agentId = data.user("ag", "agent", pa, false); rhId = data.user("rh", "rh", pr, false);
        tAdmin = bearer("adm"); tAgent = bearer("ag"); tRh = bearer("rh");
    }

    JsonNode get(String path, String token, int statut) throws Exception {
        return json.readTree(send(HttpMethod.GET, path, token, null).andExpect(status().is(statut)).andReturn().getResponse().getContentAsString());
    }

    @Test void reserveALAdministrateur() throws Exception {
        for (String p : new String[]{"/api/v1/admin/utilisateurs", "/api/v1/admin/roles", "/api/v1/admin/audit"}) {
            send(HttpMethod.GET, p, tAgent, null).andExpect(status().isForbidden());
            send(HttpMethod.GET, p, tRh, null).andExpect(status().isForbidden());
            send(HttpMethod.GET, p, tAdmin, null).andExpect(status().isOk());
        }
    }

    @Test void changerLeRoleEtLeProfil() throws Exception {
        JsonNode roles = get("/api/v1/admin/roles", tAdmin, 200);
        String cellule = null;
        for (JsonNode r : roles) if ("cellule_paie".equals(r.get("code").asText())) { cellule = r.get("id").asText(); assertTrue(r.get("permissions").toString().contains("mission.valider_paiement")); }
        assertNotNull(cellule);
        send(HttpMethod.PUT, "/api/v1/admin/utilisateurs/" + agentId, tAdmin, Map.of("roleId", cellule)).andExpect(status().isOk());
        assertEquals("rh", jdbc.queryForObject("SELECT role FROM utilisateur WHERE id = ?", String.class, agentId), "le profil suit le rôle");
        send(HttpMethod.PUT, "/api/v1/admin/utilisateurs/" + agentId, tAdmin, Map.of("roleId", "inconnu")).andExpect(status().isUnprocessableEntity());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'ADMIN_COMPTE_MODIFIE'", Integer.class) >= 1);
    }

    @Test void pasDAutoModificationEtDesactivationCoupeLesSessions() throws Exception {
        send(HttpMethod.PUT, "/api/v1/admin/utilisateurs/" + adminId, tAdmin, Map.of("estActif", false)).andExpect(status().isForbidden());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE utilisateur_id = ? AND revoque = 0", Integer.class, agentId) > 0);
        send(HttpMethod.PUT, "/api/v1/admin/utilisateurs/" + agentId, tAdmin, Map.of("estActif", false)).andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE utilisateur_id = ? AND revoque = 0", Integer.class, agentId));
        send(HttpMethod.GET, "/api/v1/me", tAgent, null).andExpect(status().isUnauthorized());
    }

    @Test void reactiverUnCompteHeriteExigeUnNouveauMotDePasse() throws Exception {
        jdbc.update("UPDATE utilisateur SET est_compte_legacy = 1, est_actif = 0, doit_changer_mdp = 0 WHERE id = ?", rhId);
        JsonNode l = get("/api/v1/admin/utilisateurs?legacy=true", tAdmin, 200);
        assertEquals(1, l.get("total").asInt());
        send(HttpMethod.PUT, "/api/v1/admin/utilisateurs/" + rhId, tAdmin, Map.of("estActif", true)).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT doit_changer_mdp FROM utilisateur WHERE id = ?", Integer.class, rhId));
    }

    @Test void lAuditNeMontrePasLeContenuAvantApres() throws Exception {
        send(HttpMethod.PUT, "/api/v1/admin/utilisateurs/" + agentId, tAdmin, Map.of("estActif", true)).andExpect(status().isOk());
        String body = get("/api/v1/admin/audit?action=ADMIN", tAdmin, 200).toString();
        assertTrue(body.contains("ADMIN_COMPTE_MODIFIE"));
        assertFalse(body.contains("avant") || body.contains("apres"));
    }
}
