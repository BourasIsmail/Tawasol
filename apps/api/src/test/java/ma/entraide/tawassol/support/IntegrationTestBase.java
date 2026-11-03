package ma.entraide.tawassol.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base des tests d'intégration. Si TEST_DB_URL est défini on l'utilise (CI ou poste sans Docker),
 * sinon un conteneur MySQL 8.4 est démarré par Testcontainers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestMailConfig.class)
public abstract class IntegrationTestBase {
    private static MySQLContainer<?> container;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        String url = System.getenv("TEST_DB_URL");
        if (url != null) {
            r.add("spring.datasource.url", () -> url);
            r.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "tw"));
            r.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "tw"));
            return;
        }
        synchronized (IntegrationTestBase.class) {
            if (container == null) {
                container = new MySQLContainer<>("mysql:8.4").withDatabaseName("tawassol_test").withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
                container.start();
            }
        }
        r.add("spring.datasource.url", container::getJdbcUrl);
        r.add("spring.datasource.username", container::getUsername);
        r.add("spring.datasource.password", container::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    @Autowired protected TestData data;

    @BeforeEach
    void cleanDatabase() { data.reset(); }

    protected JsonNode loginOk(String login, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    protected String bearer(String login) throws Exception {
        return "Bearer " + loginOk(login, TestData.PASSWORD).get("accessToken").asText();
    }

    protected org.springframework.test.web.servlet.ResultActions send(org.springframework.http.HttpMethod m, String path, String token, Object body) throws Exception {
        var rb = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(m, path).contentType("application/json");
        if (token != null) rb.header("Authorization", token);
        if (body != null) rb.content(body instanceof String ? (String) body : json.writeValueAsString(body));
        return mvc.perform(rb);
    }

    protected String idOf(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asText();
    }
}
