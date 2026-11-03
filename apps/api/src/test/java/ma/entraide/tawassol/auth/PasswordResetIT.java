package ma.entraide.tawassol.auth;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ma.entraide.tawassol.support.IntegrationTestBase;
import ma.entraide.tawassol.support.TestData;
import ma.entraide.tawassol.support.TestMailConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetIT extends IntegrationTestBase {
    @Autowired TestMailConfig.CapturingMailService mail;

    @BeforeEach
    void fixture() {
        mail.sent.clear();
        String s = data.structure(data.delegation("PR1"), null, "PRS");
        data.user("prl", "agent", data.personnel(s, "Resetuser"), false);
    }

    String token() {
        Matcher m = Pattern.compile("/fr/reset\\?token=([A-Za-z0-9_-]+)").matcher(mail.sent.get(0).body());
        assertTrue(m.find());
        return m.group(1);
    }

    void forgot(String login) throws Exception { send(HttpMethod.POST, "/api/v1/auth/forgot-password", null, Map.of("login", login)).andExpect(status().isNoContent()); }

    @Test void laReponseEstLaMemePourUnCompteInconnu_etRienNEstEnvoye() throws Exception {
        forgot("inconnu");
        assertEquals(0, mail.sent.size());
        forgot("prl");
        assertEquals(1, mail.sent.size());
        assertEquals("resetuser@example.ma", mail.sent.get(0).to());
        assertTrue(mail.sent.get(0).body().contains("/ar/reset?token="), "message bilingue");
    }

    @Test void reinitialisationCompleteEtJetonAUsageUnique() throws Exception {
        forgot("prl");
        String t = token();
        loginOk("prl", TestData.PASSWORD);
        send(HttpMethod.POST, "/api/v1/auth/reset-password", null, Map.of("token", t, "newPassword", "Nouveau-Mdp-2026!")).andExpect(status().isNoContent());
        send(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("login", "prl", "password", TestData.PASSWORD)).andExpect(status().isUnauthorized());
        send(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("login", "prl", "password", "Nouveau-Mdp-2026!")).andExpect(status().isOk());
        send(HttpMethod.POST, "/api/v1/auth/reset-password", null, Map.of("token", t, "newPassword", "Autre-Mdp-2026!!")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'PASSWORD_RESET_DONE'", Integer.class));
    }

    @Test void jetonExpire_inconnu_ouMotDePasseFaible() throws Exception {
        forgot("prl");
        String t = token();
        send(HttpMethod.POST, "/api/v1/auth/reset-password", null, Map.of("token", t, "newPassword", "faible")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_POLICY"));
        send(HttpMethod.POST, "/api/v1/auth/reset-password", null, Map.of("token", "n-importe-quoi", "newPassword", "Nouveau-Mdp-2026!")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE reset_mdp_token SET expire_le = DATE_SUB(NOW(), INTERVAL 1 HOUR)");
        send(HttpMethod.POST, "/api/v1/auth/reset-password", null, Map.of("token", t, "newPassword", "Nouveau-Mdp-2026!")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test void laReinitialisationRevoqueLesSessionsEtDeverrouilleLeCompte() throws Exception {
        String refresh = loginOk("prl", TestData.PASSWORD).get("refreshToken").asText();
        jdbc.update("UPDATE utilisateur SET verrouille_jusqua = DATE_ADD(NOW(), INTERVAL 1 HOUR), echecs_connexion = 3 WHERE login = 'prl'");
        forgot("prl");
        send(HttpMethod.POST, "/api/v1/auth/reset-password", null, Map.of("token", token(), "newPassword", "Nouveau-Mdp-2026!")).andExpect(status().isNoContent());
        send(HttpMethod.POST, "/api/v1/auth/refresh", null, Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
        send(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("login", "prl", "password", "Nouveau-Mdp-2026!")).andExpect(status().isOk());
    }
}
