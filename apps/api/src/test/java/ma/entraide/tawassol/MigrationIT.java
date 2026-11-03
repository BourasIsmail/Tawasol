package ma.entraide.tawassol;

import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationIT extends IntegrationTestBase {
    @Test void lesTablesDuDumpEtLesEvolutionsExistent() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'", Integer.class);
        assertTrue(n >= 21 + 16, "tables : " + n);
    }

    @Test void donneesDeReference() {
        assertEquals(12, jdbc.queryForObject("SELECT COUNT(*) FROM coordination_region", Integer.class));
        assertEquals(12, jdbc.queryForObject("SELECT COUNT(*) FROM type_demande", Integer.class)); // 8 + 4 types de congé repris de Suivi RH (V15)
        assertEquals(0, jdbc.queryForObject("SELECT validation_responsable FROM type_demande WHERE code = 'ATTESTATION'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM jour_ferie WHERE date_ferie = '2026-11-06'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT doit_changer_mdp FROM utilisateur WHERE login = 'admin'", Integer.class));
    }

    @Test void unPerimetreDoitAvoirExactementUneCible() {
        String d = data.delegation("M1");
        String s = data.structure(d, null, "M1S");
        String p = data.personnel(s, "Mig");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO responsable_perimetre (personnel_id, type_perimetre, structure_id, delegation_id, date_debut) VALUES (?,?,?,?,CURRENT_DATE)", p, "structure", s, d));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO responsable_perimetre (personnel_id, type_perimetre, date_debut) VALUES (?,?,CURRENT_DATE)", p, "structure"));
    }

    @Test void unNumeroDeDemandeEstUnique() {
        String d = data.delegation("M2");
        String s = data.structure(d, null, "M2S");
        String p = data.personnel(s, "Mig2");
        String type = jdbc.queryForObject("SELECT id FROM type_demande WHERE code = 'AUTRE'", String.class);
        jdbc.update("INSERT INTO demande (personnel_id, type_demande_id, objet, numero) VALUES (?,?,?,?)", p, type, "o", "DEM-2026-000001");
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO demande (personnel_id, type_demande_id, objet, numero) VALUES (?,?,?,?)", p, type, "o2", "DEM-2026-000001"));
        jdbc.update("DELETE FROM demande WHERE personnel_id = ?", p);
    }

    // ---- V10 / V11 : module Déplacements et reprise de Suivi RH ----

    @Test void referencesDeDeplacementsInitialisees_nonValidees() {
        assertEquals(14, jdbc.queryForObject("SELECT COUNT(*) FROM bareme_indemnite", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bareme_indemnite WHERE statut_validation = 'valide'", Integer.class), "rien n'est officiel sans validation");
        assertEquals(java.math.BigDecimal.valueOf(8000, 2), jdbc.queryForObject("SELECT taux_repas_1 FROM bareme_indemnite WHERE echelle = 11", java.math.BigDecimal.class));
        assertEquals(15, jdbc.queryForObject("SELECT seuil_bascule_jours FROM bareme_indemnite WHERE echelle = 7", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM puissance_fiscale", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM bareme_repas", Integer.class));
    }

    @Test void separationDesTachesDansLesPermissions() {
        java.util.function.BiFunction<String, String, Boolean> a = (role, perm) -> jdbc.queryForObject("SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id = rp.role_id JOIN permission p ON p.id = rp.permission_id WHERE r.code = ? AND p.code = ?", Integer.class, role, perm) > 0;
        assertTrue(a.apply("cellule_deplacements", "mission.valider"));
        assertFalse(a.apply("cellule_deplacements", "mission.valider_paiement"), "la cellule des déplacements ne valide pas le paiement");
        assertTrue(a.apply("cellule_paie", "mission.valider_paiement"));
        assertFalse(a.apply("cellule_paie", "mission.corriger"), "la cellule de paie n'édite pas les missions");
        assertFalse(a.apply("responsable_standard", "mission.valider"));
        assertFalse(a.apply("gestionnaire_perimetre", "mission.valider_niveau1"), "qui saisit ne valide pas");
        assertFalse(a.apply("admin_technique", "mission.etats"), "l'admin technique n'accède à aucun montant nominatif");
        assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM role", Integer.class));
    }

    @Test void uneMissionPeutNAvoirPasDeDateDeRetour_etLesStatutsOntLeNouveauCircuit() {
        String d = data.delegation("M3");
        String s = data.structure(d, null, "M3S");
        String p = data.personnel(s, "Mig3");
        jdbc.update("INSERT INTO ordre_mission (id, personnel_id, objet, date_depart, annee, statut, retour_non_renseigne, source_legacy, id_legacy) VALUES (UUID(), ?, 'Mission sans retour', '2024-06-03 08:00:00', 2024, 'payee', 1, 'propre', 42)", p);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO ordre_mission (id, personnel_id, objet, date_depart, annee, statut) VALUES (UUID(), ?, 'x', '2024-06-03 08:00:00', 2024, 'en_attente')", p));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO ordre_mission (id, personnel_id, objet, date_depart, annee, source_legacy, id_legacy) VALUES (UUID(), ?, 'doublon legacy', '2024-06-04 08:00:00', 2024, 'propre', 42)", p), "une ligne source ne s'importe qu'une fois");
        jdbc.update("DELETE FROM ordre_mission WHERE personnel_id = ?", p);
    }

    @Test void signataireEtCompteursTransactionnels() {
        String d = data.delegation("M4");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO signataire_autorisation (portee, nom_fr, nom_ar, qualite_fr, qualite_ar, date_debut) VALUES ('delegation','X','س','Délégué','مندوب','2025-01-01')"), "une cible exactement");
        jdbc.update("INSERT INTO signataire_autorisation (portee, delegation_id, nom_fr, nom_ar, qualite_fr, qualite_ar, numero_autorisation, date_debut) VALUES ('delegation', ?, 'X','س','Délégué','مندوب','14/2025','2025-03-01')", d);
        jdbc.update("INSERT INTO delegation_compteur (delegation_id, annee, type_numero, dernier_numero) VALUES (?, 2026, 'ordm', 12)", d);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO delegation_compteur (delegation_id, annee, type_numero, dernier_numero) VALUES (?, 2026, 'ordm', 1)", d), "un seul compteur par délégation, année et type");
    }

    @Test void colonnesDeRepriseDuPersonnel() {
        String d = data.delegation("M5");
        String s = data.structure(d, null, "M5S");
        String p = data.personnel(s, "Mig5");
        jdbc.update("UPDATE personnel SET date_recrutement = NULL, a_completer = 1, id_legacy = 206418, situation_service = 'sortie_de_service' WHERE id = ?", p);
        String p2 = data.personnel(s, "Mig5bis");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE personnel SET id_legacy = 206418 WHERE id = ?", p2), "un identifiant legacy ne désigne qu'un agent");
        jdbc.update("INSERT INTO migration_run (id, source, mode) VALUES ('11111111-1111-4111-8111-111111111111', 'suivirh-test', 'a_blanc')");
        jdbc.update("INSERT INTO migration_map (run_id, table_legacy, id_legacy, table_cible, id_cible) VALUES ('11111111-1111-4111-8111-111111111111', 'personnes', '206418', 'personnel', ?)", p);
        jdbc.update("DELETE FROM migration_run");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM migration_map", Integer.class), "annuler une exécution retire ses correspondances");
    }

    @Test void numerosHeritesPeuventEtreEnDoubleMaisPasLesNumerosEmis() {
        String d = data.delegation("NL");
        String s = data.structure(d, null, "NLS");
        String p = data.personnel(s, "Nl");
        String ins = "INSERT INTO ordre_mission (id, personnel_id, objet, date_depart, annee, statut, numero_ordm, numero_ordm_legacy, numero_etat_legacy, anomalie_numero) VALUES (UUID(),?,?,?,?,?,?,?,?,?)";
        // deux missions migrées avec le même numéro d'état d'origine : accepté, signalé
        jdbc.update(ins, p, "o1", "2024-05-01 08:00:00", 2024, "payee", null, "0294", "PR / SB - 1 / 2024", "etat_en_double");
        jdbc.update(ins, p, "o2", "2024-05-02 08:00:00", 2024, "payee", null, "0295", "PR / SB - 1 / 2024", "etat_en_double");
        // une mission sans numéro d'origine : migrée avec son indicateur, rien d'inventé
        jdbc.update(ins, p, "o3", "2024-05-03 08:00:00", 2024, "payee", null, null, null, "numero_absent");
        // les numéros émis par TAWASSOL, eux, sont uniques
        jdbc.update(ins, p, "n1", "2026-05-01 08:00:00", 2026, "approuvee", "TG-2026-00001", null, null, "aucune");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(ins, p, "n2", "2026-05-02 08:00:00", 2026, "approuvee", "TG-2026-00001", null, null, "aucune"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ordre_mission WHERE numero_etat_legacy = 'PR / SB - 1 / 2024' AND anomalie_numero = 'etat_en_double'", Integer.class));
        jdbc.update("DELETE FROM ordre_mission WHERE personnel_id = ?", p);
    }

    @Test void statutFinaliseeEntreApprobationEtPaiement() {
        String d = data.delegation("SF");
        String p = data.personnel(data.structure(d, null, "SFS"), "Sf");
        for (String st : new String[]{"brouillon", "soumise", "validee_responsable", "approuvee", "finalisee", "payee", "rejetee", "annulee"})
            jdbc.update("INSERT INTO ordre_mission (id, personnel_id, objet, date_depart, annee, statut) VALUES (UUID(),?,?,?,?,?)", p, "o", "2026-05-01 08:00:00", 2026, st);
        assertEquals(8, jdbc.queryForObject("SELECT COUNT(DISTINCT statut) FROM ordre_mission WHERE personnel_id = ?", Integer.class, p));
        jdbc.update("DELETE FROM ordre_mission WHERE personnel_id = ?", p);
    }
}
