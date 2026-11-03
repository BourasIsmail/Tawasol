package ma.entraide.tawassol.publication;

import java.time.LocalDateTime;
import java.util.UUID;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicationIT extends IntegrationTestBase {
    String d1, d2, parent, child, other, tChild, tOther;

    void annonce(String titre, String cibleType, String cibleId, LocalDateTime expire, boolean actif) {
        jdbc.update("INSERT INTO annonce (id, titre, message, est_active, publie_le, expire_le, cible_type, cible_id) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), titre, "msg", actif, LocalDateTime.now().minusDays(1), expire, cibleType, cibleId);
    }

    String doc(String titre, String cibleType, String cibleId, boolean accuse) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO document (id, type_doc_id, titre, est_publie, publie_le, cible_type, cible_id, accuse_lecture) VALUES (?, (SELECT id FROM type_document WHERE libelle = 'Note de service'), ?, 1, ?, ?, ?, ?)",
                id, titre, LocalDateTime.now().minusDays(1), cibleType, cibleId, accuse);
        return id;
    }

    @BeforeEach
    void fixture() throws Exception {
        d1 = data.delegation("PB1"); d2 = data.delegation("PB2");
        parent = data.structure(d1, null, "PBP"); child = data.structure(d1, parent, "PBC"); other = data.structure(d2, null, "PBO");
        data.user("pc", "agent", data.personnel(child, "Pubc"), false); data.user("po", "agent", data.personnel(other, "Pubo"), false);
        tChild = bearer("pc"); tOther = bearer("po");
    }

    @Test void ciblageParStructureParenteDelegationEtTous() throws Exception {
        annonce("pour tous", "tous", null, null, true);
        annonce("pour le parent", "structure", parent, null, true);
        annonce("pour la delegation 1", "delegation", d1, null, true);
        annonce("pour la delegation 2", "delegation", d2, null, true);
        send(HttpMethod.GET, "/api/v1/annonces", tChild, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].titre", hasItem("pour tous"))).andExpect(jsonPath("$[*].titre", hasItem("pour le parent")))
                .andExpect(jsonPath("$[*].titre", hasItem("pour la delegation 1"))).andExpect(jsonPath("$[*].titre", not(hasItem("pour la delegation 2"))));
        send(HttpMethod.GET, "/api/v1/annonces", tOther, null)
                .andExpect(jsonPath("$[*].titre", not(hasItem("pour le parent")))).andExpect(jsonPath("$[*].titre", hasItem("pour la delegation 2")));
    }

    @Test void expireeEtDesactiveeInvisibles() throws Exception {
        annonce("expiree", "tous", null, LocalDateTime.now().minusHours(1), true);
        annonce("desactivee", "tous", null, null, false);
        annonce("valide", "tous", null, LocalDateTime.now().plusDays(3), true);
        send(HttpMethod.GET, "/api/v1/annonces", tChild, null).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].titre").value("valide"));
    }

    @Test void documentsFiltresParTypeEtAccuseDeLecture() throws Exception {
        String visible = doc("Note horaires", "tous", null, true);
        String cache = doc("Note delegation 2", "delegation", d2, true);
        send(HttpMethod.GET, "/api/v1/documents", tChild, null).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].lu").value(false));
        send(HttpMethod.GET, "/api/v1/documents?type=Circulaire", tChild, null).andExpect(jsonPath("$.length()").value(0));
        send(HttpMethod.POST, "/api/v1/documents/" + cache + "/lecture", tChild, null).andExpect(status().isNotFound());
        send(HttpMethod.POST, "/api/v1/documents/" + visible + "/lecture", tChild, null).andExpect(status().isNoContent());
        send(HttpMethod.POST, "/api/v1/documents/" + visible + "/lecture", tChild, null).andExpect(status().isNoContent());
        send(HttpMethod.GET, "/api/v1/documents", tChild, null).andExpect(jsonPath("$[0].lu").value(true));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM document_lecture", Integer.class));
    }
}
