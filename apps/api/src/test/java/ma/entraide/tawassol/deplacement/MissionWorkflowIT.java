package ma.entraide.tawassol.deplacement;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MissionWorkflowIT extends IntegrationTestBase {
    String deleg, dest, struct, pAgent, pResp, pCell, pPaie, pAutre;
    String tAgent, tResp, tCell, tPaie, tAutre;
    @Autowired VerificationService verif;

    @BeforeEach void fixture() throws Exception {
        deleg = data.delegation("MW"); dest = data.delegation("MWD");
        struct = data.structure(deleg, null, "MWS");
        String autreStruct = data.structure(data.delegation("MWX"), null, "MWXS");
        pAgent = data.personnel(struct, "Agent"); pResp = data.personnel(struct, "Resp"); pCell = data.personnel(struct, "Cell"); pPaie = data.personnel(struct, "Paie"); pAutre = data.personnel(autreStruct, "Autre");
        jdbc.update("UPDATE personnel SET echelle = 7 WHERE id IN (?,?,?,?,?)", pAgent, pResp, pCell, pPaie, pAutre);
        data.user("ag", "agent", pAgent, false);
        data.user("resp", "responsable", pResp, false);
        String uc = data.user("cell", "rh", pCell, false), up = data.user("paie", "rh", pPaie, false);
        jdbc.update("UPDATE utilisateur SET role_id = (SELECT id FROM role WHERE code = 'cellule_deplacements') WHERE id = ?", uc);
        jdbc.update("UPDATE utilisateur SET role_id = (SELECT id FROM role WHERE code = 'cellule_paie') WHERE id = ?", up);
        data.user("autre", "responsable", pAutre, false);
        data.perimeter(pResp, "structure", struct, LocalDate.of(2020, 1, 1), null, true);
        tAgent = bearer("ag"); tResp = bearer("resp"); tCell = bearer("cell"); tPaie = bearer("paie"); tAutre = bearer("autre");
    }

    Map<String, Object> req(String depart, String retour, String transport) {
        Map<String, Object> b = new HashMap<>();
        b.put("objet", "Visite de terrain"); b.put("arrivalDelegationId", dest); b.put("departDelegationId", deleg);
        b.put("moyenTransportId", jdbc.queryForObject("SELECT id FROM moyen_transport WHERE code = ?", String.class, transport));
        b.put("dateDepart", depart); b.put("dateRetour", retour);
        return b;
    }

    JsonNode call(String path, String token, Object body, int statut) throws Exception {
        var r = send(HttpMethod.POST, path, token, body).andExpect(status().is(statut));
        String s = r.andReturn().getResponse().getContentAsString();
        return s.isBlank() ? null : json.readTree(s);
    }

    String creer(String token, String depart, String retour) throws Exception {
        return call("/api/v1/missions", token, req(depart, retour, "voiture_de_service"), 201).get("id").asText();
    }

    @Test void circuitCompletAvecDeuxMomentsDeNumerotation() throws Exception {
        String id = creer(tAgent, "2025-03-10T08:00:00", "2025-03-12T20:00:00");
        assertEquals("brouillon", jdbc.queryForObject("SELECT statut FROM ordre_mission WHERE id = ?", String.class, id));
        assertNull(jdbc.queryForObject("SELECT numero_ordm FROM ordre_mission WHERE id = ?", String.class, id), "aucun numéro au brouillon");
        call("/api/v1/missions/" + id + "/soumettre", tAgent, null, 200);

        // l'agent ne valide pas sa propre mission ; un responsable hors périmètre ne la voit pas ; la cellule ne fait pas le niveau 1
        call("/api/v1/missions/" + id + "/valider-niveau1", tAgent, null, 403);
        call("/api/v1/missions/" + id + "/valider-niveau1", tAutre, null, 404);
        call("/api/v1/missions/" + id + "/valider-niveau1", tCell, null, 403);
        call("/api/v1/missions/" + id + "/approuver", tCell, null, 422);   // pas encore validée par le responsable
        call("/api/v1/missions/" + id + "/valider-niveau1", tResp, null, 200);
        call("/api/v1/missions/" + id + "/approuver", tResp, null, 403);   // le responsable n'approuve pas

        JsonNode ok = call("/api/v1/missions/" + id + "/approuver", tCell, null, 200);
        assertEquals("approuvee", ok.get("statut").asText());
        assertTrue(ok.get("numeroOrdm").asText().matches("MW-2025-0000\\d"), ok.get("numeroOrdm").asText());
        assertFalse(ok.has("numeroEtat"), "pas de numéro d'état à l'approbation");

        // finalisation après le retour : dates réelles, repas, montants figés, numéro d'état
        Map<String, Object> reel = new HashMap<>();
        reel.put("dateDepart", "2025-03-10T08:00:00"); reel.put("dateRetour", "2025-03-12T20:00:00");
        call("/api/v1/missions/" + id + "/finaliser", tAgent, reel, 403);
        JsonNode fin = call("/api/v1/missions/" + id + "/finaliser", tCell, reel, 200);
        assertEquals("finalisee", fin.get("statut").asText());
        assertEquals("EMW-2025-00001", fin.get("numeroEtat").asText());
        assertTrue(fin.get("montantTotal").decimalValue().signum() > 0);
        int repas = fin.get("repasTaux1").asInt() + fin.get("repasTaux2").asInt();
        assertTrue(repas > 0);
        assertEquals(0, fin.get("montantIndemnites").decimalValue().compareTo(fin.get("indemniteTaux1").decimalValue().multiply(java.math.BigDecimal.valueOf(fin.get("repasTaux1").asInt())).add(fin.get("indemniteTaux2").decimalValue().multiply(java.math.BigDecimal.valueOf(fin.get("repasTaux2").asInt())))));

        // paiement : seule la cellule de paie, pas la cellule des déplacements
        call("/api/v1/missions/" + id + "/payer", tCell, null, 403);
        JsonNode paye = call("/api/v1/missions/" + id + "/payer", tPaie, null, 200);
        assertEquals("payee", paye.get("statut").asText());
        call("/api/v1/missions/" + id + "/annuler", tCell, null, 422);   // plus annulable après paiement
        JsonNode hist = json.readTree(send(HttpMethod.GET, "/api/v1/missions/" + id, tAgent, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("historique");
        assertEquals(6, hist.size()); assertEquals("payee", hist.get(5).get("vers").asText());

        // montants figés : une modification des barèmes ensuite ne les change pas
        jdbc.update("UPDATE bareme_indemnite SET taux_repas_1 = 999 WHERE echelle = 7");
        JsonNode relu = json.readTree(send(HttpMethod.GET, "/api/v1/missions/" + id, tAgent, null).andReturn().getResponse().getContentAsString());
        assertEquals(0, fin.get("montantTotal").decimalValue().compareTo(relu.get("montantTotal").decimalValue()));
        jdbc.update("UPDATE bareme_indemnite SET taux_repas_1 = 40 WHERE echelle = 7");
    }

    @Test void uneMissionAnnuleeNeConsommeAucunNumeroDEtat() throws Exception {
        String a = creer(tAgent, "2025-04-01T08:00:00", "2025-04-02T20:00:00");
        for (String p : new String[]{"soumettre:tAgent", "valider-niveau1:tResp", "approuver:tCell"}) {
            String[] x = p.split(":"); call("/api/v1/missions/" + a + "/" + x[0], x[1].equals("tAgent") ? tAgent : x[1].equals("tResp") ? tResp : tCell, null, 200);
        }
        JsonNode annulee = call("/api/v1/missions/" + a + "/annuler", tCell, null, 200);
        assertEquals("annulee", annulee.get("statut").asText());
        assertNotNull(jdbc.queryForObject("SELECT numero_ordm FROM ordre_mission WHERE id = ?", String.class, a), "le numéro d'ordre émis est conservé");
        assertNull(jdbc.queryForObject("SELECT numero_etat FROM ordre_mission WHERE id = ?", String.class, a));

        String b = creer(tAgent, "2025-05-01T08:00:00", "2025-05-02T20:00:00");
        for (String p : new String[]{"soumettre:tAgent", "valider-niveau1:tResp", "approuver:tCell"}) {
            String[] x = p.split(":"); call("/api/v1/missions/" + b + "/" + x[0], x[1].equals("tAgent") ? tAgent : x[1].equals("tResp") ? tResp : tCell, null, 200);
        }
        Map<String, Object> reel = Map.of("dateRetour", "2025-05-02T20:00:00");
        JsonNode fin = call("/api/v1/missions/" + b + "/finaliser", tCell, reel, 200);
        assertEquals("EMW-2025-00001", fin.get("numeroEtat").asText(), "la séquence des états est sans trou");
        assertEquals("MW-2025-00002", fin.get("numeroOrdm").asText());
    }

    @Test void controlesDeSaisieEtDeChevauchement() throws Exception {
        call("/api/v1/missions", tAgent, req("2025-06-10T08:00:00", "2025-06-09T08:00:00", "voiture_de_service"), 422);
        String a = creer(tAgent, "2025-06-01T08:00:00", "2025-06-05T18:00:00");
        call("/api/v1/missions/" + a + "/soumettre", tAgent, null, 200);
        String b = creer(tAgent, "2025-06-03T08:00:00", "2025-06-04T18:00:00");
        JsonNode e = call("/api/v1/missions/" + b + "/soumettre", tAgent, null, 422);
        assertEquals("MISSION_CHEVAUCHEMENT", e.get("code").asText());
        // seule une saisie dans son périmètre est possible pour un autre agent
        Map<String, Object> pourAutrui = req("2025-07-01T08:00:00", "2025-07-02T08:00:00", "voiture_de_service"); pourAutrui.put("personnelId", pAgent);
        call("/api/v1/missions", tResp, pourAutrui, 201);
        call("/api/v1/missions", tAutre, pourAutrui, 404);
        call("/api/v1/missions", tAgent, Map.of("objet", "x", "arrivalDelegationId", dest, "dateDepart", "2025-07-01T08:00:00", "personnelId", pCell), 403);
    }

    @Test void kilometriqueVoiturePriveeEtRetourFutur() throws Exception {
        Map<String, Object> r = req("2025-08-01T08:00:00", "2025-08-01T20:00:00", "voiture_privee");
        r.put("puissanceFiscaleId", jdbc.queryForObject("SELECT id FROM puissance_fiscale WHERE code = 'six_or_less'", String.class));
        r.put("distanceKm", 100); r.put("nombreTrajets", 2);
        String id = call("/api/v1/missions", tAgent, r, 201).get("id").asText();
        for (String p : new String[]{"soumettre:a", "valider-niveau1:r", "approuver:c"}) {
            String[] x = p.split(":"); call("/api/v1/missions/" + id + "/" + x[0], x[1].equals("a") ? tAgent : x[1].equals("r") ? tResp : tCell, null, 200);
        }
        JsonNode fin = call("/api/v1/missions/" + id + "/finaliser", tCell, Map.of("dateRetour", "2025-08-01T20:00:00"), 200);
        assertEquals(0, fin.get("montantKm").decimalValue().compareTo(new java.math.BigDecimal("240.00")));
        String futur = creer(tAgent, "2025-09-01T08:00:00", "2025-09-02T08:00:00");
        for (String p : new String[]{"soumettre:a", "valider-niveau1:r", "approuver:c"}) {
            String[] x = p.split(":"); call("/api/v1/missions/" + futur + "/" + x[0], x[1].equals("a") ? tAgent : x[1].equals("r") ? tResp : tCell, null, 200);
        }
        assertEquals("RETOUR_FUTUR", call("/api/v1/missions/" + futur + "/finaliser", tCell, Map.of("dateRetour", "2099-01-01T08:00:00"), 422).get("code").asText());
    }

    byte[] pdf(String token, String path, int statut) throws Exception {
        var r = send(HttpMethod.GET, path, token, null).andExpect(status().is(statut)).andReturn().getResponse();
        return r.getContentAsByteArray();
    }

    @Test void documentsOfficielsEtVerificationParQr() throws Exception {
        // signataire habilité à la date de l'ordre, avec son nom (Suivi RH ne conserve que la qualité)
        jdbc.update("INSERT INTO signataire_autorisation (id, portee, delegation_id, nom_fr, nom_ar, qualite_fr, qualite_ar, numero_autorisation, date_autorisation, date_debut) VALUES (UUID(),'delegation',?,?,?,?,?,?,?,?)",
                deleg, "Nom Signataire", "اسم الموقع", "le délégué provincial", "المندوب الإقليمي", "61", "2022-06-02", "2022-06-02");
        Map<String, Object> r = req("2025-10-01T08:00:00", "2025-10-03T19:00:00", "voiture_privee");
        r.put("puissanceFiscaleId", jdbc.queryForObject("SELECT id FROM puissance_fiscale WHERE code = 'seven'", String.class));
        r.put("distanceKm", 120); r.put("nombreTrajets", 2); r.put("vehiculeNom", "Dacia Logan"); r.put("vehiculeImmatriculation", "12345-A-6");
        String id = call("/api/v1/missions", tAgent, r, 201).get("id").asText();
        pdf(tAgent, "/api/v1/missions/" + id + "/documents/ordre", 403);   // pas d'ordre avant l'approbation
        call("/api/v1/missions/" + id + "/soumettre", tAgent, null, 200);
        call("/api/v1/missions/" + id + "/valider-niveau1", tResp, null, 200);
        call("/api/v1/missions/" + id + "/approuver", tCell, null, 200);

        byte[] ordre = pdf(tAgent, "/api/v1/missions/" + id + "/documents/ordre", 200);
        assertEquals("%PDF", new String(ordre, 0, 4));
        pdf(tAgent, "/api/v1/missions/" + id + "/documents/etat", 422);     // pas d'état avant la finalisation
        pdf(tAutre, "/api/v1/missions/" + id + "/documents/ordre", 404);    // hors périmètre
        java.nio.file.Files.write(java.nio.file.Path.of("target/exemple-ordre-de-mission.pdf"), ordre);

        JsonNode fin = call("/api/v1/missions/" + id + "/finaliser", tCell, Map.of("dateRetour", "2025-10-03T19:00:00"), 200);
        byte[] etat = pdf(tAgent, "/api/v1/missions/" + id + "/documents/etat", 200);
        byte[] km = pdf(tAgent, "/api/v1/missions/" + id + "/documents/etat-km", 200);
        java.nio.file.Files.write(java.nio.file.Path.of("target/exemple-etat.pdf"), etat);
        java.nio.file.Files.write(java.nio.file.Path.of("target/exemple-etat-km.pdf"), km);
        assertTrue(etat.length > 5000 && km.length > 5000);
        pdf(tAgent, "/api/v1/missions/" + id + "/documents/inconnu", 404);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'MISSION_DOCUMENT_ORDRE' AND apres LIKE '%sha256%'", Integer.class) >= 1, "l'empreinte du PDF émis est journalisée");

        // vérification publique : accessible sans connexion, sans donnée personnelle
        String numero = fin.get("numeroOrdm").asText();
        String jeton = verif.jeton(id, "ordre", numero);
        String ok = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/verification/ordre/" + id + "/" + jeton)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(ok.contains(numero)); assertTrue(ok.contains("\"valide\":true"));
        assertFalse(ok.contains("Agent") || ok.contains("matricule"), "aucune donnée personnelle");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/verification/ordre/" + id + "/" + jeton + "x")).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/verification/etat/" + id + "/" + jeton)).andExpect(status().isNotFound());
        // une mission annulée apparaît comme non valide
        jdbc.update("UPDATE ordre_mission SET statut = 'annulee' WHERE id = ?", id);
        assertTrue(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/verification/ordre/" + id + "/" + jeton)).andReturn().getResponse().getContentAsString().contains("\"valide\":false"));
    }

    @Test void exportsEtAnalytiqueRespectentLePerimetre() throws Exception {
        Map<String, Object> r = req("2025-11-03T08:00:00", "2025-11-04T20:00:00", "voiture_de_service");
        r.put("objet", "=HYPERLINK(\"http://x\")");
        String id = call("/api/v1/missions", tAgent, r, 201).get("id").asText();
        for (String p : new String[]{"soumettre:a", "valider-niveau1:r", "approuver:c"}) { String[] x = p.split(":"); call("/api/v1/missions/" + id + "/" + x[0], x[1].equals("a") ? tAgent : x[1].equals("r") ? tResp : tCell, null, 200); }
        call("/api/v1/missions/" + id + "/finaliser", tCell, Map.of("dateRetour", "2025-11-04T20:00:00"), 200);

        byte[] xl = pdf(tCell, "/api/v1/missions/export?annee=2025", 200);
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xl))) {
            var sh = wb.getSheetAt(0);
            assertEquals(2, sh.getPhysicalNumberOfRows());
            assertEquals("'=HYPERLINK(\"http://x\")", sh.getRow(1).getCell(11).getStringCellValue(), "formule neutralisée");
            assertEquals("finalisee", sh.getRow(1).getCell(4).getStringCellValue());
        }
        // le responsable d'une autre délégation peut exporter mais ne voit aucune de ces missions (périmètre)
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(pdf(tAutre, "/api/v1/missions/export?annee=2025", 200)))) {
            assertEquals(1, wb.getSheetAt(0).getPhysicalNumberOfRows(), "en-tête seulement");
        }
        pdf(tPaie, "/api/v1/missions/export?annee=2025", 200);
        byte[] glob = pdf(tCell, "/api/v1/missions/etat-global?annee=2025", 200);
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(glob))) {
            assertTrue(wb.getSheetAt(0).getPhysicalNumberOfRows() >= 3);
        }
        JsonNode an = json.readTree(send(HttpMethod.GET, "/api/v1/missions/analytique?annee=2025", tCell, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("finalisee", an.get("parStatut").get(0).get("statut").asText());
        assertEquals(1, an.get("parStatut").get(0).get("nombre").asInt());
        send(HttpMethod.GET, "/api/v1/missions/analytique?annee=2025", tAgent, null).andExpect(status().isForbidden());
    }
}
