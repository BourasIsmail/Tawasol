package ma.entraide.tawassol.auth;

import com.fasterxml.jackson.databind.JsonNode;
import ma.entraide.tawassol.support.IntegrationTestBase;
import ma.entraide.tawassol.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends IntegrationTestBase {
    String agent;

    @BeforeEach
    void fixture() {
        String d = data.delegation("A1");
        String s = data.structure(d, null, "A1S");
        agent = data.personnel(s, "Agentauth");
        data.user("agentauth", "agent", agent, false);
    }

    String body(String login, String pw) { return "{\"login\":\"" + login + "\",\"password\":\"" + pw + "\"}"; }

    @Test void connexionReussieRenvoieJetonsEtRole() throws Exception {
        JsonNode t = loginOk("agentauth", TestData.PASSWORD);
        assertEquals("agent", t.get("role").asText());
        assertEquals(900, t.get("expiresInSeconds").asInt());
        assertFalse(t.get("mustChangePassword").asBoolean());
    }

    @Test void mauvaisMotDePasseEtLoginInconnuDonnentLaMemeReponse() throws Exception {
        String a = mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body("agentauth", "Mauvais-mdp-1!")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("BAD_CREDENTIALS")).andReturn().getResponse().getContentAsString();
        String b = mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body("inconnu", "Mauvais-mdp-1!")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(a).get("message"), json.readTree(b).get("message"));
    }

    @Test void verrouillageApresCinqEchecs_memeAvecLeBonMotDePasse() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body("agentauth", "Mauvais-mdp-1!"))).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body("agentauth", TestData.PASSWORD)))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("ACCOUNT_TEMPORARILY_LOCKED"));
    }

    @Test void refreshEstRotatifEtLaReutilisationRevoqueLaFamille() throws Exception {
        String r1 = loginOk("agentauth", TestData.PASSWORD).get("refreshToken").asText();
        String r2 = json.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"" + r1 + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("refreshToken").asText();
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"" + r1 + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"" + r2 + "\"}")).andExpect(status().isUnauthorized());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'REFRESH_REUSE_DETECTED'", Integer.class) >= 1);
    }

    @Test void deconnexionDeTousLesAppareils() throws Exception {
        JsonNode t = loginOk("agentauth", TestData.PASSWORD);
        mvc.perform(post("/api/v1/auth/logout-all").header("Authorization", "Bearer " + t.get("accessToken").asText())).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"" + t.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void premierChangementDeMotDePasse() throws Exception {
        data.user("nouveau", "agent", null, true);
        JsonNode t = loginOk("nouveau", TestData.PASSWORD);
        assertTrue(t.get("mustChangePassword").asBoolean());
        String auth = "Bearer " + t.get("accessToken").asText();
        mvc.perform(get("/api/v1/structures/mine").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", auth).contentType("application/json")
                .content("{\"currentPassword\":\"" + TestData.PASSWORD + "\",\"newPassword\":\"faible\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_POLICY"));
        String ok = mvc.perform(post("/api/v1/auth/change-password").header("Authorization", auth).contentType("application/json")
                .content("{\"currentPassword\":\"" + TestData.PASSWORD + "\",\"newPassword\":\"Nouveau-Mdp-2026!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false)).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + json.readTree(ok).get("accessToken").asText())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body("nouveau", TestData.PASSWORD))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body("nouveau", "Nouveau-Mdp-2026!"))).andExpect(status().isOk());
    }

    @Test void lesEvenementsDeConnexionSontJournalises_sansMotDePasse() throws Exception {
        loginOk("agentauth", TestData.PASSWORD);
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body("agentauth", "Mauvais-mdp-1!")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'LOGIN_OK'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'LOGIN_FAIL'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE CONCAT_WS('', avant, apres) LIKE '%Mauvais%'", Integer.class));
    }
}
