package ma.entraide.tawassol.rh;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
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

/** Espace RH : accès réservé, traitement des demandes, dossiers agents, notation, réclamations, publications. */
class RhIT extends IntegrationTestBase {
    String d1, s1, s2, pA, pResp, pRh, pRh2, tA, tResp, tRh, tRh2, tAdmin;
    int annee = LocalDate.now().getYear();

    @BeforeEach
    void fixture() throws Exception {
        d1 = data.delegation("RH1");
        s1 = data.structure(d1, null, "RHS1"); s2 = data.structure(d1, null, "RHS2");
        pA = data.personnel(s1, "Rhagent"); pResp = data.personnel(s1, "Rhresp"); pRh = data.personnel(s2, "Rhun"); pRh2 = data.personnel(s2, "Rhdeux");
        data.user("rha", "agent", pA, false); data.user("rhresp", "responsable", pResp, false); data.user("rh1", "rh", pRh, false); data.user("rh2", "rh", pRh2, false); data.user("rhadmin", "admin", null, false);
        data.perimeter(pResp, "structure", s1, LocalDate.of(2020, 1, 1), null, true);
        tA = bearer("rha"); tResp = bearer("rhresp"); tRh = bearer("rh1"); tRh2 = bearer("rh2"); tAdmin = bearer("rhadmin");
    }

    String leave(String token, String debut, String fin) throws Exception {
        String id = idOf(send(HttpMethod.POST, "/api/v1/demandes", token, Map.of("typeCode", "CONGE_ANNUEL", "dateDebut", debut, "dateFin", fin)).andExpect(status().isCreated()));
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/soumettre", token, null).andExpect(status().isOk());
        return id;
    }

    // ---- accès ----

    @Test void lEspaceRhEstInterditAuxAutresProfils() throws Exception {
        for (String path : new String[]{"/api/v1/rh/tableau-de-bord", "/api/v1/rh/demandes", "/api/v1/rh/grades", "/api/v1/rh/imports/lots", "/api/v1/rh/reclamations", "/api/v1/rh/annonces", "/api/v1/rh/notation/campagnes"}) {
            send(HttpMethod.GET, path, tA, null).andExpect(status().isForbidden());
            send(HttpMethod.GET, path, tResp, null).andExpect(status().isForbidden());
            send(HttpMethod.GET, path, tAdmin, null).andExpect(status().isForbidden());
            send(HttpMethod.GET, path, tRh, null).andExpect(status().isOk());
        }
        send(HttpMethod.GET, "/api/v1/rh/tableau-de-bord", null, null).andExpect(status().isUnauthorized());
        send(HttpMethod.POST, "/api/v1/rh/personnel", tResp, Map.of("matricule", "9")).andExpect(status().isForbidden());
    }

    // ---- traitement des demandes ----

    @Test void laRhNeTraiteQueCeQuiLuiEstParvenu() throws Exception {
        String id = leave(tA, "2026-11-16", "2026-11-17");
        send(HttpMethod.GET, "/api/v1/rh/demandes", tRh, null).andExpect(jsonPath("$.total").value(0));
        send(HttpMethod.POST, "/api/v1/rh/demandes/" + id + "/approuver", tRh, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + id + "/valider", tResp, null).andExpect(status().isOk());
        send(HttpMethod.GET, "/api/v1/rh/demandes", tRh, null).andExpect(jsonPath("$.content[*].id", hasItem(id))).andExpect(jsonPath("$.content[0].circuit").value("responsable"));
    }

    @Test void approbationDunCongeDecompteLeSolde_etNotifieLAgent() throws Exception {
        String id = leave(tA, "2026-11-16", "2026-11-20");
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + id + "/valider", tResp, null).andExpect(status().isOk());
        send(HttpMethod.POST, "/api/v1/rh/demandes/" + id + "/approuver", tRh, Map.of("commentaire", "Accordé")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("approuvee"));
        send(HttpMethod.POST, "/api/v1/rh/demandes/" + id + "/approuver", tRh, null).andExpect(status().isOk());
        assertEquals(0, new BigDecimal("4").compareTo(jdbc.queryForObject("SELECT pris FROM solde_conge WHERE personnel_id = ? AND annee = 2026", BigDecimal.class, pA)), "le solde n'est décompté qu'une fois");
        assertEquals("rh1", jdbc.queryForObject("SELECT login FROM utilisateur WHERE id = (SELECT traite_par FROM demande WHERE id = ?)", String.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'DEMANDE_APPROUVEE' AND utilisateur_id = (SELECT id FROM utilisateur WHERE login = 'rha')", Integer.class));
        send(HttpMethod.GET, "/api/v1/me/solde-conge?annee=2026", tA, null).andExpect(jsonPath("$.pris").value(4.0));
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/annuler", tA, null).andExpect(status().isConflict());
    }

    @Test void rejetAvecMotifObligatoire() throws Exception {
        String id = leave(tA, "2026-11-16", "2026-11-17");
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + id + "/valider", tResp, null).andExpect(status().isOk());
        send(HttpMethod.POST, "/api/v1/rh/demandes/" + id + "/rejeter", tRh, null).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
        send(HttpMethod.POST, "/api/v1/rh/demandes/" + id + "/rejeter", tRh, Map.of("commentaire", "Quota atteint")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("rejetee"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM solde_conge WHERE personnel_id = ?", Integer.class, pA));
        send(HttpMethod.GET, "/api/v1/demandes/" + id, tA, null).andExpect(jsonPath("$.commentaireRh").value("Quota atteint"));
    }

    @Test void uneAttestationVaDirectementALaRh_etLaRhNeTraitePasSaPropreDemande() throws Exception {
        String att = idOf(send(HttpMethod.POST, "/api/v1/demandes", tA, Map.of("typeCode", "ATTESTATION", "donnees", Map.of("typeAttestation", "travail"))).andExpect(status().isCreated()));
        send(HttpMethod.POST, "/api/v1/demandes/" + att + "/soumettre", tA, null).andExpect(status().isOk());
        send(HttpMethod.GET, "/api/v1/rh/demandes", tRh, null).andExpect(jsonPath("$.content[0].circuit").value("direct"));
        send(HttpMethod.POST, "/api/v1/rh/demandes/" + att + "/approuver", tRh, null).andExpect(status().isOk());
        String own = leave(tRh, "2026-12-07", "2026-12-08");
        assertNull(jdbc.queryForObject("SELECT responsable_personnel_id FROM demande WHERE id = ?", String.class, own));
        send(HttpMethod.POST, "/api/v1/rh/demandes/" + own + "/approuver", tRh, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SELF_VALIDATION_FORBIDDEN"));
        send(HttpMethod.GET, "/api/v1/rh/demandes", tRh, null).andExpect(jsonPath("$.content[*].id", not(hasItem(own))));
        send(HttpMethod.POST, "/api/v1/rh/demandes/" + own + "/approuver", tRh2, null).andExpect(status().isOk());
    }

    @Test void lesOrdresDeMissionPassentParLeModuleMissions() throws Exception {
        String transport = jdbc.queryForObject("SELECT id FROM moyen_transport WHERE code = 'voiture_de_service'", String.class);
        String id = idOf(send(HttpMethod.POST, "/api/v1/demandes", tA, Map.of("typeCode", "ORDRE_MISSION", "dateDebut", "2026-11-25", "dateFin", "2026-11-26",
                "donnees", Map.of("departDelegationId", d1, "arrivalDelegationId", d1, "moyenTransportId", transport))).andExpect(status().isCreated()));
        send(HttpMethod.POST, "/api/v1/demandes/" + id + "/soumettre", tA, null).andExpect(status().isOk());
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + id + "/valider", tResp, null).andExpect(status().isOk());
        send(HttpMethod.POST, "/api/v1/rh/demandes/" + id + "/approuver", tRh, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USE_MISSION_MODULE"));
    }

    @Test void leDetailRhMontreLesPiecesEtLeSolde() throws Exception {
        String id = leave(tA, "2026-11-16", "2026-11-17");
        send(HttpMethod.POST, "/api/v1/validation/demandes/" + id + "/valider", tResp, Map.of("commentaire", "Accord")).andExpect(status().isOk());
        send(HttpMethod.GET, "/api/v1/rh/demandes/" + id, tRh, null).andExpect(status().isOk()).andExpect(jsonPath("$.traitable").value(true)).andExpect(jsonPath("$.solde.suffisant").value(true))
                .andExpect(jsonPath("$.commentaireResponsable").value("Accord")).andExpect(jsonPath("$.agent.matricule").exists());
    }

    // ---- dossiers agents ----

    @Test void creationModificationEtHistoriqueDeCarriere() throws Exception {
        String grade = jdbc.queryForObject("SELECT id FROM grade LIMIT 1", String.class);
        String id = idOf(send(HttpMethod.POST, "/api/v1/rh/personnel", tRh, Map.of("matricule", "55001", "nomFr", "Nouveau", "prenomFr", "Agent", "nomAr", "جديد", "prenomAr", "موظف", "structureId", s1,
                "dateRecrutement", "2026-09-01", "email", "nouveau@example.ma", "cin", "AB123456")));
        send(HttpMethod.POST, "/api/v1/rh/personnel", tRh, Map.of("matricule", "55001", "nomFr", "X", "prenomFr", "Y", "nomAr", "س", "prenomAr", "ص", "structureId", s1, "dateRecrutement", "2026-09-01")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_MATRICULE"));
        send(HttpMethod.POST, "/api/v1/rh/personnel", tRh, Map.of("matricule", "55002", "nomFr", "X", "prenomFr", "Y", "nomAr", "س", "prenomAr", "ص", "structureId", s1, "dateRecrutement", "2026-09-01", "cin", "AB123456")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CIN"));
        send(HttpMethod.PUT, "/api/v1/rh/personnel/" + id, tRh, Map.of("structureId", s2, "gradeId", grade, "echelon", "3", "echelle", 8, "decisionRef", "DEC-2026-044", "dateEffet", "2026-10-01")).andExpect(status().isOk());
        JsonNode car = json.readTree(send(HttpMethod.GET, "/api/v1/rh/personnel/" + id + "/carriere", tRh, null).andReturn().getResponse().getContentAsString());
        assertEquals(4, car.size(), car.toString());
        assertTrue(car.toString().contains("RHS2") && car.toString().contains("DEC-2026-044"));
        String audit = jdbc.queryForObject("SELECT avant FROM journal_audit WHERE action = 'PERSONNEL_MODIFIE' LIMIT 1", String.class);
        assertFalse(audit.contains("AB123456") && audit.contains("nouveau@"), "valeurs sensibles absentes de l'audit");
        send(HttpMethod.PUT, "/api/v1/rh/personnel/" + id, tRh, Map.of("email", "pas-un-email")).andExpect(status().isUnprocessableEntity());
        send(HttpMethod.DELETE, "/api/v1/rh/personnel/" + id, tRh, null).andExpect(status().isNoContent());
        send(HttpMethod.GET, "/api/v1/personnel?q=Nouveau", tRh, null).andExpect(jsonPath("$.total").value(0));
    }

    @Test void referentielsEtDoublons() throws Exception {
        send(HttpMethod.POST, "/api/v1/rh/grades", tRh, Map.of("code", 501, "libelleFr", "Grade test", "libelleAr", "درجة", "echelle", 7)).andExpect(status().isCreated());
        send(HttpMethod.POST, "/api/v1/rh/grades", tRh, Map.of("code", 501, "libelleFr", "Autre", "libelleAr", "x")).andExpect(status().isConflict());
        String sid = idOf(send(HttpMethod.POST, "/api/v1/rh/structures", tRh, Map.of("delegationId", d1, "type", "centre", "code", "RHNEW", "nomFr", "Nouveau centre", "nomAr", "مركز جديد")).andExpect(status().isCreated()));
        send(HttpMethod.DELETE, "/api/v1/rh/structures/" + s1, tRh, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STRUCTURE_NOT_EMPTY"));
        send(HttpMethod.DELETE, "/api/v1/rh/structures/" + sid, tRh, null).andExpect(status().isNoContent());
    }

    // ---- notation ----

    @Test void campagneControleValidationPublication() throws Exception {
        send(HttpMethod.POST, "/api/v1/rh/notation/campagnes", tRh, Map.of("annee", annee, "dateOuverture", LocalDate.now().minusDays(2).toString(), "dateCloture", LocalDate.now().plusDays(20).toString())).andExpect(status().isCreated());
        send(HttpMethod.POST, "/api/v1/rh/notation/campagnes", tRh, Map.of("annee", annee, "dateOuverture", LocalDate.now().toString(), "dateCloture", LocalDate.now().toString())).andExpect(status().isConflict());
        send(HttpMethod.PUT, "/api/v1/notation/" + annee + "/agents/" + pA, tResp, Map.of("note", 15.5, "appreciation", "Bien")).andExpect(status().isOk());
        send(HttpMethod.POST, "/api/v1/notation/" + annee + "/agents/" + pA + "/soumettre", tResp, null).andExpect(status().isOk());
        send(HttpMethod.GET, "/api/v1/rh/notation/" + annee + "/notes", tRh, null).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].saisiePar").value("Prenom Rhresp"));
        send(HttpMethod.POST, "/api/v1/rh/notation/" + annee + "/notes/" + pA + "/renvoyer", tRh, null).andExpect(status().isUnprocessableEntity());
        send(HttpMethod.POST, "/api/v1/rh/notation/" + annee + "/notes/" + pA + "/renvoyer", tRh, Map.of("commentaire", "Appréciation à compléter")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("brouillon"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'NOTE_RENVOYEE' AND utilisateur_id = (SELECT id FROM utilisateur WHERE login = 'rhresp')", Integer.class));
        send(HttpMethod.POST, "/api/v1/notation/" + annee + "/agents/" + pA + "/soumettre", tResp, null).andExpect(status().isOk());
        send(HttpMethod.POST, "/api/v1/rh/notation/" + annee + "/notes/" + pA + "/valider", tRh, null).andExpect(status().isOk());
        send(HttpMethod.PUT, "/api/v1/notation/" + annee + "/agents/" + pA, tResp, Map.of("note", 20)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTE_LOCKED"));
        send(HttpMethod.GET, "/api/v1/me/notes", tA, null).andExpect(jsonPath("$.length()").value(0));
        send(HttpMethod.POST, "/api/v1/rh/notation/" + annee + "/publier", tRh, null).andExpect(jsonPath("$.publiees").value(1));
        send(HttpMethod.GET, "/api/v1/me/notes", tA, null).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].note").value(15.5));
        send(HttpMethod.GET, "/api/v1/rh/notation/" + annee + "/taux", tRh, null).andExpect(status().isOk());
    }

    @Test void laRhNeValidePasSaPropreNote_etSaisitPourUnAgentSansResponsable() throws Exception {
        jdbc.update("INSERT INTO campagne_notation (annee, date_ouverture, date_cloture) VALUES (?,?,?)", annee, LocalDate.now().minusDays(2), LocalDate.now().plusDays(20));
        send(HttpMethod.PUT, "/api/v1/rh/notation/" + annee + "/notes/" + pRh, tRh2, Map.of("note", 14)).andExpect(status().isOk());
        send(HttpMethod.POST, "/api/v1/rh/notation/" + annee + "/notes/" + pRh + "/valider", tRh, null).andExpect(status().isForbidden());
        send(HttpMethod.POST, "/api/v1/rh/notation/" + annee + "/notes/" + pRh + "/valider", tRh2, null).andExpect(status().isOk());
        send(HttpMethod.PUT, "/api/v1/rh/notation/" + annee + "/notes/" + pA, tRh, Map.of("note", 25)).andExpect(status().isUnprocessableEntity());
    }

    // ---- réclamations ----

    @Test void traiterUneReclamation() throws Exception {
        String id = json.readTree(send(HttpMethod.POST, "/api/v1/me/fiche/signaler-erreur", tA, Map.of("champ", "echelon", "description", "Échelon incorrect")).andReturn().getResponse().getContentAsString()).get("reclamationId").asText();
        send(HttpMethod.GET, "/api/v1/rh/reclamations?statut=nouvelle", tRh, null).andExpect(jsonPath("$[0].typeReclamation").value("correction_donnees"));
        send(HttpMethod.POST, "/api/v1/rh/reclamations/" + id + "/prendre", tRh, null).andExpect(jsonPath("$.statut").value("en_cours"));
        send(HttpMethod.POST, "/api/v1/rh/reclamations/" + id + "/repondre", tRh, Map.of("reponse", "", "statut", "resolue")).andExpect(status().isUnprocessableEntity());
        send(HttpMethod.POST, "/api/v1/rh/reclamations/" + id + "/repondre", tRh, Map.of("reponse", "Échelon rectifié", "statut", "resolue")).andExpect(status().isOk());
        send(HttpMethod.GET, "/api/v1/reclamations/" + id, tA, null).andExpect(jsonPath("$.statut").value("resolue")).andExpect(jsonPath("$.reponse").value("Échelon rectifié"));
        send(HttpMethod.POST, "/api/v1/rh/reclamations/" + id + "/repondre", tRh, Map.of("reponse", "x", "statut", "resolue")).andExpect(status().isConflict());
    }

    // ---- publications ----

    @Test void annonceCibleeEtExpiree() throws Exception {
        send(HttpMethod.POST, "/api/v1/rh/annonces", tRh, Map.of("titre", "Pour S1", "message", "m", "cibleType", "structure", "cibleId", s1)).andExpect(status().isCreated());
        send(HttpMethod.POST, "/api/v1/rh/annonces", tRh, Map.of("titre", "Pour S2", "message", "m", "cibleType", "structure", "cibleId", s2)).andExpect(status().isCreated());
        send(HttpMethod.POST, "/api/v1/rh/annonces", tRh, Map.of("titre", "Cible inconnue", "message", "m", "cibleType", "structure", "cibleId", UUID.randomUUID().toString())).andExpect(status().isUnprocessableEntity());
        send(HttpMethod.GET, "/api/v1/annonces", tA, null).andExpect(jsonPath("$[*].titre", hasItem("Pour S1"))).andExpect(jsonPath("$[*].titre", not(hasItem("Pour S2"))));
    }
}
