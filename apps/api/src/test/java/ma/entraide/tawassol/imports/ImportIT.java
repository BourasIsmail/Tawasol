package ma.entraide.tawassol.imports;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportIT extends IntegrationTestBase {
    String s1, pA, pB, tRh, tA, mA, mB;

    @BeforeEach
    void fixture() throws Exception {
        s1 = data.structure(data.delegation("IM1"), null, "IMS1");
        pA = data.personnel(s1, "Impa"); pB = data.personnel(s1, "Impb");
        String pRh = data.personnel(s1, "Imprh");
        data.user("impa", "agent", pA, false); data.user("imprh", "rh", pRh, false);
        mA = jdbc.queryForObject("SELECT matricule FROM personnel WHERE id = ?", String.class, pA);
        mB = jdbc.queryForObject("SELECT matricule FROM personnel WHERE id = ?", String.class, pB);
        tRh = bearer("imprh"); tA = bearer("impa");
    }

    ResultActions post(String path, String type, byte[] contenu, String nom, Map<String, String> params) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart(path).file(new MockMultipartFile("fichier", nom, "application/octet-stream", contenu));
        var req = b.param("type", type).header("Authorization", tRh);
        for (var e : params.entrySet()) req = req.param(e.getKey(), e.getValue());
        return mvc.perform(req);
    }

    byte[] csv(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test void analyseProposeLaCorrespondanceDesColonnes() throws Exception {
        post("/api/v1/rh/imports/analyse", "salaire", csv("Matricule;Net à payer;Allocations familiales;Retenue mutuelle\n" + mA + ";7842,10;300;120,50\n"), "paie.csv", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.nbLignes").value(1)).andExpect(jsonPath("$.mappingSuggere.matricule").value("Matricule"))
                .andExpect(jsonPath("$.mappingSuggere.salaire_net").value("Net à payer")).andExpect(jsonPath("$.mappingSuggere.alloc_familiale").value("Allocations familiales"))
                .andExpect(jsonPath("$.mappingSuggere.retenue_mutuelle").value("Retenue mutuelle"));
    }

    @Test void controleRapporteLesAnomaliesSansRienEcrire() throws Exception {
        String f = "Matricule;Net\n" + mA + ";7842,10\n99999;100\n" + mB + ";-5\n" + mA + ";8000\n;100\n";
        post("/api/v1/rh/imports/controle", "salaire", csv(f), "paie.csv", Map.of("annee", "2026", "mois", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rapport.total").value(5)).andExpect(jsonPath("$.rapport.valides").value(1)).andExpect(jsonPath("$.rapport.erreurs").value(4))
                .andExpect(jsonPath("$.rapport.anomalies[?(@.code=='UNKNOWN_MATRICULE')]").exists()).andExpect(jsonPath("$.rapport.anomalies[?(@.code=='NEGATIVE_AMOUNT')]").exists())
                .andExpect(jsonPath("$.rapport.anomalies[?(@.code=='DUPLICATE_IN_FILE')]").exists()).andExpect(jsonPath("$.rapport.anomalies[?(@.code=='REQUIRED')]").exists());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM salaire", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM import_lot", Integer.class));
    }

    @Test void importEnBrouillonPuisPublicationVisibleParLAgent() throws Exception {
        String f = "Matricule;Net;Allocations familiales;Retenue mutuelle\n" + mA + ";7 842,10;300;120,50\n" + mB + ";6000;0;0\n99999;1;0;0\n";
        JsonNode lot = json.readTree(post("/api/v1/rh/imports", "salaire", csv(f), "paie-oct.csv", Map.of("annee", "2026", "mois", "10")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("brouillon")).andExpect(jsonPath("$.nbLignes").value(2)).andExpect(jsonPath("$.nbErreurs").value(1)).andReturn().getResponse().getContentAsString());
        assertTrue(lot.get("numero").asText().matches("LOT-\\d{4}-\\d{6}"));
        String id = lot.get("id").asText();
        send(HttpMethod.GET, "/api/v1/me/bulletins", tA, null).andExpect(jsonPath("$.length()").value(0));
        send(HttpMethod.POST, "/api/v1/rh/imports/lots/" + id + "/publier", tRh, null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("publie"));
        send(HttpMethod.POST, "/api/v1/rh/imports/lots/" + id + "/publier", tRh, null).andExpect(status().isConflict());
        send(HttpMethod.GET, "/api/v1/me/bulletins", tA, null).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].salaireNet").value(7842.10)).andExpect(jsonPath("$[0].retenueMutuelle").value(120.50));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'BULLETIN_DISPONIBLE' AND utilisateur_id = (SELECT id FROM utilisateur WHERE login = 'impa')", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'IMPORT_LOT_PUBLIE'", Integer.class));
        send(HttpMethod.POST, "/api/v1/rh/imports/lots/" + id + "/annuler", tRh, null).andExpect(status().isConflict());
    }

    @Test void memeFichierDeuxFoisEtPeriodeDejaEnBrouillon() throws Exception {
        String f = "Matricule;Net\n" + mA + ";7000\n";
        post("/api/v1/rh/imports", "salaire", csv(f), "a.csv", Map.of("annee", "2026", "mois", "9")).andExpect(status().isCreated());
        post("/api/v1/rh/imports", "salaire", csv(f), "a.csv", Map.of("annee", "2026", "mois", "9")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_FILE"));
        String g = "Matricule;Net\n" + mA + ";7100\n";
        post("/api/v1/rh/imports", "salaire", csv(g), "b.csv", Map.of("annee", "2026", "mois", "9")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_HAS_DRAFT"));
        post("/api/v1/rh/imports", "salaire", csv(g), "b.csv", Map.of("annee", "2026", "mois", "9", "remplacerBrouillon", "true")).andExpect(status().isCreated());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM salaire WHERE annee = 2026 AND mois = 9", Integer.class));
        assertEquals(new java.math.BigDecimal("7100.00"), jdbc.queryForObject("SELECT salaire_net FROM salaire WHERE annee = 2026 AND mois = 9", java.math.BigDecimal.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM import_lot WHERE statut = 'annule'", Integer.class));
    }

    @Test void unMoisPublieNEstPlusReimportable() throws Exception {
        JsonNode lot = json.readTree(post("/api/v1/rh/imports", "salaire", csv("Matricule;Net\n" + mA + ";7000\n"), "a.csv", Map.of("annee", "2026", "mois", "8")).andReturn().getResponse().getContentAsString());
        send(HttpMethod.POST, "/api/v1/rh/imports/lots/" + lot.get("id").asText() + "/publier", tRh, null).andExpect(status().isOk());
        post("/api/v1/rh/imports/controle", "salaire", csv("Matricule;Net\n" + mA + ";9999\n"), "c.csv", Map.of("annee", "2026", "mois", "8")).andExpect(jsonPath("$.rapport.anomalies[?(@.code=='ALREADY_PUBLISHED')]").exists());
    }

    @Test void annulerUnLotEnBrouillon() throws Exception {
        String id = json.readTree(post("/api/v1/rh/imports", "salaire", csv("Matricule;Net\n" + mA + ";7000\n"), "a.csv", Map.of("annee", "2026", "mois", "7")).andReturn().getResponse().getContentAsString()).get("id").asText();
        send(HttpMethod.POST, "/api/v1/rh/imports/lots/" + id + "/annuler", tRh, null).andExpect(jsonPath("$.statut").value("annule"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM salaire", Integer.class));
    }

    @Test void fichierExcelAvecCellulesNumeriquesEtDates() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sh = wb.createSheet();
            Row h = sh.createRow(0);
            String[] cols = {"Matricule", "Type", "Montant brut", "IR", "Montant net", "Date"};
            for (int i = 0; i < cols.length; i++) h.createCell(i).setCellValue(cols[i]);
            Row r = sh.createRow(1);
            r.createCell(0).setCellValue(mA); r.createCell(1).setCellValue("gratification"); r.createCell(2).setCellValue(4000); r.createCell(3).setCellValue(560); r.createCell(4).setCellValue(3440);
            CellStyle st = wb.createCellStyle(); st.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            var dc = r.createCell(5); dc.setCellValue(LocalDate.of(2026, 6, 15)); dc.setCellStyle(st);
            wb.write(out);
        }
        post("/api/v1/rh/imports", "prime", out.toByteArray(), "primes.xlsx", Map.of()).andExpect(status().isCreated()).andExpect(jsonPath("$.nbLignes").value(1));
        assertEquals(LocalDate.of(2026, 6, 15), jdbc.queryForObject("SELECT date_prime FROM prime", LocalDate.class));
        assertEquals(0, new java.math.BigDecimal("3440").compareTo(jdbc.queryForObject("SELECT montant_net FROM prime", java.math.BigDecimal.class)));
    }

    @Test void rubriquesDuBulletinViaLeModele() throws Exception {
        Map<String, Object> mapping = Map.of("colonnes", Map.of("matricule", "Mat", "salaire_net", "Net"), "rubriques", java.util.List.of(Map.of("colonne", "IR", "code", "IR", "libelleFr", "Impôt sur le revenu", "libelleAr", "الضريبة على الدخل", "sens", "retenue")));
        String modele = idOf(send(HttpMethod.POST, "/api/v1/rh/imports/modeles", tRh, Map.of("type", "salaire", "nom", "Paie v1", "mapping", mapping)).andExpect(status().isCreated()));
        send(HttpMethod.POST, "/api/v1/rh/imports/modeles", tRh, Map.of("type", "salaire", "nom", "Paie v1", "mapping", mapping)).andExpect(status().isConflict());
        String id = json.readTree(post("/api/v1/rh/imports", "salaire", csv("Mat;Net;IR\n" + mA + ";7000;640\n"), "p.csv", Map.of("annee", "2026", "mois", "6", "modeleId", modele)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        send(HttpMethod.POST, "/api/v1/rh/imports/lots/" + id + "/publier", tRh, null).andExpect(status().isOk());
        String bulletin = json.readTree(send(HttpMethod.GET, "/api/v1/me/bulletins", tA, null).andReturn().getResponse().getContentAsString()).get(0).get("id").asText();
        send(HttpMethod.GET, "/api/v1/me/bulletins/" + bulletin, tA, null).andExpect(jsonPath("$.lignes[0].code").value("IR")).andExpect(jsonPath("$.lignes[0].sens").value("retenue")).andExpect(jsonPath("$.lignes[0].montant").value(640.0));
    }

    @Test void modeleAvecAutreEncodageSeparateurEtDecimale() throws Exception {
        String modele = idOf(send(HttpMethod.POST, "/api/v1/rh/imports/modeles", tRh, Map.of("type", "salaire", "nom", "Ancien système", "encodage", "windows-1252", "separateur", ",", "separateurDecimal", ".", "formatDate", "yyyy-MM-dd",
                "mapping", Map.of("colonnes", Map.of("matricule", "Matricule", "salaire_net", "Salaire net à payer")))).andExpect(status().isCreated()));
        byte[] f = ("Matricule,Salaire net à payer\n" + mA + ",\"7842.10\"\n").getBytes(Charset.forName("windows-1252"));
        post("/api/v1/rh/imports/controle", "salaire", f, "x.csv", Map.of("annee", "2026", "mois", "5", "modeleId", modele)).andExpect(jsonPath("$.rapport.valides").value(1)).andExpect(jsonPath("$.rapport.erreurs").value(0));
    }

    @Test void creditsRemplacesSansSuppression() throws Exception {
        String h = "Matricule;Banque;Dossier;Type;Mensualité;Restant;Mois restants\n";
        String l1 = json.readTree(post("/api/v1/rh/imports", "credit", csv(h + mA + ";AOS;D-1;interne_AOS;1450;26100;18\n"), "c1.csv", Map.of()).andReturn().getResponse().getContentAsString()).get("id").asText();
        send(HttpMethod.POST, "/api/v1/rh/imports/lots/" + l1 + "/publier", tRh, null).andExpect(status().isOk());
        String l2 = json.readTree(post("/api/v1/rh/imports", "credit", csv(h + mA + ";AOS;D-1;interne_AOS;1450;24650;17\n"), "c2.csv", Map.of()).andReturn().getResponse().getContentAsString()).get("id").asText();
        send(HttpMethod.GET, "/api/v1/me/credits", tA, null).andExpect(jsonPath("$[0].nbMoisRestants").value(18));
        send(HttpMethod.POST, "/api/v1/rh/imports/lots/" + l2 + "/publier", tRh, null).andExpect(status().isOk());
        send(HttpMethod.GET, "/api/v1/me/credits", tA, null).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nbMoisRestants").value(17)).andExpect(jsonPath("$[0].montantRestant").value(24650.0));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM credit", Integer.class), "l'ancienne ligne est conservée");
        assertEquals("remplace", jdbc.queryForObject("SELECT statut FROM credit WHERE nb_mois_restants = 18", String.class));
    }

    @Test void importDuPersonnelCreeEtMetAJour_avecHistorique() throws Exception {
        String f = "Matricule;Nom FR;Prénom FR;Nom AR;Prénom AR;Structure;Date de recrutement;Email\n"
                + "70001;Nouvel;Agent;جديد;موظف;IMS1;01/09/2026;nouvel@example.ma\n"
                + mA + ";Impa;Prenom;الاسم;اللقب;IMS1;01/09/2015;maj@example.ma\n"
                + "70002;Sans;Structure;س;ص;INCONNUE;01/09/2026;\n";
        post("/api/v1/rh/imports", "personnel", csv(f), "personnel.csv", Map.of()).andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("publie")).andExpect(jsonPath("$.nbLignes").value(2)).andExpect(jsonPath("$.nbErreurs").value(1));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE matricule = '70001'", Integer.class));
        assertEquals("maj@example.ma", jdbc.queryForObject("SELECT email FROM personnel WHERE id = ?", String.class, pA));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM historique_carriere h JOIN personnel p ON p.id = h.personnel_id WHERE p.matricule = '70001'", Integer.class));
    }

    @Test void rapportCsvNeutraliseLesFormules() throws Exception {
        String id = json.readTree(post("/api/v1/rh/imports", "prime", csv("Matricule;Montant brut;Montant net;Date\n=cmd|' /C calc'!A0;100;100;01/01/2026\n"), "p.csv", Map.of()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String body = mvc.perform(get("/api/v1/rh/imports/lots/" + id + "/rapport").header("Authorization", tRh)).andExpect(status().isOk()).andExpect(content().string(containsString("UNKNOWN_MATRICULE"))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.startsWith("\uFEFFligne;niveau;champ;code;message"), "BOM UTF-8 pour Excel");
        assertTrue(body.contains("\"Matricule inconnu"), "champs texte entre guillemets");
    }

    @Test void fichiersInvalides() throws Exception {
        post("/api/v1/rh/imports/analyse", "salaire", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 1, 2, 3}, "vieux.xls", Map.of()).andExpect(status().isUnsupportedMediaType());
        post("/api/v1/rh/imports/analyse", "inconnu", csv("a;b\n1;2\n"), "x.csv", Map.of()).andExpect(status().isBadRequest());
        post("/api/v1/rh/imports", "salaire", csv("Colonne;Autre\n1;2\n"), "x.csv", Map.of("annee", "2026", "mois", "1")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("MAPPING_INCOMPLET"));
        ResultActions r = post("/api/v1/rh/imports/analyse", "salaire", new byte[0], "vide.csv", Map.of());
        r.andExpect(status().isPayloadTooLarge());
    }
}
