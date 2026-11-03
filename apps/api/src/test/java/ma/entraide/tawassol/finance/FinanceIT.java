package ma.entraide.tawassol.finance;

import java.util.UUID;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceIT extends IntegrationTestBase {
    String ta, tb, pubId, draftId, otherId;

    @BeforeEach
    void fixture() throws Exception {
        String s = data.structure(data.delegation("F1"), null, "FS");
        String pa = data.personnel(s, "Fina"), pb = data.personnel(s, "Finb");
        data.user("fa", "agent", pa, false); data.user("fb", "agent", pb, false);
        pubId = UUID.randomUUID().toString(); draftId = UUID.randomUUID().toString(); otherId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO salaire (id, personnel_id, annee, mois, salaire_net, alloc_familiale, retenue_mutuelle, rappel, statut) VALUES (?,?,2026,10,7842.10,300,120.50,0,'publie')", pubId, pa);
        jdbc.update("INSERT INTO salaire (id, personnel_id, annee, mois, salaire_net, statut) VALUES (?,?,2026,11,8000,'brouillon')", draftId, pa);
        jdbc.update("INSERT INTO salaire (id, personnel_id, annee, mois, salaire_net, statut) VALUES (?,?,2026,10,9999,'publie')", otherId, pb);
        jdbc.update("INSERT INTO salaire_ligne (salaire_id, ordre, code, libelle_fr, libelle_ar, sens, montant) VALUES (?,1,'IR','Impôt sur le revenu','الضريبة على الدخل','retenue',640)", pubId);
        jdbc.update("INSERT INTO prime (personnel_id, montant_brut, ir, montant_net, date_prime, statut) VALUES (?,4000,560,3440,'2026-06-15','publie')", pa);
        jdbc.update("INSERT INTO prime (personnel_id, montant_brut, ir, montant_net, date_prime, statut) VALUES (?,100,0,100,'2026-07-15','brouillon')", pa);
        jdbc.update("INSERT INTO credit (personnel_id, banque, mensualite, montant_global, montant_restant, nb_mois_restants, statut) VALUES (?,'AOS',1450,45000,26100,18,'publie')", pa);
        ta = bearer("fa"); tb = bearer("fb");
    }

    @Test void seulsLesBulletinsPublies_deLAgentConnecte() throws Exception {
        send(HttpMethod.GET, "/api/v1/me/bulletins", ta, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(pubId));
        send(HttpMethod.GET, "/api/v1/me/bulletins/" + draftId, ta, null).andExpect(status().isNotFound());
        send(HttpMethod.GET, "/api/v1/me/bulletins/" + otherId, ta, null).andExpect(status().isNotFound());
        send(HttpMethod.GET, "/api/v1/me/bulletins/" + pubId + "/pdf", tb, null).andExpect(status().isNotFound());
    }

    @Test void detailAvecRubriquesEtConsultationJournalisee() throws Exception {
        send(HttpMethod.GET, "/api/v1/me/bulletins/" + pubId, ta, null).andExpect(status().isOk()).andExpect(jsonPath("$.lignes[0].code").value("IR")).andExpect(jsonPath("$.salaireNet").value(7842.10));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'SALAIRE_READ' AND entite_id = ?", Integer.class, pubId));
    }

    @Test void pdfDuReleve() throws Exception {
        byte[] pdf = send(HttpMethod.GET, "/api/v1/me/bulletins/" + pubId + "/pdf", ta, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 2000, "taille " + pdf.length);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        java.nio.file.Files.write(java.nio.file.Path.of("target/releve-test.pdf"), pdf);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit WHERE action = 'SALAIRE_PDF'", Integer.class));
    }

    @Test void primesEtCreditsPublies() throws Exception {
        send(HttpMethod.GET, "/api/v1/me/primes", ta, null).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].montantNet").value(3440.0));
        send(HttpMethod.GET, "/api/v1/me/credits", ta, null).andExpect(jsonPath("$[0].nbMoisRestants").value(18));
        send(HttpMethod.GET, "/api/v1/me/credits", tb, null).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void compteSansAgent() throws Exception {
        data.user("tech3", "admin", null, false);
        send(HttpMethod.GET, "/api/v1/me/bulletins", bearer("tech3"), null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NO_PERSONNEL"));
        mvc.perform(get("/api/v1/me/bulletins")).andExpect(status().isUnauthorized());
    }
}
