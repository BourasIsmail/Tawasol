package ma.entraide.tawassol.auth;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Entry;
import java.util.Map;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Connexion par l'annuaire (serveur LDAP en mémoire, sans TLS) : mot de passe annuaire, rapprochement par identifiant ou matricule, compte local de secours. */
class LdapAuthIT extends IntegrationTestBase {
    static final String BASE = "OU=USERS,DC=ad,DC=test", SVC = "CN=svc,DC=ad,DC=test";
    static InMemoryDirectoryServer ldap;

    static {
        try {
            InMemoryDirectoryServerConfig c = new InMemoryDirectoryServerConfig("DC=ad,DC=test");
            c.addAdditionalBindCredentials(SVC, "svc-secret");
            c.setSchema(null);
            c.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("l", 0));
            ldap = new InMemoryDirectoryServer(c);
            ldap.add(new Entry("dn: DC=ad,DC=test", "objectClass: top", "objectClass: domain", "dc: ad"));
            ldap.add(new Entry("dn: OU=USERS,DC=ad,DC=test", "objectClass: top", "objectClass: organizationalUnit", "ou: USERS"));
            ldap.add(new Entry("dn: CN=Alice,OU=USERS,DC=ad,DC=test", "objectClass: top", "objectClass: user", "cn: Alice", "sAMAccountName: alice",
                    "employeeID: ldap001", "mail: alice@ad.test", "userPassword: Secret-AD-1"));
            ldap.startListening();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("tawassol.ldap.enabled", () -> "true");
        r.add("tawassol.ldap.hosts", () -> "localhost");
        r.add("tawassol.ldap.port", () -> ldap.getListenPort());
        r.add("tawassol.ldap.tls", () -> "false");
        r.add("tawassol.ldap.base-dn", () -> BASE);
        r.add("tawassol.ldap.bind-dn", () -> SVC);
        r.add("tawassol.ldap.bind-password", () -> "svc-secret");
    }

    @AfterAll static void stop() { ldap.shutDown(true); }

    @BeforeEach void fixture() {
        String s = data.structure(data.delegation("LD"), null, "LDS");
        data.user("ldap001", "agent", data.personnel(s, "Alice"), false);
        data.user("secours", "admin", null, false);
        jdbc.update("UPDATE utilisateur SET auth_locale = 1 WHERE login = 'secours'");
    }

    int login(String login, String pwd) throws Exception {
        return send(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("login", login, "password", pwd)).andReturn().getResponse().getStatus();
    }

    @Test void motDePasseAnnuaireOuvreLeCompteParMatriculeOuParIdentifiantAD() throws Exception {
        assertEquals(200, login("ldap001", "Secret-AD-1"), "matricule saisi, filtre sur employeeID");
        assertEquals(200, login("alice", "Secret-AD-1"), "identifiant AD saisi, rapprochement par l'attribut matricule");
        assertEquals(0, jdbc.queryForObject("SELECT doit_changer_mdp FROM utilisateur WHERE login = 'ldap001'", Integer.class), "pas de changement imposé : l'annuaire gère le mot de passe");
    }

    @Test void refusSiMauvaisMotDePasseOuHorsAnnuaire() throws Exception {
        assertEquals(401, login("ldap001", "faux"));
        assertEquals(401, login("ldap001", "password"), "le mot de passe local initial n'ouvre plus rien");
        assertEquals(401, login("inconnu", "Secret-AD-1"));
        assertTrue(login("alice", "") >= 400, "mot de passe vide refusé (pas de liaison anonyme)");
    }

    @Test void injectionDansLIdentifiantNeContourneRien() throws Exception {
        assertEquals(401, login("*", "Secret-AD-1"));
        assertEquals(401, login("alice)(objectClass=*", "Secret-AD-1"));
    }

    @Test void leCompteLocalDeSecoursGardeSonMotDePasse() throws Exception {
        assertNotEquals(401, login("secours", ma.entraide.tawassol.support.TestData.PASSWORD));
    }

    @Test void changementDeMotDePasseRefuseEtConfigPublique() throws Exception {
        String t = "Bearer " + json.readTree(send(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("login", "ldap001", "password", "Secret-AD-1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        send(HttpMethod.POST, "/api/v1/auth/change-password", t, Map.of("currentPassword", "x", "newPassword", "Nouveau-Mdp-12345!")).andExpect(status().isBadRequest());
        send(HttpMethod.GET, "/api/v1/auth/config", null, null).andExpect(status().isOk());
    }
}
