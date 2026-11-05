package ma.entraide.tawassol.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Circuit par défaut : le responsable direct valide, puis la RH (demandes) ou la cellule des déplacements (missions). Les niveaux supérieurs ne sont pas sollicités. */
class ValidationDirecteIT extends IntegrationTestBase {
    String deleg, dest, parent, enfant, pAgent, r1, r2, r3, r4, rh;
    String tAgent, t1, t2, t3, t4, tRh, tCell;

    @BeforeEach void fixture() throws Exception {
        deleg = data.delegation("DV"); dest = data.delegation("DVD");
        parent = data.structure(deleg, null, "DVP"); enfant = data.structure(deleg, parent, "DVE");
        pAgent = data.personnel(enfant, "Agent"); r1 = data.personnel(parent, "Niv1"); r2 = data.personnel(parent, "Niv2"); r3 = data.personnel(parent, "Niv3"); r4 = data.personnel(parent, "Niv4"); rh = data.personnel(parent, "Rh");
        jdbc.update("UPDATE personnel SET echelle = 7 WHERE id = ?", pAgent);
        data.user("ag", "agent", pAgent, false); data.user("r1", "responsable", r1, false); data.user("r2", "responsable", r2, false); data.user("r3", "responsable", r3, false); data.user("r4", "responsable", r4, false); data.user("rh", "rh", rh, false);
        String cell = data.user("cell", "rh", data.personnel(parent, "Cell"), false);
        jdbc.update("UPDATE utilisateur SET role_id = (SELECT id FROM role WHERE code = 'cellule_deplacements') WHERE id = ?", cell);
        String coord = jdbc.queryForObject("SELECT coordination_id FROM delegation_province WHERE id = ?", String.class, deleg);
        LocalDate debut = LocalDate.of(2020, 1, 1);
        data.perimeter(r1, "structure", enfant, debut, null, true);       // responsable direct
        data.perimeter(r2, "structure", parent, debut, null, true);
        data.perimeter(r3, "delegation", deleg, debut, null, true);
        data.perimeter(r4, "coordination", coord, debut, null, true);
        tAgent = bearer("ag"); t1 = bearer("r1"); t2 = bearer("r2"); t3 = bearer("r3"); t4 = bearer("r4"); tRh = bearer("rh"); tCell = bearer("cell");
    }

    JsonNode post(String path, String token, Object body, int statut) throws Exception {
        var r = send(HttpMethod.POST, path, token, body).andExpect(status().is(statut));
        String s = r.andReturn().getResponse().getContentAsString();
        return s.isBlank() ? null : json.readTree(s);
    }

    String demande() throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("typeCode", "AUTRE"); b.put("objet", "Demande test"); b.put("description", "Description");
        String id = idOf(send(HttpMethod.POST, "/api/v1/demandes", tAgent, b).andExpect(status().isCreated()));
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/soumettre", tAgent, null).andExpect(status().isOk());
        return id;
    }

    String statut(String id) { return jdbc.queryForObject("SELECT statut FROM demande WHERE id = ?", String.class, id); }

    @Test void laDemandeVaDuResponsableDirectALaRh() throws Exception {
        String id = demande();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM etape_validation WHERE objet_type = 'demande' AND objet_id = ?", Integer.class, id), "une seule étape : le responsable direct");
        assertEquals(r1, jdbc.queryForObject("SELECT responsable_personnel_id FROM etape_validation WHERE objet_id = ?", String.class, id));
        // les niveaux supérieurs ne voient ni ne décident rien
        for (String t : new String[]{t2, t3, t4}) post("/api/v1/validation/demandes/" + id + "/valider", t, Map.of("commentaire", "x"), 404);
        post("/api/v1/rh/demandes/" + id + "/approuver", tRh, null, 409);                       // la RH n'intervient qu'après le responsable direct
        JsonNode ok = post("/api/v1/validation/demandes/" + id + "/valider", t1, Map.of("commentaire", "Accord"), 200);
        assertEquals("validee_responsable", ok.get("statut").asText());
        assertEquals("approuvee", post("/api/v1/rh/demandes/" + id + "/approuver", tRh, null, 200).get("statut").asText());
    }

    @Test void unRejetDuResponsableDirectArreteLaDemande() throws Exception {
        String id = demande();
        post("/api/v1/validation/demandes/" + id + "/rejeter", t1, Map.of("commentaire", "Non justifiée"), 200);
        assertEquals("rejetee_responsable", statut(id));
        post("/api/v1/rh/demandes/" + id + "/approuver", tRh, null, 409);
    }

    @Test void sansResponsableDansLaHierarchieLaDemandeVaDirectementALaRh() throws Exception {
        jdbc.update("DELETE FROM responsable_perimetre");
        String id = demande();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM etape_validation WHERE objet_id = ?", Integer.class, id));
        assertEquals("approuvee", post("/api/v1/rh/demandes/" + id + "/approuver", tRh, null, 200).get("statut").asText());
    }

    @Test void laMissionVaDuResponsableDirectALaCellule() throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("objet", "Mission"); b.put("arrivalDelegationId", dest); b.put("departDelegationId", deleg);
        b.put("moyenTransportId", jdbc.queryForObject("SELECT id FROM moyen_transport WHERE code = 'voiture_de_service'", String.class));
        b.put("dateDepart", "2025-03-10T08:00:00"); b.put("dateRetour", "2025-03-11T20:00:00");
        String id = post("/api/v1/missions", tAgent, b, 201).get("id").asText();
        post("/api/v1/missions/" + id + "/soumettre", tAgent, null, 200);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM etape_validation WHERE objet_type = 'mission' AND objet_id = ?", Integer.class, id));
        post("/api/v1/missions/" + id + "/valider-niveau1", t3, null, 409);                    // un niveau supérieur n'est pas dans la chaîne : refusé
        post("/api/v1/missions/" + id + "/approuver", tCell, null, 422);                      // la cellule attend le responsable direct
        assertEquals("validee_responsable", post("/api/v1/missions/" + id + "/valider-niveau1", t1, null, 200).get("statut").asText());
        assertEquals("approuvee", post("/api/v1/missions/" + id + "/approuver", tCell, null, 200).get("statut").asText());
    }

    @Test void leModeToutesLesHierarchiesResteDisponibleParParametre() throws Exception {
        jdbc.update("UPDATE parametre SET valeur = '0' WHERE cle = 'validation.chaine.niveaux_max'");
        try {
            String id = demande();
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM etape_validation WHERE objet_type = 'demande' AND objet_id = ?", Integer.class, id));
        } finally {
            jdbc.update("UPDATE parametre SET valeur = '1' WHERE cle = 'validation.chaine.niveaux_max'");
        }
    }

    @Test void leBrouillonDUneMissionSeRouvrePourModification() throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("objet", "Mission à corriger"); b.put("arrivalDelegationId", dest); b.put("departDelegationId", deleg);
        b.put("moyenTransportId", jdbc.queryForObject("SELECT id FROM moyen_transport WHERE code = 'voiture_de_service'", String.class));
        b.put("dateDepart", "2027-03-10T08:00:00"); b.put("dateRetour", "2027-03-11T20:00:00");
        String id = post("/api/v1/missions", tAgent, b, 201).get("id").asText();
        var r = json.readTree(send(HttpMethod.GET, "/api/v1/missions/" + id + "/edition", tAgent, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("Mission à corriger", r.get("objet").asText());
        assertEquals(dest, r.get("arrivalDelegationId").asText(), "les identifiants sont rendus, pas les libellés");
        assertEquals("2027-03-10T08:00", r.get("dateDepart").asText());
        send(HttpMethod.GET, "/api/v1/missions/" + id + "/edition", tRh, null).andExpect(status().is4xxClientError());       // hors périmètre ou sans droit de saisie
        b.put("objet", "Mission corrigée");
        send(HttpMethod.PUT, "/api/v1/missions/" + id, tAgent, b).andExpect(status().isOk());
        post("/api/v1/missions/" + id + "/soumettre", tAgent, null, 200);
        send(HttpMethod.GET, "/api/v1/missions/" + id + "/edition", tAgent, null).andExpect(status().is4xxClientError());       // plus un brouillon
    }
}
