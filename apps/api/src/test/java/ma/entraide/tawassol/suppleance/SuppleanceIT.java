package ma.entraide.tawassol.suppleance;

import java.time.LocalDate;
import java.util.Map;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SuppleanceIT extends IntegrationTestBase {
    String pResp, pAgent, pOutside, tResp, tAgent;
    LocalDate today = LocalDate.now();

    @BeforeEach
    void fixture() throws Exception {
        String d = data.delegation("SU1");
        String s1 = data.structure(d, null, "SUS1"), s2 = data.structure(d, null, "SUS2");
        pResp = data.personnel(s1, "Suresp"); pAgent = data.personnel(s1, "Suagent"); pOutside = data.personnel(s2, "Suoutside");
        data.user("suresp", "responsable", pResp, false); data.user("suagent", "agent", pAgent, false);
        data.perimeter(pResp, "structure", s1, LocalDate.of(2020, 1, 1), null, true);
        tResp = bearer("suresp"); tAgent = bearer("suagent");
    }

    @Test void designerUnSuppleantDeSonPerimetre() throws Exception {
        send(HttpMethod.POST, "/api/v1/suppleances", tResp, Map.of("suppleantId", pAgent, "dateDebut", today.toString(), "dateFin", today.plusDays(7).toString())).andExpect(status().isCreated());
        send(HttpMethod.GET, "/api/v1/suppleances", tResp, null).andExpect(jsonPath("$.designes.length()").value(1));
        send(HttpMethod.GET, "/api/v1/suppleances", tAgent, null).andExpect(jsonPath("$.aSupplier.length()").value(1));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'SUPPLEANCE'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'SUPPLEANCE_CREEE'", Integer.class));
        send(HttpMethod.GET, "/api/v1/me", tAgent, null).andExpect(jsonPath("$.suppleant").value(true)).andExpect(jsonPath("$.responsable").value(false));
    }

    @Test void refusDansLesCasInvalides() throws Exception {
        String ok = today.toString(), fin = today.plusDays(3).toString();
        send(HttpMethod.POST, "/api/v1/suppleances", tResp, Map.of("suppleantId", pOutside, "dateDebut", ok, "dateFin", fin)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_SUPPLEANT"));
        send(HttpMethod.POST, "/api/v1/suppleances", tResp, Map.of("suppleantId", pResp, "dateDebut", ok, "dateFin", fin)).andExpect(status().isUnprocessableEntity());
        send(HttpMethod.POST, "/api/v1/suppleances", tResp, Map.of("suppleantId", pAgent, "dateDebut", fin, "dateFin", ok)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_DATES"));
        send(HttpMethod.POST, "/api/v1/suppleances", tAgent, Map.of("suppleantId", pResp, "dateDebut", ok, "dateFin", fin)).andExpect(status().isForbidden());
    }

    @Test void candidatsEtFinDeSuppleance() throws Exception {
        send(HttpMethod.GET, "/api/v1/suppleances/candidats", tResp, null).andExpect(jsonPath("$[*].id", hasItem(pAgent))).andExpect(jsonPath("$[*].id", not(hasItem(pOutside)))).andExpect(jsonPath("$[*].id", not(hasItem(pResp))));
        send(HttpMethod.GET, "/api/v1/suppleances/candidats", tAgent, null).andExpect(jsonPath("$.length()").value(0));
        String id = json.readTree(send(HttpMethod.POST, "/api/v1/suppleances", tResp, Map.of("suppleantId", pAgent, "dateDebut", today.toString(), "dateFin", today.plusDays(7).toString())).andReturn().getResponse().getContentAsString()).get("id").asText();
        send(HttpMethod.DELETE, "/api/v1/suppleances/" + id, tAgent, null).andExpect(status().isNotFound());
        send(HttpMethod.DELETE, "/api/v1/suppleances/" + id, tResp, null).andExpect(status().isNoContent());
        send(HttpMethod.GET, "/api/v1/me", tAgent, null).andExpect(jsonPath("$.suppleant").value(false));
    }
}
