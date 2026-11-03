package ma.entraide.tawassol.piecejointe;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PieceJointeIT extends IntegrationTestBase {
    @Autowired SignedLinkService links;
    String ta, tb, maladie;

    static byte[] pdf(int extra) {
        byte[] b = new byte[20 + extra];
        System.arraycopy("%PDF-1.4\n%test\n".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 15);
        return b;
    }

    @BeforeEach
    void fixture() throws Exception {
        String s = data.structure(data.delegation("PJ1"), null, "PJS");
        data.user("pa", "agent", data.personnel(s, "Pja"), false); data.user("pb", "agent", data.personnel(s, "Pjb"), false);
        ta = bearer("pa"); tb = bearer("pb");
        maladie = idOf(send(HttpMethod.POST, "/api/v1/demandes", ta, Map.of("typeCode", "CONGE_MALADIE", "dateDebut", "2026-11-16", "dateFin", "2026-11-18")).andExpect(status().isCreated()));
    }

    org.springframework.test.web.servlet.ResultActions upload(String token, String demande, String name, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/v1/demandes/" + demande + "/pieces-jointes").file(new MockMultipartFile("fichier", name, "application/pdf", content)).header("Authorization", token));
    }

    @Test void certificatObligatoirePourLeCongeMaladie_puisAccepte() throws Exception {
        send(HttpMethod.POST, "/api/v1/demandes/" + maladie + "/soumettre", ta, null).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ATTACHMENT_REQUIRED"));
        upload(ta, maladie, "certificat.pdf", pdf(2000)).andExpect(status().isCreated()).andExpect(jsonPath("$.typeMime").value("application/pdf"));
        send(HttpMethod.POST, "/api/v1/demandes/" + maladie + "/soumettre", ta, null).andExpect(status().isOk()).andExpect(jsonPath("$.piecesJointes.length()").value(1));
    }

    @Test void leVraiTypeFaitFoi_pasLExtension() throws Exception {
        upload(ta, maladie, "certificat.pdf", "ceci est un script, pas un PDF".getBytes(StandardCharsets.UTF_8)).andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        upload(ta, maladie, "photo.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0}).andExpect(status().isCreated()).andExpect(jsonPath("$.typeMime").value("image/png"));
    }

    @Test void tailleMaximale() throws Exception {
        upload(ta, maladie, "gros.pdf", pdf(6 * 1024 * 1024)).andExpect(status().isPayloadTooLarge());
    }

    @Test void nomDeFichierAssaini() throws Exception {
        upload(ta, maladie, "../../etc/passwd<script>.pdf", pdf(100)).andExpect(status().isCreated()).andExpect(jsonPath("$.nomFichier").value("passwd_script_.pdf"));
    }

    @Test void ownerSeulementEtSeulementSurBrouillon() throws Exception {
        upload(tb, maladie, "x.pdf", pdf(100)).andExpect(status().isNotFound());
        upload(ta, maladie, "x.pdf", pdf(100)).andExpect(status().isCreated());
        send(HttpMethod.POST, "/api/v1/demandes/" + maladie + "/soumettre", ta, null).andExpect(status().isOk());
        upload(ta, maladie, "y.pdf", pdf(100)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTACHMENTS_LOCKED"));
    }

    @Test void nombreMaximalDePiecesJointes() throws Exception {
        for (int i = 0; i < 5; i++) upload(ta, maladie, "p" + i + ".pdf", pdf(50)).andExpect(status().isCreated());
        upload(ta, maladie, "p6.pdf", pdf(50)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TOO_MANY_ATTACHMENTS"));
    }

    @Test void lienSigne_valide_falsifie_expire_etAutreAgent() throws Exception {
        String pid = json.readTree(upload(ta, maladie, "c.pdf", pdf(500)).andReturn().getResponse().getContentAsString()).get("id").asText();
        String url = json.readTree(send(HttpMethod.GET, "/api/v1/demandes/" + maladie + "/pieces-jointes/" + pid + "/lien", ta, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("url").asText();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        mvc.perform(get(url + "x")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/fichiers/" + links.sign(pid, Duration.ofSeconds(-5)))).andExpect(status().isForbidden());
        send(HttpMethod.GET, "/api/v1/demandes/" + maladie + "/pieces-jointes/" + pid + "/lien", tb, null).andExpect(status().isNotFound());
    }

    @Test void suppressionSurBrouillon() throws Exception {
        String pid = json.readTree(upload(ta, maladie, "c.pdf", pdf(100)).andReturn().getResponse().getContentAsString()).get("id").asText();
        send(HttpMethod.DELETE, "/api/v1/demandes/" + maladie + "/pieces-jointes/" + pid, tb, null).andExpect(status().isNotFound());
        send(HttpMethod.DELETE, "/api/v1/demandes/" + maladie + "/pieces-jointes/" + pid, ta, null).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM piece_jointe", Integer.class));
    }
}
