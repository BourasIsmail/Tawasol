package ma.entraide.tawassol.migration;

import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Reprise M2 : la « source Suivi RH » est un jeu de tables legacy (noms distincts de la cible) dans la base de test. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "tawassol.migrate.cnie-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class MigrationM2IT extends IntegrationTestBase {
    @Autowired MigrationService service;

    private static final String[] TABLES = {"coordinations", "delegations", "structures", "grades", "responsabilites", "administrations", "moyen_transports",
            "puissance_fiscales", "programmes", "holidays", "personnes", "roles", "users", "deplacement_propres", "deplacement_programmes", "deplacement_programme_statuses", "conge_types", "conges", "messages"};

    @BeforeAll void creerLaSource() {
        dropSource();
        String nt = "id BIGINT PRIMARY KEY, code VARCHAR(100), name_trans TEXT, created_at DATETIME DEFAULT '2024-06-12 21:51:33', deleted_at DATETIME NULL";
        jdbc.execute("CREATE TABLE coordinations (" + nt + ", autorisation_signature_date DATE NULL, autorisation_signature_numero VARCHAR(20) NULL, autorisation_signature_personne TEXT NULL)");
        jdbc.execute("CREATE TABLE delegations (" + nt + ", symbols VARCHAR(10), num_ordm_counter TEXT, num_etat_counter TEXT, autorisation_signature_date DATE NULL, autorisation_signature_numero VARCHAR(20) NULL, autorisation_signature_personne TEXT NULL, coordination_id BIGINT)");
        jdbc.execute("CREATE TABLE structures (" + nt + ")");
        jdbc.execute("CREATE TABLE grades (id BIGINT PRIMARY KEY, name_trans TEXT, echelle INT, taux_1 DOUBLE, taux_2 DOUBLE)");
        jdbc.execute("CREATE TABLE responsabilites (" + nt + ")");
        jdbc.execute("CREATE TABLE administrations (" + nt + ")");
        jdbc.execute("CREATE TABLE moyen_transports (" + nt + ")");
        jdbc.execute("CREATE TABLE puissance_fiscales (" + nt + ", taux DOUBLE)");
        jdbc.execute("CREATE TABLE programmes (" + nt + ", montant_max_fois TEXT, nombre_fois_ans INT)");
        jdbc.execute("CREATE TABLE holidays (id BIGINT PRIMARY KEY, nom VARCHAR(150), jour DATE, deleted_at DATETIME NULL)");
        jdbc.execute("CREATE TABLE personnes (id BIGINT PRIMARY KEY, matricule VARCHAR(20), cnie VARCHAR(30), nom_prenom_ar VARCHAR(250), nom_prenom_fr VARCHAR(250), mission TEXT, assigned_tasks TEXT, stagiaire TINYINT DEFAULT 0, administration_origine_id BIGINT, grade_id BIGINT, affectation_id BIGINT, structure_id BIGINT, situation_administrative_id BIGINT, responsabilite_id BIGINT, created_at DATETIME DEFAULT '2024-06-12 21:51:33', deleted_at DATETIME NULL, programme_id BIGINT)");
        jdbc.execute("CREATE TABLE roles (id BIGINT PRIMARY KEY, code VARCHAR(60))");
        jdbc.execute("CREATE TABLE deplacement_programme_statuses (" + nt + ")");
        jdbc.execute("CREATE TABLE conge_types (" + nt + ")");
        jdbc.execute("CREATE TABLE deplacement_propres (id BIGINT PRIMARY KEY, objet VARCHAR(200), date_depart DATETIME, date_retour DATETIME NULL, date_ordm DATE NULL, numero_ordm VARCHAR(30) NULL, numero_etat VARCHAR(60) NULL, nombre_repas_taux_1 INT NULL, indemnite_taux_1 DOUBLE NULL, nombre_repas_taux_2 INT NULL, indemnite_taux_2 DOUBLE NULL, deplacement_remboursable TINYINT DEFAULT 1, frais_transport DOUBLE NULL, matricule VARCHAR(30) NULL, nom_voiture_fr VARCHAR(100) NULL, distance_trajet DOUBLE NULL, nombre_trajet INT NULL, taux_km DOUBLE NULL, puissance_fiscale_id BIGINT NULL, annee_id INT, moyen_transport_id BIGINT, personne_id BIGINT, depart_delegation_id BIGINT NULL, arrival_delegation_id BIGINT NULL, status_id BIGINT, created_at DATETIME, updated_at DATETIME, deleted_at DATETIME NULL)");
        jdbc.execute("CREATE TABLE deplacement_programmes (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE conges (id BIGINT PRIMARY KEY, personne_id BIGINT, date_debut DATE NULL, date_fin DATE NULL, numero_demande INT NULL, date_demande DATE NULL, article_special VARCHAR(50) NULL, lieu_conge VARCHAR(100) NULL, langue_decision VARCHAR(5) NULL, type_id BIGINT, status_id BIGINT, annee_courante_id INT NULL, nombre_jours_annee_courante INT NULL, nombre_jours_annee_precedente INT NULL, objet_conge_exceptionnel VARCHAR(200) NULL, nombre_jours_conge INT NULL, remplacant_id BIGINT NULL, created_at DATETIME, updated_at DATETIME, deleted_at DATETIME NULL)");
        jdbc.execute("CREATE TABLE messages (id BIGINT PRIMARY KEY, message TEXT, response TEXT NULL, phone VARCHAR(30) NULL, user_id BIGINT NULL, created_at DATETIME, updated_at DATETIME, deleted_at DATETIME NULL)");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, is_active TINYINT, email VARCHAR(150), password VARCHAR(100), role_id BIGINT, delegation_id BIGINT, coordination_id BIGINT, structure_id BIGINT, personne_id BIGINT, deleted_at DATETIME NULL)");
    }

    @AfterAll void supprimerLaSource() { dropSource(); }

    private void dropSource() { for (String t : TABLES) jdbc.execute("DROP TABLE IF EXISTS " + t); }
    @BeforeEach void viderLaSource() { for (String t : TABLES) jdbc.update("DELETE FROM " + t); remplir(); }

    private static final String PERS = "{\"fr\":\"le délégué provincial\",\"ar\":\"المندوب الإقليمي\"}";

    private void remplir() {
        jdbc.update("INSERT INTO coordinations (id, code, name_trans, autorisation_signature_date, autorisation_signature_numero, autorisation_signature_personne) VALUES (1,'coordination_tanger','{\"fr\":\"Tanger\",\"ar\":\"طنجة\"}','2022-06-02','61',?), (13,'siege_centrale','{\"fr\":\"Siège Centrale\",\"ar\":\"المقر المركزي\"}',NULL,NULL,NULL)", PERS);
        jdbc.update("INSERT INTO delegations (id, symbols, code, name_trans, num_ordm_counter, num_etat_counter, autorisation_signature_date, autorisation_signature_numero, autorisation_signature_personne, coordination_id) VALUES (1,'TG','d_tg','{\"fr\":\"Tanger-Assilah\",\"ar\":\"طنجة\"}','[]','{\"2024\": 18}','2022-06-02','61',?,1), (83,'SC','d_sc','{\"fr\":\"Siège Centrale\",\"ar\":\"المقر\"}','[]','[]',NULL,NULL,NULL,13)", PERS);
        jdbc.update("INSERT INTO structures (id, code, name_trans) VALUES (1,'direction_x','{\"fr\":\"Direction X\",\"ar\":\"مديرية\"}')");
        jdbc.update("INSERT INTO grades (id, name_trans, echelle, taux_1, taux_2) VALUES (1,'{\"fr\":\"Agent\",\"ar\":\"عون\"}',7,40,35), (2,'{\"fr\":\"Adjoint\",\"ar\":\"مساعد\"}',9,61,48)");
        jdbc.update("INSERT INTO responsabilites (id, code, name_trans) VALUES (1,'directeur','{\"fr\":\"Directeur\",\"ar\":\"مدير\"}')");
        jdbc.update("INSERT INTO administrations (id, code, name_trans) VALUES (1,'en','{\"fr\":\"Entraide Nationale\",\"ar\":\"التعاون\"}')");
        jdbc.update("INSERT INTO moyen_transports (id, code, name_trans) VALUES (3,'voiture_privee','{\"fr\":\"Voiture privée\",\"ar\":\"سيارة\"}')");
        jdbc.update("INSERT INTO puissance_fiscales (id, code, name_trans, taux) VALUES (6,'six_or_less','{\"fr\":\"6 ou moins\",\"ar\":\"6\"}',1.2), (7,'seven','{\"fr\":\"7\",\"ar\":\"7\"}',1.99)");
        jdbc.update("INSERT INTO programmes (id, code, name_trans, montant_max_fois, nombre_fois_ans) VALUES (1,'personnelle_de_entraide_nationale','{\"fr\":\"Personnel\",\"ar\":\"موظفون\"}','{\"1\":1500,\"10\":2000}',0)");
        jdbc.update("INSERT INTO holidays (id, nom, jour) VALUES (1,'Fête test 1','2031-01-01'), (2,'Aïd test 1','2031-03-02')");
        jdbc.update("INSERT INTO personnes (id, matricule, cnie, nom_prenom_ar, nom_prenom_fr, grade_id, affectation_id, structure_id, situation_administrative_id, responsabilite_id, programme_id, administration_origine_id) VALUES " +
                "(10,'P9001','AB123456','عبد الله عكراش','AKRACHE ABDELLAH',1,1,NULL,1,NULL,1,1)," +
                "(11,'P9002','AB123456','فوزية بن رشيد','BENRACHID FOUZIA',2,83,1,2,1,NULL,1)," +
                "(12,'P9001','ZZ999999','x y','X Y',1,1,NULL,1,NULL,NULL,1)," +
                "(13,'P9003',NULL,'أحمد بنيس','BENIS AHMED',1,NULL,NULL,1,NULL,NULL,1)");
        jdbc.update("INSERT INTO deplacement_programme_statuses (id, code, name_trans) VALUES (1,'generated','{}'),(2,'created','{}'),(3,'modified','{}'),(4,'cancelled','{}'),(5,'finalized','{}'),(6,'paid','{}')");
        jdbc.update("INSERT INTO conge_types (id, code, name_trans) VALUES (1,'conge_administratif_annuel','{\"fr\":\"Congé annuel\"}'),(3,'permission_absence','{\"fr\":\"Permission absence\"}')");
        jdbc.update("INSERT INTO moyen_transports (id, code, name_trans) VALUES (1,'transports_en_commun','{\"fr\":\"TC\",\"ar\":\"TC\"}'),(2,'voiture_de_service','{\"fr\":\"VS\",\"ar\":\"VS\"}')");
        String dp = "INSERT INTO deplacement_propres (id, objet, date_depart, date_retour, date_ordm, numero_ordm, numero_etat, nombre_repas_taux_1, indemnite_taux_1, nombre_repas_taux_2, indemnite_taux_2, deplacement_remboursable, frais_transport, matricule, nom_voiture_fr, distance_trajet, nombre_trajet, taux_km, puissance_fiscale_id, annee_id, moyen_transport_id, personne_id, depart_delegation_id, arrival_delegation_id, status_id, created_at, updated_at, deleted_at) VALUES ";
        // 1 : finalisée, transports en commun ; 2 : créée sans numéro, sans retour ; 3 : payée en voiture privée ; 4 : même numéro d'état que la 1 mais supprimée
        jdbc.update(dp + "(1,'Mission A','2024-07-01 08:00:00','2024-07-01 20:00:00','2024-06-28','0294','PR / TG - 1 / 2024',3,40,0,35,1,100.50,NULL,NULL,NULL,NULL,NULL,NULL,2024,1,10,1,83,5,'2024-06-28 10:00:00','2024-07-02 10:00:00',NULL)," +
                "(2,'Mission B','2024-07-05 08:00:00',NULL,NULL,NULL,NULL,0,0,0,0,1,0,NULL,NULL,NULL,NULL,NULL,NULL,2024,2,10,1,83,2,'2024-07-03 10:00:00','2024-07-03 10:00:00',NULL)," +
                "(3,'Mission C','2024-07-10 08:00:00','2024-07-10 20:00:00','2024-07-08','0295','PR / TG - 2 / 2024',3,40,0,35,1,0,'12345-A-6','Dacia',100,2,1.2,6,2024,2,10,1,83,6,'2024-07-08 10:00:00','2024-07-11 10:00:00',NULL)," +
                "(4,'Mission A bis','2024-07-02 08:00:00','2024-07-02 20:00:00','2024-06-28','0296','PR / TG - 1 / 2024',3,40,0,35,1,0,NULL,NULL,NULL,NULL,NULL,NULL,2024,2,10,1,83,5,'2024-06-28 10:00:00','2024-07-02 10:00:00','2024-07-03 10:00:00')");
        jdbc.update("INSERT INTO conges (id, personne_id, date_debut, date_fin, numero_demande, type_id, status_id, nombre_jours_annee_courante, nombre_jours_annee_precedente, created_at, updated_at) VALUES (1,10,'2024-08-01','2024-08-10',843,1,5,8,0,'2024-07-20 10:00:00','2024-07-20 10:00:00'), (2,10,'0004-01-02','2024-12-06',12,1,2,0,5,'2024-07-20 10:00:00','2024-07-20 10:00:00')");
        jdbc.update("INSERT INTO messages (id, message, response, user_id, created_at, updated_at) VALUES (1,'Bonjour','Réponse',1,'2024-06-01 10:00:00','2024-06-01 10:00:00')");
        jdbc.update("INSERT INTO roles (id, code) VALUES (1,'super_administrateur'), (4,'coordination'), (9,'inconnu')");
        jdbc.update("INSERT INTO users (id, is_active, email, password, role_id, coordination_id) VALUES (1,1,'SuperAdmin@Legacy.ma','$2y$12$abcdefghijklmnopqrstuv',1,NULL), (8,1,'coor.tanger@legacy.ma','$2y$12$abcdefghijklmnopqrstuv',4,1), (9,1,'x@legacy.ma','$2y$12$abc',9,NULL)");
    }

    private static int i(Object o) { return o instanceof Boolean b ? (b ? 1 : 0) : ((Number) o).intValue(); }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> t(MigrationService.Resultat r, String table) { return (Map<String, Integer>) ((Map<String, Object>) r.rapport().get("tables")).get(table); }

    @Test void essaiABlancNeConserveRien() {
        int personnelAvant = jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class);
        int regionsAvant = jdbc.queryForObject("SELECT COUNT(*) FROM coordination_region", Integer.class);
        var r = service.executer(jdbc, false, null);
        assertEquals(personnelAvant, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
        assertEquals(regionsAvant, jdbc.queryForObject("SELECT COUNT(*) FROM coordination_region", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM migration_map WHERE run_id = ?", Integer.class, r.runId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM migration_run WHERE id = ? AND mode = 'a_blanc' AND statut = 'terminee'", Integer.class, r.runId()));
        assertEquals(4, t(r, "personnes").get("lus"));
        assertTrue(t(r, "personnes").get("crees") > 0);
        jdbc.update("DELETE FROM migration_run WHERE id = ?", r.runId());
    }

    @Test @Transactional void laMigrationReelleExigeUneValidationEcrite() {
        assertThrows(IllegalStateException.class, () -> service.executer(jdbc, true, null));
        assertThrows(IllegalStateException.class, () -> service.executer(jdbc, true, "  "));
    }

    @Test @Transactional void reprendLesReferentielsEtLePersonnel() {
        var r = service.executer(jdbc, true, "VALIDATION-TEST-1");
        // régions : R01 existante rattachée, siège créé
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM coordination_region WHERE code_region = 'R01' AND id_legacy = 1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM coordination_region WHERE code_region = 'SIEGE' AND est_siege = 1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delegation_province WHERE symbole = 'SC' AND est_siege = 1", Integer.class));
        // compteurs et signataires (sans nom : il reste à renseigner)
        assertEquals(18, jdbc.queryForObject("SELECT dernier_numero FROM delegation_compteur c JOIN delegation_province d ON d.id = c.delegation_id WHERE d.symbole = 'TG' AND c.annee = 2024 AND c.type_numero = 'etat'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM signataire_autorisation WHERE nom_fr = '' AND date_debut = '2022-06-02'", Integer.class));
        // programmes : plafonds datés, non validés
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM programme_mission_plafond WHERE statut_validation = 'non_valide'", Integer.class));
        // fériés : l'Aïd est à confirmer
        assertEquals(1, jdbc.queryForObject("SELECT a_confirmer FROM jour_ferie WHERE date_ferie = '2031-03-02' AND type_ferie = 'religieux'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT a_confirmer FROM jour_ferie WHERE date_ferie = '2031-01-01'", Integer.class));
        // taux kilométrique différent de la cible : signalé, jamais écrasé
        assertEquals(java.math.BigDecimal.valueOf(175, 2), jdbc.queryForObject("SELECT taux_km FROM puissance_fiscale WHERE code = 'seven'", java.math.BigDecimal.class));

        // personnel : une seule personne reprise sur 4 (doublon de matricule, CNIE en double reprise, affectation inconnue)
        assertEquals(2, t(r, "personnes").get("crees"));
        assertEquals(2, t(r, "personnes").get("ignores"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE matricule = 'P9001' AND nom_fr = 'AKRACHE' AND prenom_fr = 'ABDELLAH' AND nom_ar = 'عكراش' AND nom_prenom_fr_legacy = 'AKRACHE ABDELLAH' AND est_actif = 1 AND a_completer = 1 AND date_recrutement IS NULL", Integer.class));
        // CNIE : chiffrée, jamais en clair, empreinte présente ; la colonne cin reste vide
        Map<String, Object> p = jdbc.queryForMap("SELECT cnie_chiffre, cnie_empreinte, cin FROM personnel WHERE matricule = 'P9001'");
        assertNotNull(p.get("cnie_chiffre")); assertEquals(64, ((String) p.get("cnie_empreinte")).length()); assertNull(p.get("cin"));
        assertFalse(new String((byte[]) p.get("cnie_chiffre"), java.nio.charset.StandardCharsets.ISO_8859_1).contains("AB123456"));
        // personne rattachée à une unité du siège : structure réelle, sortie de service = inactive, CNIE en double non rattachée
        Map<String, Object> q = jdbc.queryForMap("SELECT est_actif, situation_service, a_completer, cnie_empreinte, unite_administrative_id, structure_id FROM personnel WHERE matricule = 'P9002'");
        assertEquals(0, i(q.get("est_actif"))); assertEquals("sortie_de_service", q.get("situation_service"));
        assertEquals(0, i(q.get("a_completer"))); assertNull(q.get("cnie_empreinte")); assertEquals(q.get("unite_administrative_id"), q.get("structure_id"));
        // programme du personnel
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM personnel_programme", Integer.class));

        // comptes d'entité : désactivés, mot de passe à changer, jamais perdus
        assertEquals(2, t(r, "users").get("crees")); assertEquals(1, t(r, "users").get("ignores"));
        Map<String, Object> u = jdbc.queryForMap("SELECT est_actif, doit_changer_mdp, est_compte_legacy, role, personnel_id FROM utilisateur WHERE login = 'superadmin@legacy.ma'");
        assertEquals(0, i(u.get("est_actif"))); assertEquals(1, i(u.get("doit_changer_mdp")));
        assertEquals(1, i(u.get("est_compte_legacy"))); assertEquals("admin", u.get("role")); assertNull(u.get("personnel_id"));
        assertEquals("responsable", jdbc.queryForObject("SELECT role FROM utilisateur WHERE login = 'coor.tanger@legacy.ma'", String.class));

        // anomalies et contrôles
        @SuppressWarnings("unchecked") Map<String, Long> codes = (Map<String, Long>) r.rapport().get("anomalies_par_code");
        for (String c : List.of("MATRICULE_EN_DOUBLE", "AFFECTATION_INCONNUE", "CNIE_EN_DOUBLE", "TAUX_KM_DIFFERENT", "BAREME_DIFFERENT", "ROLE_INCONNU"))
            assertTrue(codes.containsKey(c), "anomalie attendue : " + c + " dans " + codes);
        assertFalse(r.rapport().toString().contains("AB123456"), "la CNIE ne doit apparaître dans aucun rapport");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM migration_map WHERE run_id = ?", Integer.class, r.runId()) > 10);
    }

    @Test @Transactional void rejouerNeCreeAucunDoublon() {
        service.executer(jdbc, true, "VALIDATION-TEST-2");
        int personnel = jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class);
        int signataires = jdbc.queryForObject("SELECT COUNT(*) FROM signataire_autorisation", Integer.class);
        int plafonds = jdbc.queryForObject("SELECT COUNT(*) FROM programme_mission_plafond", Integer.class);
        var r2 = service.executer(jdbc, true, "VALIDATION-TEST-2");
        assertEquals(personnel, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
        assertEquals(signataires, jdbc.queryForObject("SELECT COUNT(*) FROM signataire_autorisation", Integer.class));
        assertEquals(plafonds, jdbc.queryForObject("SELECT COUNT(*) FROM programme_mission_plafond", Integer.class));
        assertEquals(0, t(r2, "personnes").get("crees"));
        assertEquals(2, t(r2, "personnes").get("deja_presents"));
        assertEquals(0, t(r2, "grades").get("crees"));
    }

    @Test @Transactional void annulerUneReprisePar_migration_run() {
        var r = service.executer(jdbc, true, "VALIDATION-TEST-3");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM migration_map WHERE run_id = ?", Integer.class, r.runId()) > 0);
        jdbc.update("DELETE FROM migration_run WHERE id = ?", r.runId());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM migration_map WHERE run_id = ?", Integer.class, r.runId()), "la suppression d'une reprise supprime ses correspondances");
    }

    @Test @Transactional void reprendLesDeplacementsLesCongesEtReconcilie() {
        var r = service.executer(jdbc, true, "VALIDATION-TEST-M4");
        assertEquals(4, t(r, "deplacement_propres").get("crees"));
        // statuts : créée avec numéro = ordre émis (approuvée) ; créée sans numéro = soumise ; finalisée ; payée ; annulée absente ici
        assertEquals("finalisee", jdbc.queryForObject("SELECT statut FROM ordre_mission WHERE source_legacy = 'propre' AND id_legacy = 1", String.class));
        assertEquals("soumise", jdbc.queryForObject("SELECT statut FROM ordre_mission WHERE source_legacy = 'propre' AND id_legacy = 2", String.class));
        assertEquals("payee", jdbc.queryForObject("SELECT statut FROM ordre_mission WHERE source_legacy = 'propre' AND id_legacy = 3", String.class));
        // numéros d'origine conservés, jamais reformatés ; numéro d'ordre TAWASSOL vide ; anomalies signalées sans rien inventer
        Map<String, Object> m1 = jdbc.queryForMap("SELECT numero_ordm, numero_ordm_legacy, numero_etat_legacy, anomalie_numero, date_validation_approximative FROM ordre_mission WHERE source_legacy = 'propre' AND id_legacy = 1");
        assertNull(m1.get("numero_ordm")); assertEquals("0294", m1.get("numero_ordm_legacy")); assertEquals("PR / TG - 1 / 2024", m1.get("numero_etat_legacy")); assertEquals("etat_en_double", m1.get("anomalie_numero"));
        assertEquals("numero_absent", jdbc.queryForObject("SELECT anomalie_numero FROM ordre_mission WHERE source_legacy = 'propre' AND id_legacy = 2", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT retour_non_renseigne FROM ordre_mission WHERE source_legacy = 'propre' AND id_legacy = 2", Integer.class));
        // montants figés tels que stockés : 3 x 40 + 0 x 35 = 120 ; frais retenus seulement pour les transports en commun ; km = 100 x 2 x 1,2
        assertEquals(0, new java.math.BigDecimal("220.50").compareTo(jdbc.queryForObject("SELECT montant_total FROM ordre_mission WHERE source_legacy = 'propre' AND id_legacy = 1", java.math.BigDecimal.class)));
        assertEquals(0, new java.math.BigDecimal("360.00").compareTo(jdbc.queryForObject("SELECT montant_total FROM ordre_mission WHERE source_legacy = 'propre' AND id_legacy = 3", java.math.BigDecimal.class)));
        assertEquals("12345-A-6", jdbc.queryForObject("SELECT vehicule_immatriculation FROM ordre_mission WHERE source_legacy = 'propre' AND id_legacy = 3", String.class));
        // la mission supprimée est reprise avec sa date de suppression
        assertNotNull(jdbc.queryForObject("SELECT supprime_le FROM ordre_mission WHERE source_legacy = 'propre' AND id_legacy = 4", java.sql.Timestamp.class));
        // une seule ligne d'historique « migré », sans acteur
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ordre_mission_historique h JOIN ordre_mission om ON om.id = h.ordre_mission_id WHERE om.id_legacy = 1 AND h.migre = 1 AND h.acteur_id IS NULL", Integer.class));
        // compteurs initialisés au maximum de l'ancien système, lignes supprimées comprises (aucune collision possible)
        assertEquals(296, jdbc.queryForObject("SELECT dernier_numero FROM delegation_compteur c JOIN delegation_province d ON d.id = c.delegation_id WHERE d.symbole = 'TG' AND c.annee = 2024 AND c.type_numero = 'ordm'", Integer.class));
        assertEquals(18, jdbc.queryForObject("SELECT dernier_numero FROM delegation_compteur c JOIN delegation_province d ON d.id = c.delegation_id WHERE d.symbole = 'TG' AND c.annee = 2024 AND c.type_numero = 'etat'", Integer.class), "le compteur JSON (18) l'emporte sur les numéros repris (2)");
        // congés : statut, type, date invalide laissée vide, historique migré
        assertEquals("approuvee", jdbc.queryForObject("SELECT statut FROM demande WHERE source_legacy = 'conge' AND id_legacy = 1", String.class));
        assertEquals("soumise", jdbc.queryForObject("SELECT statut FROM demande WHERE source_legacy = 'conge' AND id_legacy = 2", String.class));
        assertNull(jdbc.queryForObject("SELECT date_debut FROM demande WHERE source_legacy = 'conge' AND id_legacy = 2", java.sql.Date.class));
        // message archivé, jamais perdu
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM legacy_archive WHERE table_legacy = 'messages'", Integer.class));
        // réconciliation : écart nul au centime
        @SuppressWarnings("unchecked") Map<String, Map<String, Object>> rec = (Map<String, Map<String, Object>>) ((Map<String, Object>) r.rapport().get("tables")).get("reconciliation");
        for (String k : List.of("somme_repas", "somme_transport", "somme_km")) assertEquals(0, ((java.math.BigDecimal) rec.get(k).get("ecart")).signum(), k);
        @SuppressWarnings("unchecked") Map<String, Long> codes = (Map<String, Long>) r.rapport().get("anomalies_par_code");
        for (String c : List.of("NUMERO_ABSENT", "RETOUR_NON_RENSEIGNE", "ETAT_EN_DOUBLE", "DATE_CONGE_INVALIDE", "SOLDES_A_INITIALISER")) assertTrue(codes.containsKey(c), c + " dans " + codes);
        // rejouer ne duplique rien
        var r2 = service.executer(jdbc, true, "VALIDATION-TEST-M4");
        assertEquals(0, t(r2, "deplacement_propres").get("crees")); assertEquals(4, t(r2, "deplacement_propres").get("deja_presents"));
        assertEquals(0, t(r2, "conges").get("crees"));
    }
}
