package ma.entraide.tawassol.access;

import java.time.LocalDate;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests d'autorisation, dont les cas négatifs : rôle ET périmètre sont vérifiés côté serveur. */
class AuthorizationIT extends IntegrationTestBase {
    String d1, d2, s1, s2, s3, s4, s5;
    String ag1, ag2, ag3, ag4, resp, respDeleg, rh, suppleant;

    @BeforeEach
    void fixture() {
        d1 = data.delegation("D1"); d2 = data.delegation("D2");
        s1 = data.structure(d1, null, "S1"); s2 = data.structure(d1, s1, "S2"); s3 = data.structure(d1, s2, "S3");
        s4 = data.structure(d1, null, "S4"); s5 = data.structure(d2, null, "S5");
        ag1 = data.personnel(s2, "Agentun"); ag2 = data.personnel(s2, "Agentdeux");
        ag3 = data.personnel(s4, "Agenttrois"); ag4 = data.personnel(s5, "Agentquatre");
        resp = data.personnel(s1, "Chef"); respDeleg = data.personnel(s4, "Chefdeleg");
        rh = data.personnel(s4, "Rh"); suppleant = data.personnel(s3, "Suppleant");
        data.user("ag1", "agent", ag1, false); data.user("ag2", "agent", ag2, false);
        data.user("resp", "responsable", resp, false); data.user("respdeleg", "responsable", respDeleg, false);
        data.user("rh", "rh", rh, false); data.user("tech", "admin", null, false);
        data.user("supp", "agent", suppleant, false); data.user("mustchange", "agent", ag3, true);
        data.perimeter(resp, "structure", s1, LocalDate.of(2020, 1, 1), null, true);
        data.perimeter(respDeleg, "delegation", d1, LocalDate.of(2020, 1, 1), null, true);
    }

    @Test void sansJetonOn_est_refuse() throws Exception {
        mvc.perform(get("/api/v1/personnel")).andExpect(status().isUnauthorized());
    }

    @Test void agentVoitSaPropreFicheEnDetail() throws Exception {
        mvc.perform(get("/api/v1/personnel/" + ag1).header("Authorization", bearer("ag1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.niveau").value("DETAIL")).andExpect(jsonPath("$.email").exists());
    }

    @Test void agentNeVoitPasUnCollegueDeLaMemeStructure() throws Exception {
        mvc.perform(get("/api/v1/personnel/" + ag2).header("Authorization", bearer("ag1"))).andExpect(status().isNotFound());
    }

    @Test void agentNeListeQueLuiMeme() throws Exception {
        mvc.perform(get("/api/v1/personnel").header("Authorization", bearer("ag1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.content[0].id").value(ag1));
    }

    @Test void responsableVoitSonPersonnelEnSynthese_sansDonneesSensibles() throws Exception {
        mvc.perform(get("/api/v1/personnel/" + ag1).header("Authorization", bearer("resp")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.niveau").value("SYNTHESE")).andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telephone").doesNotExist());
    }

    @Test void responsableVoitLesSousStructures() throws Exception {
        mvc.perform(get("/api/v1/personnel/" + suppleant).header("Authorization", bearer("resp"))).andExpect(status().isOk());
    }

    @Test void responsableNeVoitPasHorsPerimetre_memeDelegation() throws Exception {
        mvc.perform(get("/api/v1/personnel/" + ag3).header("Authorization", bearer("resp"))).andExpect(status().isNotFound());
    }

    @Test void responsableNeVoitPasUneAutreDelegation() throws Exception {
        mvc.perform(get("/api/v1/personnel/" + ag4).header("Authorization", bearer("resp"))).andExpect(status().isNotFound());
    }

    @Test void listeDuResponsableFiltreeParPerimetre() throws Exception {
        mvc.perform(get("/api/v1/personnel?size=100").header("Authorization", bearer("resp")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", hasItem(ag1)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(ag3)))).andExpect(jsonPath("$.content[*].id", not(hasItem(ag4))));
    }

    @Test void responsableDeDelegationVoitToutesLesStructuresDeSaDelegation() throws Exception {
        mvc.perform(get("/api/v1/personnel/" + ag3).header("Authorization", bearer("respdeleg"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/personnel/" + ag4).header("Authorization", bearer("respdeleg"))).andExpect(status().isNotFound());
    }

    @Test void perimetreExpireOuInactifNeDonneRien() throws Exception {
        jdbc.update("UPDATE responsable_perimetre SET date_fin = ? WHERE personnel_id = ?", LocalDate.now().minusDays(1), resp);
        mvc.perform(get("/api/v1/personnel/" + ag1).header("Authorization", bearer("resp"))).andExpect(status().isNotFound());
        jdbc.update("UPDATE responsable_perimetre SET date_fin = NULL, est_actif = 0 WHERE personnel_id = ?", resp);
        mvc.perform(get("/api/v1/personnel/" + ag1).header("Authorization", bearer("resp"))).andExpect(status().isNotFound());
    }

    @Test void rhVoitToutEnDetail() throws Exception {
        mvc.perform(get("/api/v1/personnel/" + ag4).header("Authorization", bearer("rh")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.niveau").value("DETAIL"));
    }

    @Test void adminTechniqueNAccedePasAuxDonneesDesAgents() throws Exception {
        mvc.perform(get("/api/v1/personnel").header("Authorization", bearer("tech"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/personnel/" + ag1).header("Authorization", bearer("tech"))).andExpect(status().isForbidden());
    }

    @Test void changementDeMotDePasseObligatoireBloqueLeReste() throws Exception {
        mvc.perform(get("/api/v1/personnel").header("Authorization", bearer("mustchange")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test void compteDesactiveEstRefuseImmediatement() throws Exception {
        String token = bearer("ag1");
        jdbc.update("UPDATE utilisateur SET est_actif = 0 WHERE login = 'ag1'");
        mvc.perform(get("/api/v1/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test void suppleantVoitLePerimetrePendantLaSuppleanceSeulement() throws Exception {
        data.suppleance(resp, suppleant, LocalDate.now().minusDays(1), LocalDate.now().plusDays(5));
        mvc.perform(get("/api/v1/personnel/" + ag1).header("Authorization", bearer("supp"))).andExpect(status().isOk());
        jdbc.update("UPDATE suppleance SET date_debut = ?, date_fin = ?", LocalDate.now().minusDays(10), LocalDate.now().minusDays(2));
        mvc.perform(get("/api/v1/personnel/" + ag1).header("Authorization", bearer("supp"))).andExpect(status().isNotFound());
    }

    @Test void structuresMinesRespecteLePerimetre() throws Exception {
        mvc.perform(get("/api/v1/structures/mine").header("Authorization", bearer("resp")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].code", hasItem("S3"))).andExpect(jsonPath("$[*].code", not(hasItem("S4"))));
    }

    @Test void lectureDuneFicheParAutruiEstJournalisee_pasLaLectureDeSaPropreFiche() throws Exception {
        mvc.perform(get("/api/v1/personnel/" + ag1).header("Authorization", bearer("ag1"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/personnel/" + ag1).header("Authorization", bearer("rh"))).andExpect(status().isOk());
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'PERSONNEL_READ'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, n);
    }
}
