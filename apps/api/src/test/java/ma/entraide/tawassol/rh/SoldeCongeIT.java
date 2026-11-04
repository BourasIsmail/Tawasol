package ma.entraide.tawassol.rh;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.demande.SoldeCongeService;
import ma.entraide.tawassol.migration.LegacyMigrator;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Soldes de congé selon le modèle de Suivi RH : jours imputés par année, périodes passées, régularisation, restitution, reconstitution rejouable, cohérence. */
class SoldeCongeIT extends IntegrationTestBase {
    @Autowired SoldeCongeService soldes;
    String agent, rhPers, tRh, tAgent, struct;
    final int an = LocalDate.now().getYear();

    @BeforeEach void fixture() throws Exception {
        struct = data.structure(data.delegation("SC"), null, "SCS");
        agent = data.personnel(struct, "Agent");
        rhPers = data.personnel(struct, "Rh");
        data.user("agt", "agent", agent, false); data.user("rhh", "rh", rhPers, false);
        tAgent = bearer("agt"); tRh = bearer("rhh");
    }

    @AfterEach void nettoyage() {
        jdbc.update("DELETE FROM parametre WHERE cle LIKE 'conge.annuel.droit_jours.%'");
        jdbc.update("UPDATE parametre SET valeur = '0' WHERE cle IN ('conge.report.annees_max','conge.report.plafond_jours')");
        jdbc.update("DELETE FROM jour_ferie WHERE libelle_fr LIKE 'TEST %'");
    }

    String conge(String personnelId, String statut, LocalDate debut, LocalDate fin, int jours, boolean herite) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO demande (id, numero, personnel_id, type_demande_id, objet, statut, date_debut, date_fin, nb_jours, id_legacy) VALUES (?,?,?,(SELECT id FROM type_demande WHERE code = 'CONGE_ANNUEL'),'Congé',?,?,?,?,?)",
                id, "T-" + id.substring(0, 8), personnelId, statut, debut, fin, jours, herite ? (long) (Math.random() * 1_000_000_000L) : null);
        return id;
    }

    /** Imputation telle que la reprise de Suivi RH l'écrit : jours pris sur une année de solde. */
    void impute(String demandeId, int annee, int jours) {
        jdbc.update("INSERT INTO conge_imputation (demande_id, annee, jours) VALUES (?,?,?) ON DUPLICATE KEY UPDATE jours = VALUES(jours)", demandeId, annee, jours);
    }

    void reconstituer() { for (String sql : LegacyMigrator.SQL_SOLDES_RECONSTITUES) jdbc.update(sql); }

    JsonNode post(String path, String token, Object body, int statut) throws Exception {
        return json.readTree(send(HttpMethod.POST, path, token, body).andExpect(status().is(statut)).andReturn().getResponse().getContentAsString());
    }

    JsonNode get(String path, String token) throws Exception {
        return json.readTree(send(HttpMethod.GET, path, token, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void leReste_parAnnee_suitLesJoursImputesCommeDansSuiviRh() {
        // cas réel de Suivi RH (agent P4071) : 22 jours par an ; les congés consomment d'abord l'année précédente
        int a2 = an, a1 = an - 1, a0 = an - 2;
        String c1 = conge(agent, "approuvee", LocalDate.of(a1 - 1, 12, 30), LocalDate.of(a1, 1, 15), 11, true);
        String c2 = conge(agent, "approuvee", LocalDate.of(a1, 5, 19), LocalDate.of(a1, 6, 2), 11, true);
        String c3 = conge(agent, "approuvee", LocalDate.of(a1, 7, 21), LocalDate.of(a1, 7, 22), 2, true);
        String c4 = conge(agent, "approuvee", LocalDate.of(a1, 9, 8), LocalDate.of(a1, 9, 12), 5, true);
        String c5 = conge(agent, "soumise", LocalDate.of(a2, 1, 26), LocalDate.of(a2, 1, 30), 5, true);      // « créé » dans Suivi RH : compté
        String c6 = conge(agent, "soumise", LocalDate.of(a2, 5, 14), LocalDate.of(a2, 5, 29), 9, true);      // « modifié » : compté
        String c7 = conge(agent, "approuvee", LocalDate.of(a2, 10, 5), LocalDate.of(a2, 10, 9), 5, true);
        impute(c1, a1 - 1, 11); impute(c2, a0, 11); impute(c3, a1, 2); impute(c4, a1, 5);
        impute(c5, a1, 5); impute(c6, a1, 9); impute(c7, a2, 4); impute(c7, a1, 1);
        reconstituer();
        reconstituer();   // rejouer ne double rien
        SoldeCongeService.Solde s2 = soldes.get(agent, a2, null), s1 = soldes.get(agent, a1, null);
        assertEquals(0, new BigDecimal("18").compareTo(s2.disponible()), "reste de l'année en cours : 22 − 4");
        assertEquals(0, new BigDecimal("4").compareTo(s2.pris()));
        assertEquals(0, new BigDecimal("22").compareTo(s2.droit()));
        assertEquals(0, BigDecimal.ZERO.compareTo(s1.disponible()), "année précédente entièrement consommée : 22 − 22");
        assertEquals("reconstitue", s2.source());
        assertTrue(s2.aVerifier());
    }

    @Test void unCongeDejaCompteNeReservePasDeJoursEnPlus() {
        LocalDate futur = LocalDate.now().plusDays(10);
        String deja = conge(agent, "soumise", futur, futur.plusDays(4), 4, true);   // créé dans Suivi RH : ses jours sont déjà dans « pris »
        impute(deja, futur.getYear(), 4);
        reconstituer();
        SoldeCongeService.Solde s = soldes.get(agent, futur.getYear(), null);
        assertEquals(0, new BigDecimal("4").compareTo(s.pris()));
        assertEquals(0, BigDecimal.ZERO.compareTo(s.reserve()), "pas de double décompte");
        assertEquals(0, new BigDecimal("18").compareTo(s.disponible()));
    }

    @Test void uneDemandeSoumiseDontLaPeriodeEstPasseeNeReserveRien() {
        LocalDate passeDebut = LocalDate.now().minusDays(40), futurDebut = LocalDate.now().plusDays(10);
        conge(agent, "soumise", passeDebut, passeDebut.plusDays(5), 8, false);          // période passée : ne réserve rien
        conge(agent, "soumise", futurDebut, futurDebut.plusDays(4), 4, false);         // à venir : réserve ses jours
        BigDecimal attenduPasse = passeDebut.getYear() == futurDebut.getYear() ? new BigDecimal("4") : BigDecimal.ZERO;
        assertEquals(0, attenduPasse.compareTo(soldes.get(agent, passeDebut.getYear(), null).reserve()));
        assertEquals(0, new BigDecimal("4").compareTo(soldes.get(agent, futurDebut.getYear(), null).reserve()));
    }

    @Test void laRhRegularisePlusieursDemandesEnLotSousSonControle() throws Exception {
        String pris = conge(agent, "soumise", LocalDate.of(an, 1, 5), LocalDate.now().minusDays(30), 6, false);
        String jamais = conge(agent, "validee_responsable", LocalDate.of(an, 1, 6), LocalDate.now().minusDays(29), 3, false);
        String futur = conge(agent, "soumise", LocalDate.now().plusDays(30), LocalDate.now().plusDays(33), 3, false);
        JsonNode l = get("/api/v1/rh/conges/a-regulariser", tRh);
        assertEquals(2, l.get("total").asInt(), "seules les périodes passées sont à régulariser");
        post("/api/v1/rh/conges/regulariser", tRh, Map.of("ids", List.of(pris), "decision", "approuver"), 422);
        post("/api/v1/rh/conges/regulariser", tAgent, Map.of("ids", List.of(pris), "decision", "approuver", "commentaire", "x"), 403);
        JsonNode r = post("/api/v1/rh/conges/regulariser", tRh, Map.of("ids", List.of(pris, futur), "decision", "approuver", "commentaire", "Congé pris, confirmé par le chef de service"), 200);
        assertEquals(1, r.get("traitees").asInt());
        assertEquals(1, r.get("ignorees").asInt(), "une demande dont la période n'est pas passée n'est jamais régularisée");
        assertEquals("approuvee", jdbc.queryForObject("SELECT statut FROM demande WHERE id = ?", String.class, pris));
        assertEquals("soumise", jdbc.queryForObject("SELECT statut FROM demande WHERE id = ?", String.class, futur));
        assertEquals(0, new BigDecimal("6").compareTo(soldes.get(agent, an, null).pris()));
        post("/api/v1/rh/conges/regulariser", tRh, Map.of("ids", List.of(jamais), "decision", "rejeter", "commentaire", "Jamais pris"), 200);
        assertEquals("rejetee", jdbc.queryForObject("SELECT statut FROM demande WHERE id = ?", String.class, jamais));
        assertEquals(0, new BigDecimal("6").compareTo(soldes.get(agent, an, null).pris()), "un rejet ne décompte rien");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM demande_historique WHERE demande_id = ?", Integer.class, pris) >= 1);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'CONGE_REGULARISE'", Integer.class) >= 2);
    }

    @Test void regulariserUnCongeDejaCompteNeDecomptePasDeuxFoisEtLeRejeterRestitue() throws Exception {
        String herite = conge(agent, "soumise", LocalDate.of(an, 1, 5), LocalDate.now().minusDays(30), 6, true);   // « créé » dans Suivi RH, déjà dans le solde
        impute(herite, an, 6);
        reconstituer();
        assertEquals(0, new BigDecimal("6").compareTo(soldes.get(agent, an, null).pris()));
        assertTrue(get("/api/v1/rh/conges/a-regulariser", tRh).get("content").get(0).get("dejaCompte").asBoolean());
        post("/api/v1/rh/conges/regulariser", tRh, Map.of("ids", List.of(herite), "decision", "approuver", "commentaire", "Pris, confirmé"), 200);
        assertEquals(0, new BigDecimal("6").compareTo(soldes.get(agent, an, null).pris()), "approuver ne recompte pas des jours déjà comptés");
        String autre = conge(agent, "soumise", LocalDate.of(an, 1, 7), LocalDate.now().minusDays(20), 4, true);
        impute(autre, an, 4);
        reconstituer();
        assertEquals(0, new BigDecimal("10").compareTo(soldes.get(agent, an, null).pris()));
        post("/api/v1/rh/conges/regulariser", tRh, Map.of("ids", List.of(autre), "decision", "rejeter", "commentaire", "Jamais pris"), 200);
        assertEquals(0, new BigDecimal("6").compareTo(soldes.get(agent, an, null).pris()), "« jamais pris » restitue les jours comptés");
    }

    @Test void laRhNeRegularisePasSonPropreConge() throws Exception {
        String mien = conge(rhPers, "soumise", LocalDate.of(an, 1, 5), LocalDate.now().minusDays(30), 5, true);
        JsonNode r = post("/api/v1/rh/conges/regulariser", tRh, Map.of("ids", List.of(mien), "decision", "approuver", "commentaire", "x"), 200);
        assertEquals(0, r.get("traitees").asInt());
        assertEquals("soumise", jdbc.queryForObject("SELECT statut FROM demande WHERE id = ?", String.class, mien));
    }

    @Test void annulerUneApprobationRestitueLesJours() throws Exception {
        String id = conge(agent, "soumise", LocalDate.of(an, 3, 2), LocalDate.of(an, 3, 6), 5, false);
        jdbc.update("UPDATE demande SET statut = 'approuvee' WHERE id = ?", id);
        soldes.imputer(id, agent, an, new BigDecimal("5"));
        assertEquals(0, new BigDecimal("5").compareTo(soldes.get(agent, an, null).pris()));
        post("/api/v1/rh/conges/" + id + "/annuler-approbation", tRh, Map.of("commentaire", ""), 422);
        post("/api/v1/rh/conges/" + id + "/annuler-approbation", tRh, Map.of("commentaire", "Congé non pris"), 200);
        assertEquals(0, BigDecimal.ZERO.compareTo(soldes.get(agent, an, null).pris()));
        assertEquals("annulee", jdbc.queryForObject("SELECT statut FROM demande WHERE id = ?", String.class, id));
        post("/api/v1/rh/conges/" + id + "/annuler-approbation", tRh, Map.of("commentaire", "encore"), 409);   // plus approuvé
    }

    @Test void controleDeCoherenceEtSoldesNegatifs() throws Exception {
        String id = conge(agent, "approuvee", LocalDate.of(an, 2, 2), LocalDate.of(an, 3, 20), 30, true);   // 30 jours pour un droit de 22
        impute(id, an, 30);
        reconstituer();
        JsonNode c = get("/api/v1/rh/conges/controle", tRh);
        assertTrue(c.get("coherent").asBoolean(), "jours imputés = jours pris des soldes tenus par le système");
        assertEquals(1, c.get("negatifsTotal").asInt());
        assertEquals("Agent", c.get("negatifs").get(0).get("nomFr").asText());
        assertTrue(c.get("negatifs").get(0).get("disponible").asDouble() < 0);
        jdbc.update("UPDATE solde_conge SET pris = pris + 2 WHERE personnel_id = ?", agent);   // écart sans imputation correspondante
        assertFalse(get("/api/v1/rh/conges/controle", tRh).get("coherent").asBoolean());
    }

    @Test void laCorrectionDeLaRhFaitFoiEtResisteAuRejeuDeLaReconstitution() throws Exception {
        String id = conge(agent, "approuvee", LocalDate.of(an, 2, 2), LocalDate.of(an, 2, 13), 10, true);
        impute(id, an, 10);
        reconstituer();
        send(HttpMethod.PUT, "/api/v1/rh/conges/soldes/" + agent + "/" + an, tRh, Map.of("droit", 25, "reporte", 3, "pris", 12)).andExpect(status().isOk());
        reconstituer();
        SoldeCongeService.Solde s = soldes.get(agent, an, null);
        assertEquals("saisi", s.source());
        assertEquals(0, new BigDecimal("12").compareTo(s.pris()), "la valeur saisie n'est jamais écrasée par la reconstitution");
        assertEquals(0, new BigDecimal("25").compareTo(s.droit()));
        assertEquals(0, new BigDecimal("16").compareTo(s.disponible()));   // 25 + 3 - 12
        send(HttpMethod.PUT, "/api/v1/rh/conges/soldes/" + agent + "/" + an, tAgent, Map.of("droit", 99)).andExpect(status().isForbidden());
        send(HttpMethod.PUT, "/api/v1/rh/conges/soldes/" + agent + "/" + an, tRh, Map.of("droit", 400)).andExpect(status().isUnprocessableEntity());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'SOLDE_CONGE_CORRIGE'", Integer.class) >= 1);
    }

    @Test void droitParCategorieEtReportDesAnneesPrecedentesParametrables() throws Exception {
        assertEquals(0, new BigDecimal("22").compareTo(soldes.droit(agent, an)));
        jdbc.update("INSERT INTO parametre (cle, valeur, date_effet, statut_validation) VALUES ('conge.annuel.droit_jours.CONTRACTUEL','18','2026-01-01','non_valide')");
        send(HttpMethod.PUT, "/api/v1/rh/conges/soldes/" + agent + "/" + an, tRh, Map.of("categorieAgent", "contractuel")).andExpect(status().isOk());
        jdbc.update("DELETE FROM solde_conge WHERE personnel_id = ?", agent);
        assertEquals(0, new BigDecimal("18").compareTo(soldes.droit(agent, an)), "le droit suit la catégorie de l'agent");
        // l'an passé : 8 jours pris sur 18, il reste 10
        jdbc.update("INSERT INTO solde_conge (id, personnel_id, annee, droit, pris, reporte, source) VALUES (UUID(), ?, ?, 18, 8, 0, 'saisi')", agent, an - 1);
        assertEquals(0, new BigDecimal("18").compareTo(soldes.get(agent, an, null).utilisable()), "report désactivé par défaut : rien ne vient de l'an passé");
        jdbc.update("UPDATE parametre SET valeur = '1' WHERE cle = 'conge.report.annees_max'");
        jdbc.update("UPDATE parametre SET valeur = '6' WHERE cle = 'conge.report.plafond_jours'");
        SoldeCongeService.Solde s = soldes.get(agent, an, null);
        assertEquals(0, new BigDecimal("18").compareTo(s.disponible()), "le reste de l'année en cours ne change pas");
        assertEquals(0, new BigDecimal("24").compareTo(s.utilisable()), "10 jours restants l'an passé, plafonnés à 6, utilisables en plus");
        // un congé de 20 jours consomme d'abord l'année précédente (6 jours), puis l'année courante (14)
        String id = conge(agent, "soumise", LocalDate.of(an, 4, 6), LocalDate.of(an, 4, 24), 20, false);
        jdbc.update("UPDATE demande SET statut = 'approuvee' WHERE id = ?", id);
        soldes.imputer(id, agent, an, new BigDecimal("20"));
        assertEquals(0, new BigDecimal("14").compareTo(soldes.get(agent, an, null).pris()));
        assertEquals(0, new BigDecimal("14").compareTo(soldes.get(agent, an - 1, null).pris()), "8 déjà pris + 6 imputés");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM conge_imputation WHERE demande_id = ?", Integer.class, id));
    }

    @Test void joursFeriesTenusParLaRh() throws Exception {
        LocalDate d = LocalDate.of(an, 11, 3);
        JsonNode c = post("/api/v1/rh/jours-feries", tRh, Map.of("date", d.toString(), "libelleFr", "TEST fête", "libelleAr", "عيد", "type", "religieux", "aConfirmer", true), 201);
        post("/api/v1/rh/jours-feries", tRh, Map.of("date", d.toString(), "libelleFr", "TEST bis", "libelleAr", "عيد", "type", "civil"), 409);
        post("/api/v1/rh/jours-feries", tRh, Map.of("date", d.plusDays(1).toString(), "libelleFr", "", "libelleAr", "عيد", "type", "civil"), 422);
        post("/api/v1/rh/jours-feries", tAgent, Map.of("date", d.plusDays(2).toString(), "libelleFr", "TEST x", "libelleAr", "ي", "type", "civil"), 403);
        boolean present = false;
        for (JsonNode j : get("/api/v1/rh/jours-feries?annee=" + an, tRh)) if (j.get("id").asText().equals(c.get("id").asText())) present = j.get("aConfirmer").asBoolean();
        assertTrue(present);
        send(HttpMethod.DELETE, "/api/v1/rh/jours-feries/" + c.get("id").asText(), tRh, null).andExpect(status().isNoContent());
    }

    @Test void soldesParAnneePourLAgentEtPourLaRh() throws Exception {
        String a = conge(agent, "approuvee", LocalDate.of(an - 1, 3, 2), LocalDate.of(an - 1, 3, 6), 5, true);
        String b = conge(agent, "approuvee", LocalDate.of(an, 2, 2), LocalDate.of(an, 2, 13), 10, true);
        impute(a, an - 1, 5); impute(b, an, 10);
        reconstituer();
        JsonNode mien = get("/api/v1/me/soldes-conge", tAgent);
        assertEquals(an, mien.get(0).get("annee").asInt(), "année la plus récente d'abord");
        assertEquals(an - 1, mien.get(1).get("annee").asInt());
        assertEquals(10, mien.get(0).get("pris").asInt());
        assertEquals(17, mien.get(1).get("disponible").asInt());   // 22 - 5
        assertEquals("reconstitue", mien.get(0).get("source").asText());
        assertTrue(mien.get(0).get("aVerifier").asBoolean());
        JsonNode rh = get("/api/v1/rh/conges/soldes/" + agent, tRh);
        assertEquals(mien.size(), rh.size());
        send(HttpMethod.GET, "/api/v1/rh/conges/soldes/" + agent, tAgent, null).andExpect(status().isForbidden());
        send(HttpMethod.GET, "/api/v1/rh/conges/soldes/inconnu", tRh, null).andExpect(status().isNotFound());
    }
}
