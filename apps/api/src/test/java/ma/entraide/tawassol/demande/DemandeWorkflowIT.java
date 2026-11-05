package ma.entraide.tawassol.demande;

import java.time.LocalDate;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemandeWorkflowIT extends IntegrationTestBase {
    String a, b, ta, tb, pa, pb, deleg1, deleg2;

    @BeforeEach
    void fixture() throws Exception {
        deleg1 = data.delegation("W1"); deleg2 = data.delegation("W2");
        String s = data.structure(deleg1, null, "WS");
        pa = data.personnel(s, "Agenta"); pb = data.personnel(s, "Agentb");
        data.user("wa", "agent", pa, false); data.user("wb", "agent", pb, false);
        ta = bearer("wa"); tb = bearer("wb");
    }

    String draft(String token, String type, String debut, String fin, Map<String, Object> donnees, String description) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("typeCode", type); body.put("dateDebut", debut); body.put("dateFin", fin); body.put("donnees", donnees); body.put("description", description);
        return idOf(send(HttpMethod.POST, "/api/v1/demandes", token, body).andExpect(status().isCreated()));
    }

    JsonNode submit(String token, String id) throws Exception {
        return json.readTree(send(HttpMethod.POST, "/api/v1/demandes/" + id + "/soumettre", token, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    String leave(String token, String debut, String fin) throws Exception { return draft(token, "CONGE_ANNUEL", debut, fin, Map.of(), null); }

    @Test void congeAnnuelDecompteEnJoursOuvrables_fériéExclu() throws Exception {
        JsonNode d = submit(ta, leave(ta, "2026-11-16", "2026-11-20"));
        assertEquals("soumise", d.get("statut").asText());
        assertEquals(0, new BigDecimal("4").compareTo(d.get("nbJours").decimalValue()), "16 au 20 novembre 2026 : 4 jours (18/11 férié)");
        assertTrue(d.get("numero").asText().matches("DEM-\\d{4}-\\d{6}"));
    }

    @Test void numerosSequentiels() throws Exception {
        String n1 = submit(ta, leave(ta, "2026-11-16", "2026-11-16")).get("numero").asText();
        String n2 = submit(tb, leave(tb, "2026-11-16", "2026-11-16")).get("numero").asText();
        assertEquals(Integer.parseInt(n1.substring(n1.length() - 6)) + 1, Integer.parseInt(n2.substring(n2.length() - 6)));
    }

    @Test void soumissionIdempotente_sansDoublonDansLHistorique() throws Exception {
        String id = leave(ta, "2026-11-16", "2026-11-17");
        JsonNode first = submit(ta, id), second = submit(ta, id);
        assertEquals(first.get("numero"), second.get("numero"));
        assertEquals(2, second.get("historique").size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'DEMANDE_SOUMISE'", Integer.class));
    }

    @Test void soldeInsuffisant() throws Exception {
        send(HttpMethod.POST, "/api/v1/demandes/" + leave(ta, "2026-01-05", "2026-03-27") + "/soumettre", ta, null)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test void soldeTientCompteDesDemandesDejaSoumises() throws Exception {
        // dates relatives à aujourd'hui : une période passée ne réserve plus de jours (H-CONGE-4)
        LocalDate lundi = LocalDate.now().plusDays(40);
        lundi = lundi.plusDays((8 - lundi.getDayOfWeek().getValue()) % 7);
        if (lundi.plusDays(9).getYear() != lundi.getYear()) lundi = LocalDate.of(lundi.getYear() + 1, 1, 1).plusDays((8 - LocalDate.of(lundi.getYear() + 1, 1, 1).getDayOfWeek().getValue()) % 7 + 7);
        jdbc.update("INSERT INTO solde_conge (personnel_id, annee, droit, pris, reporte) VALUES (?, ?, 5.0, 0, 0)", pa, lundi.getYear());
        submit(ta, leave(ta, lundi.toString(), lundi.plusDays(3).toString()));
        send(HttpMethod.POST, "/api/v1/demandes/" + leave(ta, lundi.plusDays(7).toString(), lundi.plusDays(9).toString()) + "/soumettre", ta, null)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test void chevauchementRefuse() throws Exception {
        submit(ta, leave(ta, "2026-11-16", "2026-11-20"));
        send(HttpMethod.POST, "/api/v1/demandes/" + leave(ta, "2026-11-19", "2026-11-23") + "/soumettre", ta, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAVE_OVERLAP"));
    }

    @Test void periodeSansJourOuvrableRefusee() throws Exception {
        send(HttpMethod.POST, "/api/v1/demandes/" + leave(ta, "2026-11-21", "2026-11-22") + "/soumettre", ta, null)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("NO_WORKING_DAYS"));
    }

    @Test void datesIncoherentesRefuseesDesLaCreation() throws Exception {
        send(HttpMethod.POST, "/api/v1/demandes", ta, Map.of("typeCode", "CONGE_ANNUEL", "dateDebut", "2026-11-20", "dateFin", "2026-11-16"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_DATES"));
    }

    @Test void annulation_et_etatsInterdits() throws Exception {
        String id = leave(ta, "2026-11-16", "2026-11-17");
        submit(ta, id);
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/annuler", ta, Map.of("commentaire", "Changement de plan")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("annulee"));
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/annuler", ta, null).andExpect(status().isOk());
        String id2 = leave(ta, "2026-12-01", "2026-12-02");
        submit(ta, id2);
        jdbc.update("UPDATE demande SET statut = 'approuvee' WHERE id = ?", id2);
        send(HttpMethod.POST, "/api/v1/demandes/" + id2 + "/annuler", ta, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test void unAgentNeTouchePasLaDemandeDUnAutre() throws Exception {
        String id = leave(ta, "2026-11-16", "2026-11-17");
        send(HttpMethod.GET, "/api/v1/demandes/" + id, tb, null).andExpect(status().isNotFound());
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/soumettre", tb, null).andExpect(status().isNotFound());
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/annuler", tb, null).andExpect(status().isNotFound());
        send(HttpMethod.DELETE, "/api/v1/demandes/" + id, tb, null).andExpect(status().isNotFound());
        send(HttpMethod.GET, "/api/v1/demandes", tb, null).andExpect(jsonPath("$.total").value(0));
    }

    @Test void brouillonModifiableEtSupprimable_pasApresSoumission() throws Exception {
        String id = leave(ta, "2026-11-16", "2026-11-17");
        send(HttpMethod.PUT, "/api/v1/demandes/" + id, ta, Map.of("typeCode", "CONGE_ANNUEL", "dateDebut", "2026-11-16", "dateFin", "2026-11-18", "objet", "Congé modifié"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.objet").value("Congé modifié"));
        send(HttpMethod.PUT, "/api/v1/demandes/" + id, ta, Map.of("typeCode", "ATTESTATION")).andExpect(status().isUnprocessableEntity());
        submit(ta, id);
        send(HttpMethod.PUT, "/api/v1/demandes/" + id, ta, Map.of("typeCode", "CONGE_ANNUEL", "dateDebut", "2026-11-16", "dateFin", "2026-11-18")).andExpect(status().isConflict());
        send(HttpMethod.DELETE, "/api/v1/demandes/" + id, ta, null).andExpect(status().isConflict());
        String d2 = leave(ta, "2026-12-07", "2026-12-08");
        send(HttpMethod.DELETE, "/api/v1/demandes/" + d2, ta, null).andExpect(status().isNoContent());
    }

    @Test void historiqueCompletEtNotification() throws Exception {
        String id = leave(ta, "2026-11-16", "2026-11-17");
        submit(ta, id);
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/annuler", ta, null).andExpect(status().isOk());
        JsonNode d = json.readTree(send(HttpMethod.GET, "/api/v1/demandes/" + id, ta, null).andReturn().getResponse().getContentAsString());
        assertEquals(3, d.get("historique").size());
        assertEquals("annulee", d.get("historique").get(2).get("nouveauStatut").asText());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE utilisateur_id = (SELECT id FROM utilisateur WHERE login = 'wa')", Integer.class));
    }

    @Test void filtreParStatutEtPagination() throws Exception {
        submit(ta, leave(ta, "2026-11-16", "2026-11-17"));
        leave(ta, "2026-12-07", "2026-12-08");
        send(HttpMethod.GET, "/api/v1/demandes?statut=brouillon", ta, null).andExpect(jsonPath("$.total").value(1));
        send(HttpMethod.GET, "/api/v1/demandes?size=1", ta, null).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test void attestationExigeLeTypeEtNaPasBesoinDuResponsable() throws Exception {
        send(HttpMethod.POST, "/api/v1/demandes/" + draft(ta, "ATTESTATION", null, null, Map.of(), null) + "/soumettre", ta, null)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_ATTESTATION_TYPE"));
        JsonNode d = submit(ta, draft(ta, "ATTESTATION", null, null, Map.of("typeAttestation", "travail"), null));
        assertEquals("soumise", d.get("statut").asText());
        assertEquals(0, jdbc.queryForObject("SELECT validation_responsable FROM type_demande WHERE code = 'ATTESTATION'", Integer.class));
    }

    @Test void leTypeAvanceSurSalaireNExistePlus() throws Exception {
        send(HttpMethod.POST, "/api/v1/demandes", ta, Map.of("typeCode", "AVANCE_SALAIRE", "donnees", Map.of("montant", 1000))).andExpect(status().is4xxClientError());
        send(HttpMethod.GET, "/api/v1/types-demande", ta, null).andExpect(jsonPath("$[*].code", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("AVANCE_SALAIRE"))));
    }

    @Test void ordreDeMissionCreeUnEnregistrementLie_etSonAnnulationLePropage() throws Exception {
        String transport = jdbc.queryForObject("SELECT id FROM moyen_transport WHERE code = 'voiture_de_service'", String.class);
        String id = draft(ta, "ORDRE_MISSION", "2026-11-25", "2026-11-26", Map.of("departDelegationId", deleg1, "arrivalDelegationId", deleg2, "moyenTransportId", transport), null);
        submit(ta, id);
        assertEquals("soumise", jdbc.queryForObject("SELECT statut FROM ordre_mission WHERE demande_id = ?", String.class, id));
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/annuler", ta, null).andExpect(status().isOk());
        assertEquals("annulee", jdbc.queryForObject("SELECT statut FROM ordre_mission WHERE demande_id = ?", String.class, id));
        String bad = draft(ta, "ORDRE_MISSION", "2026-12-02", "2026-12-03", Map.of("departDelegationId", "inconnu", "arrivalDelegationId", deleg2, "moyenTransportId", transport), null);
        send(HttpMethod.POST, "/api/v1/demandes/" + bad + "/soumettre", ta, null).andExpect(status().isUnprocessableEntity());
    }

    @Test void formationEtAutre() throws Exception {
        send(HttpMethod.POST, "/api/v1/demandes/" + draft(ta, "FORMATION", "2026-12-01", "2026-12-03", Map.of(), null) + "/soumettre", ta, null)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TITLE_REQUIRED"));
        submit(ta, draft(ta, "FORMATION", "2026-12-01", "2026-12-03", Map.of("intitule", "Gestion des archives", "cout", 1500), null));
        send(HttpMethod.POST, "/api/v1/demandes/" + draft(ta, "AUTRE", null, null, Map.of(), null) + "/soumettre", ta, null).andExpect(status().isUnprocessableEntity());
        submit(ta, draft(ta, "AUTRE", null, null, Map.of(), "Copie conforme d'un diplôme"));
    }

    @Test void typeInconnuEtSoldeDuCompte() throws Exception {
        send(HttpMethod.POST, "/api/v1/demandes", ta, Map.of("typeCode", "N_IMPORTE_QUOI")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_TYPE"));
        send(HttpMethod.GET, "/api/v1/me/solde-conge?annee=2026", ta, null).andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(22.0));
    }

    @Test void compteSansAgentNePeutPasCreerDeDemande() throws Exception {
        data.user("tech2", "admin", null, false);
        send(HttpMethod.POST, "/api/v1/demandes", bearer("tech2"), Map.of("typeCode", "AUTRE")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NO_PERSONNEL"));
    }
}
