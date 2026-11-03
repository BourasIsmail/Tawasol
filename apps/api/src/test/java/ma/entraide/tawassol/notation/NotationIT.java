package ma.entraide.tawassol.notation;

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

class NotationIT extends IntegrationTestBase {
    int annee = LocalDate.now().getYear();
    String pA, pB, pResp1, pRespDeleg, pResp2, pC, tA, tResp1, tRespDeleg, tResp2;

    @BeforeEach
    void fixture() throws Exception {
        String d1 = data.delegation("N1"), d2 = data.delegation("N2");
        String s1 = data.structure(d1, null, "NS1"), s2 = data.structure(d1, s1, "NS2"), s3 = data.structure(d1, null, "NS3"), s4 = data.structure(d2, null, "NS4");
        pResp1 = data.personnel(s1, "Nresun"); pRespDeleg = data.personnel(s3, "Nrespdeleg"); pResp2 = data.personnel(s4, "Nresdeux");
        pA = data.personnel(s2, "Nagenta"); pB = data.personnel(s1, "Nagentb"); pC = data.personnel(s4, "Nagentc");
        data.user("nresp1", "responsable", pResp1, false); data.user("nrespdeleg", "responsable", pRespDeleg, false); data.user("nresp2", "responsable", pResp2, false); data.user("na", "agent", pA, false);
        LocalDate debut = LocalDate.of(2020, 1, 1);
        data.perimeter(pResp1, "structure", s1, debut, null, true); data.perimeter(pRespDeleg, "delegation", d1, debut, null, true); data.perimeter(pResp2, "structure", s4, debut, null, true);
        jdbc.update("INSERT INTO campagne_notation (annee, date_ouverture, date_cloture, bareme_max) VALUES (?,?,?,20)", annee, LocalDate.now().minusDays(5), LocalDate.now().plusDays(30));
        tA = bearer("na"); tResp1 = bearer("nresp1"); tRespDeleg = bearer("nrespdeleg"); tResp2 = bearer("nresp2");
    }

    String path(String p) { return "/api/v1/notation/" + annee + "/agents" + p; }

    @Test void chaqueResponsableNeNoteQueSesAgents_jamaisLuiMeme() throws Exception {
        send(HttpMethod.GET, path(""), tResp1, null).andExpect(status().isOk()).andExpect(jsonPath("$.agents[*].id", hasItem(pA))).andExpect(jsonPath("$.agents[*].id", hasItem(pB)))
                .andExpect(jsonPath("$.agents[*].id", not(hasItem(pResp1)))).andExpect(jsonPath("$.agents[*].id", not(hasItem(pC))));
        send(HttpMethod.GET, path(""), tRespDeleg, null).andExpect(jsonPath("$.agents[*].id", hasItem(pResp1))).andExpect(jsonPath("$.agents[*].id", not(hasItem(pA))));
        send(HttpMethod.GET, path(""), tA, null).andExpect(jsonPath("$.total").value(0));
    }

    @Test void brouillonPuisSoumission_etVerrouillage() throws Exception {
        send(HttpMethod.PUT, path("/" + pA), tResp1, Map.of("note", 15.5, "appreciation", "Bien")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("brouillon"));
        send(HttpMethod.PUT, path("/" + pA), tResp1, Map.of("note", 16, "appreciation", "Très bien")).andExpect(status().isOk());
        send(HttpMethod.GET, path(""), tResp1, null).andExpect(jsonPath("$.agents[?(@.id=='" + pA + "')].statut").value("brouillon")).andExpect(jsonPath("$.soumises").value(0));
        send(HttpMethod.POST, path("/" + pA + "/soumettre"), tResp1, null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("soumise"));
        send(HttpMethod.POST, path("/" + pA + "/soumettre"), tResp1, null).andExpect(status().isOk());
        send(HttpMethod.PUT, path("/" + pA), tResp1, Map.of("note", 18)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTE_LOCKED"));
        assertEquals("soumise", jdbc.queryForObject("SELECT statut FROM note_annuelle WHERE personnel_id = ?", String.class, pA));
        jdbc.update("UPDATE note_annuelle SET statut = 'validee'");
        send(HttpMethod.PUT, path("/" + pA), tResp1, Map.of("note", 18)).andExpect(status().isConflict());
    }

    @Test void barèmeEtCampagne() throws Exception {
        send(HttpMethod.PUT, path("/" + pA), tResp1, Map.of("note", 21)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("NOTE_OUT_OF_RANGE"));
        send(HttpMethod.PUT, path("/" + pA), tResp1, Map.of("note", -1)).andExpect(status().isBadRequest());
        send(HttpMethod.POST, path("/" + pA + "/soumettre"), tResp1, null).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("NOTE_MISSING"));
        send(HttpMethod.PUT, "/api/v1/notation/" + (annee - 1) + "/agents/" + pA, tResp1, Map.of("note", 10)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAMPAIGN_CLOSED"));
        send(HttpMethod.GET, "/api/v1/notation/campagne", tResp1, null).andExpect(jsonPath("$.annee").value(annee));
    }

    @Test void saisieRefuseeSiPasCompetent() throws Exception {
        send(HttpMethod.PUT, path("/" + pA), tResp2, Map.of("note", 10)).andExpect(status().isNotFound());
        send(HttpMethod.PUT, path("/" + pA), tRespDeleg, Map.of("note", 10)).andExpect(status().isNotFound());
        send(HttpMethod.PUT, path("/" + pResp1), tResp1, Map.of("note", 10)).andExpect(status().isNotFound());
        send(HttpMethod.PUT, path("/" + pResp1), tRespDeleg, Map.of("note", 10)).andExpect(status().isOk());
        send(HttpMethod.PUT, path("/" + pA), tA, Map.of("note", 20)).andExpect(status().isNotFound());
    }

    @Test void laNoteNEstPasVisibleDeLAgentTantQuElleNEstPasPubliee() throws Exception {
        send(HttpMethod.PUT, path("/" + pA), tResp1, Map.of("note", 15)).andExpect(status().isOk());
        send(HttpMethod.POST, path("/" + pA + "/soumettre"), tResp1, null).andExpect(status().isOk());
        send(HttpMethod.GET, "/api/v1/me/notes", tA, null).andExpect(jsonPath("$.length()").value(0));
    }
}
