package ma.entraide.tawassol.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Circuit du premier niveau : routage vers le bon responsable, séparation des tâches, suppléance, confidentialité médicale. */
class ValidationIT extends IntegrationTestBase {
    String d1, d2, s1, s2, s3, s4;
    String pA, pB, pResp1, pRespDeleg, pC, pResp2;
    String tA, tB, tResp1, tRespDeleg, tC, tResp2;

    @BeforeEach
    void fixture() throws Exception {
        d1 = data.delegation("V1"); d2 = data.delegation("V2");
        s1 = data.structure(d1, null, "VS1"); s2 = data.structure(d1, s1, "VS2"); s3 = data.structure(d1, null, "VS3"); s4 = data.structure(d2, null, "VS4");
        pResp1 = data.personnel(s1, "Resunun"); pRespDeleg = data.personnel(s3, "Resdeleg"); pResp2 = data.personnel(s4, "Resdeux");
        pA = data.personnel(s2, "Agenta"); pB = data.personnel(s1, "Agentb"); pC = data.personnel(s4, "Agentc");
        for (String[] u : new String[][]{{"resp1", "responsable", pResp1}, {"respdeleg", "responsable", pRespDeleg}, {"resp2", "responsable", pResp2},
                {"va", "agent", pA}, {"vb", "agent", pB}, {"vc", "agent", pC}}) data.user(u[0], u[1], u[2], false);
        LocalDate debut = LocalDate.of(2020, 1, 1);
        data.perimeter(pResp1, "structure", s1, debut, null, true);
        data.perimeter(pRespDeleg, "delegation", d1, debut, null, true);
        data.perimeter(pResp2, "structure", s4, debut, null, true);
        tA = bearer("va"); tB = bearer("vb"); tResp1 = bearer("resp1"); tRespDeleg = bearer("respdeleg"); tC = bearer("vc"); tResp2 = bearer("resp2");
    }

    String submitLeave(String token, String debut, String fin) throws Exception {
        String id = idOf(send(HttpMethod.POST, "/api/v1/demandes", token, Map.of("typeCode", "CONGE_ANNUEL", "dateDebut", debut, "dateFin", fin)).andExpect(status().isCreated()));
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/soumettre", token, null).andExpect(status().isOk());
        return id;
    }

    String responsableOf(String demandeId) { return jdbc.queryForObject("SELECT responsable_personnel_id FROM demande WHERE id = ?", String.class, demandeId); }

    // ---- routage ----

    @Test void leResponsableCompetentEstLePlusProcheDansLArbre() throws Exception {
        assertEquals(pResp1, responsableOf(submitLeave(tA, "2026-11-16", "2026-11-17")), "agent de S2, enfant de S1 : responsable de S1");
        assertEquals(pResp1, responsableOf(submitLeave(tB, "2026-11-23", "2026-11-24")));
        assertEquals(pResp2, responsableOf(submitLeave(tC, "2026-11-16", "2026-11-17")), "autre délégation, autre responsable");
    }

    @Test void laDemandeDUnResponsableRemonteAuNiveauSuperieur_etSansPersonneVaALaRh() throws Exception {
        assertEquals(pRespDeleg, responsableOf(submitLeave(tResp1, "2026-11-16", "2026-11-17")), "jamais lui-même");
        assertNull(responsableOf(submitLeave(tRespDeleg, "2026-11-16", "2026-11-17")), "plus personne au-dessus : RH directe");
        assertNull(responsableOf(submitLeave(tResp2, "2026-11-16", "2026-11-17")));
    }

    @Test void uneAttestationNePasseParAucunResponsable() throws Exception {
        String id = idOf(send(HttpMethod.POST, "/api/v1/demandes", tA, Map.of("typeCode", "ATTESTATION", "donnees", Map.of("typeAttestation", "travail"))).andExpect(status().isCreated()));
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/soumettre", tA, null).andExpect(status().isOk());
        assertNull(responsableOf(id));
    }

    @Test void leResponsableEstNotifieALaSoumission() throws Exception {
        submitLeave(tA, "2026-11-16", "2026-11-17");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'VALIDATION_A_FAIRE' AND utilisateur_id = (SELECT id FROM utilisateur WHERE login = 'resp1')", Integer.class));
    }

    // ---- file et accès ----

    @Test void laFileNeMontreQueLesDemandesAttribuees_jamaisLaSienne() throws Exception {
        String a = submitLeave(tA, "2026-11-16", "2026-11-17");
        String own = submitLeave(tResp1, "2026-12-07", "2026-12-08");
        String autre = submitLeave(tC, "2026-11-16", "2026-11-17");
        send(HttpMethod.GET, "/api/v1/validation/demandes", tResp1, null).andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", hasItem(a)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(own)))).andExpect(jsonPath("$.content[*].id", not(hasItem(autre))));
        send(HttpMethod.GET, "/api/v1/validation/demandes", tRespDeleg, null).andExpect(jsonPath("$.content[*].id", hasItem(own))).andExpect(jsonPath("$.content[*].id", not(hasItem(a))));
        send(HttpMethod.GET, "/api/v1/validation/demandes", tA, null).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test void accesAuDetailRefuseHorsAttribution() throws Exception {
        String a = submitLeave(tA, "2026-11-16", "2026-11-17");
        send(HttpMethod.GET, "/api/v1/validation/demandes/" + a, tResp1, null).andExpect(status().isOk()).andExpect(jsonPath("$.agent.matricule").exists());
        send(HttpMethod.GET, "/api/v1/validation/demandes/" + a, tResp2, null).andExpect(status().isNotFound());
        // le délégué fait partie de la chaîne : il voit la demande, mais ne peut pas la décider avant son tour
        send(HttpMethod.GET, "/api/v1/validation/demandes/" + a, tRespDeleg, null).andExpect(status().isOk());
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/valider", tRespDeleg, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_YOUR_TURN"));
        send(HttpMethod.GET, "/api/v1/validation/demandes/" + a, tB, null).andExpect(status().isNotFound());
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/valider", tResp2, null).andExpect(status().isNotFound());
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/valider", tB, null).andExpect(status().isNotFound());
    }

    @Test void leDetailMontreLesAbsencesDeLEquipeEtLeSoldeMaisPasDeDonneesFinancieres() throws Exception {
        submitLeave(tB, "2026-11-18", "2026-11-19");
        String a = submitLeave(tA, "2026-11-16", "2026-11-19");
        JsonNode d = json.readTree(send(HttpMethod.GET, "/api/v1/validation/demandes/" + a, tResp1, null).andReturn().getResponse().getContentAsString());
        assertEquals("conge", d.get("equipe").get(0).get("genre").asText());
        assertTrue(d.get("solde").get("suffisant").asBoolean());
        assertFalse(d.toString().toLowerCase().contains("salaire"));
        assertFalse(d.get("agent").has("email"));
    }

    // ---- décision ----

    @Test void validerPuisIdempotence() throws Exception {
        String a = submitLeave(tA, "2026-11-16", "2026-11-17");
        // chaîne complète : S1 puis la délégation ; la demande n'est « validée » qu'après le dernier niveau
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/valider", tResp1, Map.of("commentaire", "Accord")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("soumise"));
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/valider", tRespDeleg, Map.of("commentaire", "Accord")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("validee_responsable"));
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/valider", tRespDeleg, null).andExpect(status().isOk());
        assertEquals("validee_responsable", jdbc.queryForObject("SELECT statut FROM demande WHERE id = ?", String.class, a));
        assertNotNull(jdbc.queryForObject("SELECT valide_par FROM demande WHERE id = ?", String.class, a));
        JsonNode agent = json.readTree(send(HttpMethod.GET, "/api/v1/demandes/" + a, tA, null).andReturn().getResponse().getContentAsString());
        assertEquals(4, agent.get("historique").size(), "création, soumission, niveau 1 franchi, validation finale");
        assertTrue(agent.get("historique").get(3).get("commentaire").asText().contains("Accord"));
        assertEquals(2, agent.get("chaine").size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'DEMANDE_VALIDEE' AND utilisateur_id = (SELECT id FROM utilisateur WHERE login = 'va')", Integer.class));
    }

    @Test void leRejetExigeUnCommentaire() throws Exception {
        String a = submitLeave(tA, "2026-11-16", "2026-11-17");
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/rejeter", tResp1, null).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/rejeter", tResp1, Map.of("commentaire", "  ")).andExpect(status().isUnprocessableEntity());
        assertEquals("soumise", jdbc.queryForObject("SELECT statut FROM demande WHERE id = ?", String.class, a));
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/rejeter", tResp1, Map.of("commentaire", "Période de forte activité")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("rejetee_responsable"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'DEMANDE_REJETEE' AND message LIKE '%forte activité%'", Integer.class));
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/valider", tResp1, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test void uneDemandeAnnuleeParLAgentNEstPlusATraiter() throws Exception {
        String a = submitLeave(tA, "2026-11-16", "2026-11-17");
        send(HttpMethod.POST, "/api/v1/demandes/" + a + "/annuler", tA, null).andExpect(status().isOk());
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/valider", tResp1, null).andExpect(status().isConflict());
        send(HttpMethod.GET, "/api/v1/validation/demandes", tResp1, null).andExpect(jsonPath("$.total").value(0));
    }

    // ---- suppléance ----

    @Test void leSuppleantTraiteLaFilePendantLaSuppleanceSeulement_etPasSaPropreDemande() throws Exception {
        String a = submitLeave(tA, "2026-11-16", "2026-11-17");
        String own = submitLeave(tB, "2026-11-23", "2026-11-24");
        data.suppleance(pResp1, pB, LocalDate.now().minusDays(1), LocalDate.now().plusDays(10));
        send(HttpMethod.GET, "/api/v1/validation/demandes", tB, null).andExpect(jsonPath("$.content[*].id", hasItem(a))).andExpect(jsonPath("$.content[*].id", not(hasItem(own))));
        send(HttpMethod.GET, "/api/v1/validation/demandes/" + own, tB, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SELF_VALIDATION_FORBIDDEN"));
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + own + "/valider", tB, null).andExpect(status().isForbidden());
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + a + "/valider", tB, null).andExpect(status().isOk());
        JsonNode h = json.readTree(send(HttpMethod.GET, "/api/v1/demandes/" + a, tA, null).andReturn().getResponse().getContentAsString()).get("historique");
        assertTrue(h.toString().contains("[par suppléance]"));
        String a2 = submitLeave(tA, "2026-12-07", "2026-12-08");
        jdbc.update("UPDATE suppleance SET date_debut = ?, date_fin = ?", LocalDate.now().minusDays(10), LocalDate.now().minusDays(2));
        send(HttpMethod.GET, "/api/v1/validation/demandes/" + a2, tB, null).andExpect(status().isNotFound());
    }

    // ---- confidentialité ----

    @Test void leResponsableNeVoitNiMotifNiPiecesDUnCongeMaladie() throws Exception {
        String id = idOf(send(HttpMethod.POST, "/api/v1/demandes", tA, Map.of("typeCode", "CONGE_MALADIE", "dateDebut", "2026-11-16", "dateFin", "2026-11-17", "description", "Opération du genou")).andExpect(status().isCreated()));
        jdbc.update("INSERT INTO piece_jointe (id, demande_id, nom_fichier, url_fichier, type_mime, taille_kb) VALUES (?,?,'certificat.pdf','cle','application/pdf',10)", UUID.randomUUID().toString(), id);
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/soumettre", tA, null).andExpect(status().isOk());
        String body = send(HttpMethod.GET, "/api/v1/validation/demandes/" + id, tResp1, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("genou"), body);
        assertFalse(body.contains("certificat"), body);
        assertEquals("Congé maladie", json.readTree(body).get("objet").asText());
        assertTrue(!json.readTree(body).has("description") || json.readTree(body).get("description").isNull());
    }

    @Test void tableauDeBord() throws Exception {
        submitLeave(tA, "2026-11-16", "2026-11-17");
        send(HttpMethod.GET, "/api/v1/validation/tableau-de-bord", tResp1, null).andExpect(status().isOk()).andExpect(jsonPath("$.aValider").value(1)).andExpect(jsonPath("$.effectif").value(2));
    }
}
