package ma.entraide.tawassol.deplacement;

import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import ma.entraide.tawassol.security.Role;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DeplacementCoreIT extends IntegrationTestBase {
    @Autowired IndemniteService indemnites;
    @Autowired NumerotationMissionService numeros;
    @Autowired PermissionService permissions;
    @Autowired TransactionTemplate tx;

    private static BigDecimal b(String s) { return new BigDecimal(s); }
    private String puissance(String code) { return jdbc.queryForObject("SELECT id FROM puissance_fiscale WHERE code = ?", String.class, code); }

    @Test void indemniteDeRepasAuxDeuxTaux() {
        // échelle 7 : 40 puis 35 MAD ; 20 jours = 44 repas au taux 1 et 16 au taux 2 (cas vérifié sur Suivi RH)
        var c = indemnites.calculer(new IndemniteService.Demande(7, LocalDateTime.parse("2024-06-13T08:00"), LocalDateTime.parse("2024-07-03T08:00"), true, "transports_en_commun", null, null, null, null));
        assertEquals(44, c.repasTaux1()); assertEquals(16, c.repasTaux2());
        assertEquals(b("2320.00"), c.montantIndemnites()); assertEquals(b("2320.00"), c.total());
        assertTrue(c.avertissements().contains("BAREME_NON_VALIDE"), "un barème non validé est toujours signalé");
    }

    @Test void fraisDeTransportSeulementPourLesTransportsEnCommun() {
        var tc = indemnites.calculer(new IndemniteService.Demande(7, LocalDateTime.parse("2024-06-13T08:00"), LocalDateTime.parse("2024-06-13T20:00"), true, "transports_en_commun", b("120.50"), null, null, null));
        assertEquals(b("120.50"), tc.fraisTransport());
        var vs = indemnites.calculer(new IndemniteService.Demande(7, LocalDateTime.parse("2024-06-13T08:00"), LocalDateTime.parse("2024-06-13T20:00"), true, "voiture_de_service", b("120.50"), null, null, null));
        assertEquals(b("0.00"), vs.fraisTransport().setScale(2));
        assertEquals(tc.montantIndemnites(), vs.montantIndemnites());
    }

    @Test void indemniteKilometriqueVoiturePrivee() {
        var c = indemnites.calculer(new IndemniteService.Demande(7, LocalDateTime.parse("2024-06-13T08:00"), LocalDateTime.parse("2024-06-13T20:00"), true, "voiture_privee", null, b("100"), 2, puissance("six_or_less")));
        assertEquals(b("1.20"), c.tauxKm()); assertEquals(b("240.00"), c.montantKm());
        assertEquals(c.montantIndemnites().add(b("240.00")), c.total());
    }

    @Test void missionNonRemboursableOuSansRetourNeDonneAucunRepas() {
        var nr = indemnites.calculer(new IndemniteService.Demande(7, LocalDateTime.parse("2024-06-13T08:00"), LocalDateTime.parse("2024-06-20T20:00"), false, "voiture_de_service", null, null, null, null));
        assertEquals(0, nr.repasTaux1() + nr.repasTaux2()); assertEquals(b("0.00"), nr.total());
        var sr = indemnites.calculer(new IndemniteService.Demande(7, LocalDateTime.parse("2024-06-13T08:00"), null, true, "voiture_de_service", null, null, null, null));
        assertTrue(sr.avertissements().contains("RETOUR_NON_RENSEIGNE")); assertEquals(0, sr.repasTaux1());
    }

    @Test void sansBaremeOuSansSeuilsLeCalculEstRefuse() {
        assertEquals("BAREME_ABSENT", assertThrows(ApiException.class, () -> indemnites.calculer(new IndemniteService.Demande(55, LocalDateTime.parse("2024-06-13T08:00"), LocalDateTime.parse("2024-06-14T08:00"), true, "voiture_de_service", null, null, null, null))).code());
        assertEquals("SEUILS_REPAS_ABSENTS", assertThrows(ApiException.class, () -> indemnites.calculer(new IndemniteService.Demande(7, LocalDateTime.parse("2024-03-01T08:00"), LocalDateTime.parse("2024-03-02T08:00"), true, "voiture_de_service", null, null, null, null))).code());
    }

    @Test void leGabaritDeNumeroEstFormate() {
        assertEquals("TG-2026-00007", NumerotationMissionService.formater("{symbole}-{annee}-{n:5}", "TG", 2026, 7));
        assertEquals("7/TG/2026", NumerotationMissionService.formater("{n}/{symbole}/{annee}", "TG", 2026, 7));
    }

    @Test void numerosSequentielsParDelegationAnneeEtType() {
        String d1 = data.delegation("NUM1"), d2 = data.delegation("NUM2");
        int[] r = tx.execute(s -> new int[]{numeros.suivant(d1, 2026, NumerotationMissionService.Type.ORDM), numeros.suivant(d1, 2026, NumerotationMissionService.Type.ORDM),
                numeros.suivant(d1, 2026, NumerotationMissionService.Type.ETAT), numeros.suivant(d2, 2026, NumerotationMissionService.Type.ORDM), numeros.suivant(d1, 2027, NumerotationMissionService.Type.ORDM)});
        assertArrayEquals(new int[]{1, 2, 1, 1, 1}, r);
    }

    @Test void numerosUniquesSousCharge() throws Exception {
        String d = data.delegation("NUMC");
        var pool = Executors.newFixedThreadPool(8);
        Set<Integer> vus = ConcurrentHashMap.newKeySet();
        var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < 40; i++) futures.add(pool.submit(() -> vus.add(tx.execute(s -> numeros.suivant(d, 2026, NumerotationMissionService.Type.ORDM)))));
        for (var f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(40, vus.size(), "aucun numéro attribué deux fois");
        assertEquals(40, jdbc.queryForObject("SELECT dernier_numero FROM delegation_compteur WHERE delegation_id = ? AND annee = 2026 AND type_numero = 'ordm'", Integer.class, d));
    }

    @Test void uneTransactionAnnuleeNeLaissePasDeTrou() {
        String d = data.delegation("NUMG");
        assertEquals(1, (int) tx.execute(s -> numeros.suivant(d, 2026, NumerotationMissionService.Type.ORDM)));
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> { numeros.suivant(d, 2026, NumerotationMissionService.Type.ORDM); throw new IllegalStateException("échec avant l'émission"); }));
        assertEquals(2, (int) tx.execute(s -> numeros.suivant(d, 2026, NumerotationMissionService.Type.ORDM)), "le numéro consommé par la transaction annulée est rendu");
    }

    @Test void numerotationExigeUneTransaction() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> numeros.suivant(data.delegation("NUMT"), 2026, NumerotationMissionService.Type.ORDM));
    }

    @Test void permissionsParRoleEtSeparationDesTaches() {
        String s = data.structure(data.delegation("PERM"), null, "PERMS");
        String p = data.personnel(s, "Perm");
        String uid = data.user("cellule.dep", "rh", p, false);
        jdbc.update("UPDATE utilisateur SET role_id = (SELECT id FROM role WHERE code = 'cellule_deplacements') WHERE id = ?", uid);
        var u = new AuthenticatedUser(uid, p, Role.RH, false);
        assertTrue(permissions.a(u, "mission.valider"));
        assertFalse(permissions.a(u, "mission.valider_paiement"), "la cellule des déplacements ne valide pas le paiement");
        assertThrows(ApiException.class, () -> permissions.exiger(u, "mission.valider_paiement"));
        jdbc.update("UPDATE utilisateur SET est_actif = 0 WHERE id = ?", uid);
        assertFalse(permissions.a(u, "mission.valider"), "un compte désactivé n'a plus aucune permission");
        jdbc.update("UPDATE utilisateur SET est_actif = 1, role_id = NULL WHERE id = ?", uid);
        assertFalse(permissions.a(u, "mission.valider"), "sans rôle fin : aucune permission");
    }
}
