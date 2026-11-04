package ma.entraide.tawassol.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mode « toute la hiérarchie » (validation.chaine.niveaux_max = 0) : tous les responsables valident, dans l'ordre : structure, structure parente, délégation, coordination. */
class ChaineValidationIT extends IntegrationTestBase {
    String deleg, dest, parent, enfant, pAgent, r1, r2, r3, r4, suppleant, rh;
    String tAgent, t1, t2, t3, t4, tSupp, tRh, tCell;

    @AfterEach void remettreLeParametre() { jdbc.update("UPDATE parametre SET valeur = '1' WHERE cle = 'validation.chaine.niveaux_max'"); }

    @BeforeEach void fixture() throws Exception {
        jdbc.update("UPDATE parametre SET valeur = '0' WHERE cle = 'validation.chaine.niveaux_max'");
        deleg = data.delegation("CV"); dest = data.delegation("CVD");
        parent = data.structure(deleg, null, "CVP"); enfant = data.structure(deleg, parent, "CVE");
        pAgent = data.personnel(enfant, "Agent"); r1 = data.personnel(parent, "Niv1"); r2 = data.personnel(parent, "Niv2"); r3 = data.personnel(parent, "Niv3"); r4 = data.personnel(parent, "Niv4");
        suppleant = data.personnel(parent, "Supp"); rh = data.personnel(parent, "Rh");
        jdbc.update("UPDATE personnel SET echelle = 7 WHERE id = ?", pAgent);
        data.user("ag", "agent", pAgent, false); data.user("r1", "responsable", r1, false); data.user("r2", "responsable", r2, false); data.user("r3", "responsable", r3, false); data.user("r4", "responsable", r4, false);
        data.user("supp", "responsable", suppleant, false); data.user("rh", "rh", rh, false);
        String coord = jdbc.queryForObject("SELECT coordination_id FROM delegation_province WHERE id = ?", String.class, deleg);
        LocalDate debut = LocalDate.of(2020, 1, 1);
        data.perimeter(r1, "structure", enfant, debut, null, true);       // niveau 1 : la structure de l'agent
        data.perimeter(r2, "structure", parent, debut, null, true);       // niveau 2 : la structure parente
        data.perimeter(r3, "delegation", deleg, debut, null, true);       // niveau 3 : la délégation
        data.perimeter(r4, "coordination", coord, debut, null, true);     // niveau 4 : la coordination
        tAgent = bearer("ag"); t1 = bearer("r1"); t2 = bearer("r2"); t3 = bearer("r3"); t4 = bearer("r4"); tSupp = bearer("supp"); tRh = bearer("rh");
    }

    String demande() throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("typeCode", "AUTRE"); b.put("objet", "Demande test"); b.put("description", "Description");
        String id = idOf(send(HttpMethod.POST, "/api/v1/demandes", tAgent, b).andExpect(status().isCreated()));
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/soumettre", tAgent, null).andExpect(status().isOk());
        return id;
    }

    JsonNode decide(String id, String token, String action, int statut) throws Exception {
        var r = send(HttpMethod.POST, "/api/v1/validation/demandes/" + id + "/" + action, token, Map.of("commentaire", "ok")).andExpect(status().is(statut));
        String s = r.andReturn().getResponse().getContentAsString();
        return s.isBlank() ? null : json.readTree(s);
    }

    String statut(String id) { return jdbc.queryForObject("SELECT statut FROM demande WHERE id = ?", String.class, id); }

    @Test void laDemandeParcourtTousLesNiveauxDansLOrdre() throws Exception {
        String id = demande();
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM etape_validation WHERE objet_type = 'demande' AND objet_id = ?", Integer.class, id));
        assertEquals(r1, jdbc.queryForObject("SELECT responsable_personnel_id FROM demande WHERE id = ?", String.class, id));
        // seul le niveau actif décide ; les autres reçoivent « pas votre tour »
        assertEquals("NOT_YOUR_TURN", decide(id, t2, "valider", 409).get("code").asText());
        assertEquals("NOT_YOUR_TURN", decide(id, t4, "valider", 409).get("code").asText());
        decide(id, tAgent, "valider", 404);                                  // l'agent ne voit pas la file des responsables
        assertEquals("soumise", decide(id, t1, "valider", 200).get("statut").asText());
        assertEquals(r2, jdbc.queryForObject("SELECT responsable_personnel_id FROM demande WHERE id = ?", String.class, id));
        decide(id, t1, "valider", 409);                                      // niveau 1 déjà franchi : plus son tour
        assertEquals("soumise", decide(id, t2, "valider", 200).get("statut").asText());
        assertEquals("soumise", decide(id, t3, "valider", 200).get("statut").asText());
        assertEquals("soumise", statut(id), "tant que tous n'ont pas validé, la RH ne la voit pas");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM demande d WHERE d.id = ? AND (d.statut = 'validee_responsable' OR (d.statut = 'soumise' AND d.responsable_personnel_id IS NULL))", Integer.class, id));
        assertEquals("validee_responsable", decide(id, t4, "valider", 200).get("statut").asText());
        // la RH la voit seulement maintenant
        assertTrue(send(HttpMethod.GET, "/api/v1/rh/demandes", tRh, null).andReturn().getResponse().getContentAsString().contains(id));
        // chaque niveau est tracé
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM etape_validation WHERE objet_type = 'demande' AND objet_id = ? AND statut = 'validee' AND decide_par IS NOT NULL AND date_decision IS NOT NULL", Integer.class, id));
        // l'agent voit toute la chaîne
        JsonNode d = json.readTree(send(HttpMethod.GET, "/api/v1/demandes/" + id, tAgent, null).andReturn().getResponse().getContentAsString());
        assertEquals(4, d.get("chaine").size()); assertEquals("structure", d.get("chaine").get(0).get("niveau").asText()); assertEquals("coordination", d.get("chaine").get(3).get("niveau").asText());
        // chaque responsable retrouve la demande dans « validées »
        assertTrue(send(HttpMethod.GET, "/api/v1/validation/demandes?statut=validee_responsable", t2, null).andReturn().getResponse().getContentAsString().contains(id));
    }

    @Test void unRejetArreteLaChaine() throws Exception {
        String id = demande();
        decide(id, t1, "valider", 200);
        assertEquals("rejetee_responsable", decide(id, t2, "rejeter", 200).get("statut").asText());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM etape_validation WHERE objet_id = ? AND statut = 'ignoree'", Integer.class, id));
        decide(id, t3, "valider", 409);
        assertEquals("rejetee_responsable", statut(id));
    }

    @Test void leSuppleantDecideAlaPlaceDuResponsableAbsent() throws Exception {
        String id = demande();
        decide(id, t1, "valider", 200);
        data.suppleance(r2, suppleant, LocalDate.now().minusDays(1), LocalDate.now().plusDays(5));
        decide(id, tSupp, "valider", 200);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM etape_validation WHERE objet_id = ? AND ordre = 2 AND par_suppleance = 1", Integer.class, id));
        assertTrue(jdbc.queryForObject("SELECT commentaire FROM demande_historique WHERE demande_id = ? AND commentaire LIKE '%suppléance%' LIMIT 1", String.class, id) != null);
    }

    @Test void leResponsableEstEcarteDeSaPropreChaine() throws Exception {
        // la demande d'un responsable de niveau 1 remonte directement aux niveaux supérieurs
        String tOwn = t1;
        Map<String, Object> b = Map.of("typeCode", "AUTRE", "objet", "Ma demande", "description", "x");
        String id = idOf(send(HttpMethod.POST, "/api/v1/demandes", tOwn, b).andExpect(status().isCreated()));
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/soumettre", tOwn, null).andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM etape_validation WHERE objet_id = ? AND responsable_personnel_id = ?", Integer.class, id, r1));
        assertEquals(r2, jdbc.queryForObject("SELECT responsable_personnel_id FROM demande WHERE id = ?", String.class, id), "la chaîne commence au niveau au-dessus");
    }

    // ---- missions : même chaîne ----

    Map<String, Object> mission() {
        Map<String, Object> b = new HashMap<>();
        b.put("objet", "Mission"); b.put("arrivalDelegationId", dest); b.put("departDelegationId", deleg);
        b.put("moyenTransportId", jdbc.queryForObject("SELECT id FROM moyen_transport WHERE code = 'voiture_de_service'", String.class));
        b.put("dateDepart", "2025-03-10T08:00:00"); b.put("dateRetour", "2025-03-11T20:00:00");
        return b;
    }

    JsonNode post(String path, String token, Object body, int statut) throws Exception {
        var r = send(HttpMethod.POST, path, token, body).andExpect(status().is(statut));
        String s = r.andReturn().getResponse().getContentAsString();
        return s.isBlank() ? null : json.readTree(s);
    }

    @Test void laMissionSuitLaMemeChaineAvantLApprobation() throws Exception {
        String uc = data.user("cell", "rh", data.personnel(parent, "Cell"), false);
        jdbc.update("UPDATE utilisateur SET role_id = (SELECT id FROM role WHERE code = 'cellule_deplacements') WHERE id = ?", uc);
        tCell = bearer("cell");
        String id = post("/api/v1/missions", tAgent, mission(), 201).get("id").asText();
        post("/api/v1/missions/" + id + "/soumettre", tAgent, null, 200);
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM etape_validation WHERE objet_type = 'mission' AND objet_id = ?", Integer.class, id));
        post("/api/v1/missions/" + id + "/valider-niveau1", t3, null, 409);            // pas son tour
        JsonNode apres1 = post("/api/v1/missions/" + id + "/valider-niveau1", t1, null, 200);
        assertEquals("soumise", apres1.get("statut").asText());
        assertEquals(4, apres1.get("chaine").size());
        assertFalse(apres1.get("actionsPossibles").toString().contains("valider_niveau1"), "l'acteur ne revoit pas le bouton après avoir validé");
        post("/api/v1/missions/" + id + "/approuver", tCell, null, 422);               // la cellule n'approuve pas avant la fin de la chaîne
        post("/api/v1/missions/" + id + "/valider-niveau1", t2, null, 200);
        post("/api/v1/missions/" + id + "/valider-niveau1", t3, null, 200);
        JsonNode fin = post("/api/v1/missions/" + id + "/valider-niveau1", t4, null, 200);
        assertEquals("validee_responsable", fin.get("statut").asText());
        JsonNode ok = post("/api/v1/missions/" + id + "/approuver", tCell, null, 200);
        assertEquals("approuvee", ok.get("statut").asText());
        // le rejet d'un niveau arrête la mission
        String id2 = post("/api/v1/missions", tAgent, mission(), 201).get("id").asText();
        jdbc.update("UPDATE ordre_mission SET date_depart = '2025-05-10 08:00:00', date_retour = '2025-05-11 20:00:00' WHERE id = ?", id2);
        post("/api/v1/missions/" + id2 + "/soumettre", tAgent, null, 200);
        post("/api/v1/missions/" + id2 + "/valider-niveau1", t1, null, 200);
        assertEquals("rejetee", post("/api/v1/missions/" + id2 + "/rejeter", t2, Map.of("commentaire", "Non justifiée"), 200).get("statut").asText());
        post("/api/v1/missions/" + id2 + "/valider-niveau1", t3, null, 422);
    }

    @Test void sansAucunResponsableLaMissionPasseDirectementALApprobation() throws Exception {
        jdbc.update("DELETE FROM responsable_perimetre");
        String id = post("/api/v1/missions", tAgent, mission(), 201).get("id").asText();
        assertEquals("validee_responsable", post("/api/v1/missions/" + id + "/soumettre", tAgent, null, 200).get("statut").asText());
    }
}
