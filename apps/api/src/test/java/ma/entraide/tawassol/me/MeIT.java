package ma.entraide.tawassol.me;

import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeIT extends IntegrationTestBase {
    String pa, pb, ta, tb;

    @BeforeEach
    void fixture() throws Exception {
        String s = data.structure(data.delegation("ME1"), null, "MES");
        pa = data.personnel(s, "Mea"); pb = data.personnel(s, "Meb");
        data.user("ma", "agent", pa, false); data.user("mb", "agent", pb, false);
        ta = bearer("ma"); tb = bearer("mb");
    }

    @Test void confirmationDeLaFicheEstHorodatee() throws Exception {
        send(HttpMethod.POST, "/api/v1/me/fiche/confirmer", ta, null).andExpect(status().isOk()).andExpect(jsonPath("$.confirmeLe").exists());
        send(HttpMethod.GET, "/api/v1/me/tableau-de-bord", ta, null).andExpect(jsonPath("$.ficheConfirmeeLe").exists());
        send(HttpMethod.GET, "/api/v1/me/tableau-de-bord", tb, null).andExpect(status().isOk()).andExpect(jsonPath("$.ficheConfirmeeLe").doesNotExist());
    }

    @Test void signalerUneErreurCreeUneReclamationDeCorrection() throws Exception {
        String id = json.readTree(send(HttpMethod.POST, "/api/v1/me/fiche/signaler-erreur", ta, Map.of("champ", "echelon", "valeurProposee", "6", "description", "Mon échelon n'est pas à jour"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("reclamationId").asText();
        send(HttpMethod.GET, "/api/v1/reclamations/" + id, ta, null).andExpect(jsonPath("$.typeReclamation").value("correction_donnees")).andExpect(jsonPath("$.statut").value("nouvelle")).andExpect(jsonPath("$.champConcerne").value("echelon"));
        send(HttpMethod.GET, "/api/v1/reclamations/" + id, tb, null).andExpect(status().isNotFound());
        send(HttpMethod.GET, "/api/v1/reclamations", tb, null).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void reclamationGenerale() throws Exception {
        send(HttpMethod.POST, "/api/v1/reclamations", ta, Map.of("objet", "Bulletin de septembre", "description", "Il manque un rappel")).andExpect(status().isCreated()).andExpect(jsonPath("$.typeReclamation").value("generale"));
        send(HttpMethod.POST, "/api/v1/reclamations", ta, Map.of("objet", "", "description", "x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test void notificationsPersonnelles() throws Exception {
        send(HttpMethod.POST, "/api/v1/me/fiche/signaler-erreur", ta, Map.of("champ", "grade", "description", "x")).andExpect(status().isCreated());
        String nid = json.readTree(send(HttpMethod.GET, "/api/v1/notifications", ta, null).andExpect(jsonPath("$.nonLues").value(1)).andReturn().getResponse().getContentAsString()).get("items").get(0).get("id").asText();
        send(HttpMethod.GET, "/api/v1/notifications", tb, null).andExpect(jsonPath("$.items.length()").value(0));
        send(HttpMethod.POST, "/api/v1/notifications/" + nid + "/lu", tb, null).andExpect(status().isNotFound());
        send(HttpMethod.POST, "/api/v1/notifications/" + nid + "/lu", ta, null).andExpect(status().isNoContent());
        send(HttpMethod.GET, "/api/v1/notifications?nonLues=true", ta, null).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test void noteAnnuelle_publieeSeulement_etObservations() throws Exception {
        jdbc.update("INSERT INTO note_annuelle (id, personnel_id, annee, note, appreciation, statut) VALUES (?,?,2025,16.25,'Très bien','publiee')", UUID.randomUUID().toString(), pa);
        jdbc.update("INSERT INTO note_annuelle (id, personnel_id, annee, note, appreciation, statut) VALUES (?,?,2026,12.00,'Bien','soumise')", UUID.randomUUID().toString(), pa);
        send(HttpMethod.GET, "/api/v1/me/notes", ta, null).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].annee").value(2025)).andExpect(jsonPath("$[0].note").value(16.25));
        send(HttpMethod.PUT, "/api/v1/me/notes/2025/observations", ta, Map.of("observations", "Je souhaite un réexamen")).andExpect(status().isOk());
        send(HttpMethod.PUT, "/api/v1/me/notes/2026/observations", ta, Map.of("observations", "x")).andExpect(status().isNotFound());
        send(HttpMethod.PUT, "/api/v1/me/notes/2025/observations", tb, Map.of("observations", "x")).andExpect(status().isNotFound());
        assertEquals("Je souhaite un réexamen", jdbc.queryForObject("SELECT observations_agent FROM note_annuelle WHERE personnel_id = ? AND annee = 2025", String.class, pa));
    }

    @Test void tableauDeBord() throws Exception {
        send(HttpMethod.GET, "/api/v1/me/tableau-de-bord", ta, null).andExpect(status().isOk()).andExpect(jsonPath("$.soldeConges.disponible").exists()).andExpect(jsonPath("$.demandesEnCours").value(0));
    }
}
