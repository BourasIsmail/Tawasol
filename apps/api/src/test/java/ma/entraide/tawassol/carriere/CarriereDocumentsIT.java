package ma.entraide.tawassol.carriere;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import ma.entraide.tawassol.deplacement.VerificationService;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CarriereDocumentsIT extends IntegrationTestBase {
    @Autowired VerificationService verif;
    String pAgent, pRh, pAutre, tAgent, tRh, tAutre, grade;

    @BeforeEach void fixture() throws Exception {
        String d = data.delegation("CD");
        String s = data.structure(d, null, "CDS");
        pAgent = data.personnel(s, "Agent"); pRh = data.personnel(s, "Rh"); pAutre = data.personnel(s, "Autre");
        grade = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO grade (id, code, libelle_fr, libelle_ar, echelle, nb_echelons) VALUES (?,?,?,?,?,?)", grade, 700, "Administrateur", "متصرف", 10, 5);
        jdbc.update("UPDATE personnel SET grade_id = ?, echelon = '2', date_echelon = '2022-01-01', sexe = 'F' WHERE id = ?", grade, pAgent);
        data.user("ag", "agent", pAgent, false); data.user("rh", "rh", pRh, false); data.user("autre", "agent", pAutre, false);
        tAgent = bearer("ag"); tRh = bearer("rh"); tAutre = bearer("autre");
    }

    String demandeAttestation(String type, String statut) {
        String id = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO demande (id, numero, personnel_id, type_demande_id, objet, statut, donnees_json) VALUES (?,?,?,(SELECT id FROM type_demande WHERE code = 'ATTESTATION'),?,?,?)", id, "DEM-2026-9" + (int) (Math.random() * 90000 + 10000), pAgent, "Banque", statut, "{\"typeAttestation\":\"" + type + "\"}");
        return id;
    }

    byte[] get(String path, String token, int statut) throws Exception {
        return send(HttpMethod.GET, path, token, null).andExpect(status().is(statut)).andReturn().getResponse().getContentAsByteArray();
    }

    @Test void attestationDeTravailNumeroteeArchiveeEtVerifiable() throws Exception {
        String id = demandeAttestation("travail", "soumise");
        get("/api/v1/demandes/" + id + "/attestation", tAgent, 422);          // pas avant l'approbation
        jdbc.update("UPDATE demande SET statut = 'approuvee' WHERE id = ?", id);
        get("/api/v1/demandes/" + id + "/attestation", tAutre, 404);          // un autre agent ne la voit pas
        byte[] pdf = get("/api/v1/demandes/" + id + "/attestation", tAgent, 200);
        assertEquals("%PDF", new String(pdf, 0, 4));
        String numero = jdbc.queryForObject("SELECT numero FROM document_officiel WHERE demande_id = ?", String.class, id);
        assertTrue(numero.matches("ATT-\\d{4}-\\d{6}"), numero);
        // le fichier émis est restitué tel quel, sans nouveau numéro
        assertArrayEquals(pdf, get("/api/v1/demandes/" + id + "/attestation", tRh, 200));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM document_officiel WHERE demande_id = ?", Integer.class, id));
        String docId = jdbc.queryForObject("SELECT id FROM document_officiel WHERE demande_id = ?", String.class, id);
        String jeton = verif.jeton(docId, "attestation", numero);
        String ok = mvc.perform(MockMvcRequestBuilders.get("/api/v1/verification/attestation/" + docId + "/" + jeton)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(ok.contains(numero) && ok.contains("\"valide\":true"));
        assertFalse(ok.contains("Agent"), "aucune donnée personnelle");
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/verification/attestation/" + docId + "/" + jeton + "x")).andExpect(status().isNotFound());
        jdbc.update("UPDATE document_officiel SET annule = 1 WHERE id = ?", docId);
        assertTrue(mvc.perform(MockMvcRequestBuilders.get("/api/v1/verification/attestation/" + docId + "/" + jeton)).andReturn().getResponse().getContentAsString().contains("\"valide\":false"));
        // numérotation suivante
        String id2 = demandeAttestation("travail", "approuvee");
        get("/api/v1/demandes/" + id2 + "/attestation", tAgent, 200);
        assertNotEquals(numero, jdbc.queryForObject("SELECT numero FROM document_officiel WHERE demande_id = ?", String.class, id2));
    }

    @Test void attestationDeSalaireExigeUnBulletinPublie() throws Exception {
        String id = demandeAttestation("salaire", "approuvee");
        get("/api/v1/demandes/" + id + "/attestation", tAgent, 422);
        jdbc.update("INSERT INTO salaire (id, personnel_id, annee, mois, salaire_net, statut, publie_le) VALUES (UUID(),?,2026,9,6543.21,'publie',NOW())", pAgent);
        byte[] pdf = get("/api/v1/demandes/" + id + "/attestation", tAgent, 200);
        assertTrue(pdf.length > 4000);
        java.nio.file.Files.write(java.nio.file.Path.of("target/exemple-attestation.pdf"), pdf);
    }

    @Test void avancementUniquementParLaRhAvecDecisionTracee() throws Exception {
        JsonNode props = json.readTree(get("/api/v1/rh/avancement/propositions", tRh, 200));
        assertTrue(props.get("propositions").toString().contains(pAgent));
        assertEquals(24, props.get("dureeMois").asInt());
        assertFalse(props.get("parametreValide").asBoolean(), "la durée dans l'échelon reste non validée");
        send(HttpMethod.GET, "/api/v1/rh/avancement/propositions", tAgent, null).andExpect(status().isForbidden());
        send(HttpMethod.POST, "/api/v1/rh/avancement/" + pAgent + "/echelon", tAgent, Map.of("dateEffet", "2026-01-01", "decisionRef", "D1")).andExpect(status().isForbidden());
        send(HttpMethod.POST, "/api/v1/rh/avancement/" + pAgent + "/echelon", tRh, Map.of("dateEffet", "2026-01-01")).andExpect(status().isUnprocessableEntity());       // décision obligatoire
        send(HttpMethod.POST, "/api/v1/rh/avancement/" + pAgent + "/echelon", tRh, Map.of("dateEffet", "2021-01-01", "decisionRef", "D0")).andExpect(status().isUnprocessableEntity()); // antérieure à l'échelon actuel
        send(HttpMethod.POST, "/api/v1/rh/avancement/" + pAgent + "/echelon", tRh, Map.of("dateEffet", "2026-01-01", "decisionRef", "Décision n° 12/2026")).andExpect(status().isOk());
        assertEquals("3", jdbc.queryForObject("SELECT echelon FROM personnel WHERE id = ?", String.class, pAgent));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM historique_carriere WHERE personnel_id = ? AND type_evenement = 'echelon' AND ancienne_valeur = '2' AND nouvelle_valeur = '3' AND decision_ref = 'Décision n° 12/2026' AND cree_par IS NOT NULL", Integer.class, pAgent));
        assertFalse(json.readTree(get("/api/v1/rh/avancement/propositions", tRh, 200)).get("propositions").toString().contains(pAgent), "plus éligible après avancement");
        // dernier échelon : refusé
        jdbc.update("UPDATE personnel SET echelon = '5' WHERE id = ?", pAgent);
        send(HttpMethod.POST, "/api/v1/rh/avancement/" + pAgent + "/echelon", tRh, Map.of("dateEffet", "2026-06-01", "decisionRef", "D2")).andExpect(status().isUnprocessableEntity());
        // l'agent voit sa carrière, pas celle des autres
        assertEquals(1, json.readTree(get("/api/v1/me/carriere", tAgent, 200)).size());
        assertEquals(0, json.readTree(get("/api/v1/me/carriere", tAutre, 200)).size());
    }

    @Test void examensGeresParLaRhEtVisiblesParTous() throws Exception {
        send(HttpMethod.POST, "/api/v1/rh/examens", tAgent, Map.of("dateExamen", "2026-12-01", "nbPostes", 3)).andExpect(status().isForbidden());
        send(HttpMethod.POST, "/api/v1/rh/examens", tRh, Map.of("dateExamen", "2026-12-01", "nbPostes", 3, "lieu", "Rabat", "gradeCibleId", grade)).andExpect(status().isCreated());
        send(HttpMethod.POST, "/api/v1/rh/examens", tRh, Map.of("nbPostes", 3)).andExpect(status().isUnprocessableEntity());
        JsonNode l = json.readTree(get("/api/v1/examens", tAgent, 200));
        assertEquals(1, l.size()); assertEquals("Rabat", l.get(0).get("lieu").asText()); assertEquals("Administrateur", l.get(0).get("gradeFr").asText());
        send(HttpMethod.PUT, "/api/v1/rh/examens/" + l.get(0).get("id").asText(), tRh, Map.of("resultatsFinal", "Liste publiée", "lieu", "Rabat")).andExpect(status().isOk());
        assertEquals("Liste publiée", json.readTree(get("/api/v1/examens", tAgent, 200)).get(0).get("resultatsFinal").asText());
    }
}
