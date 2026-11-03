-- V10 : module Déplacements (annexe Suivi RH, phase M1). Schéma seulement : aucune donnée migrée ici.
-- Toutes les valeurs de barème sont des paramètres datés, avec référence du texte et statut de validation (« non_valide » par défaut).

-- 1. Territoire : symbole de délégation (préfixe des numéros), siège sans identifiant codé en dur
ALTER TABLE delegation_province
  ADD COLUMN symbole varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN est_siege tinyint(1) NOT NULL DEFAULT '0',
  ADD UNIQUE KEY uq_delegation_symbole (symbole);
ALTER TABLE coordination_region ADD COLUMN est_siege tinyint(1) NOT NULL DEFAULT '0';

-- 2. Puissance fiscale (indemnité kilométrique), datée
CREATE TABLE puissance_fiscale (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  code varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle_fr varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle_ar varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  taux_km decimal(6,2) NOT NULL,
  date_effet date NOT NULL,
  date_fin date DEFAULT NULL,
  reference_texte varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  statut_validation enum('non_valide','valide') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'non_valide',
  PRIMARY KEY (id),
  UNIQUE KEY uq_puissance_code_effet (code, date_effet)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Barème d'indemnité de repas par échelle, avec seuil de bascule au taux 2
CREATE TABLE bareme_indemnite (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  echelle int NOT NULL,
  taux_repas_1 decimal(8,2) NOT NULL,
  taux_repas_2 decimal(8,2) NOT NULL,
  seuil_bascule_jours int NOT NULL DEFAULT '15',
  date_effet date NOT NULL,
  date_fin date DEFAULT NULL,
  reference_texte varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  statut_validation enum('non_valide','valide') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'non_valide',
  PRIMARY KEY (id),
  UNIQUE KEY uq_bareme_echelle_effet (echelle, date_effet)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Seuils de décompte des repas (présence puis intervalle), paramétrables et datés
CREATE TABLE bareme_repas (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  repas enum('petit_dejeuner','dejeuner','diner') COLLATE utf8mb4_unicode_ci NOT NULL,
  seuil_presence_min int NOT NULL COMMENT 'Minutes de présence pour compter le repas',
  intervalle_min int NOT NULL COMMENT 'Minutes écoulées avant le repas suivant',
  date_effet date NOT NULL,
  date_fin date DEFAULT NULL,
  reference_texte varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  statut_validation enum('non_valide','valide') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'non_valide',
  PRIMARY KEY (id),
  UNIQUE KEY uq_bareme_repas_effet (repas, date_effet)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Programmes : cycles par an, plafonds par échelle, rattachement des agents
ALTER TABLE programme_mission ADD COLUMN nombre_cycles_an int NOT NULL DEFAULT '0';

CREATE TABLE programme_mission_plafond (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  programme_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  echelle int NOT NULL,
  plafond decimal(10,2) NOT NULL,
  date_effet date NOT NULL,
  reference_texte varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  statut_validation enum('non_valide','valide') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'non_valide',
  PRIMARY KEY (id),
  UNIQUE KEY uq_plafond (programme_id, echelle, date_effet),
  CONSTRAINT fk_plafond_programme FOREIGN KEY (programme_id) REFERENCES programme_mission (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE personnel_programme (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  personnel_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  programme_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  date_debut date NOT NULL,
  date_fin date DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_pp_personnel (personnel_id, date_debut),
  KEY idx_pp_programme (programme_id),
  CONSTRAINT fk_pp_personnel FOREIGN KEY (personnel_id) REFERENCES personnel (id) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_pp_programme FOREIGN KEY (programme_id) REFERENCES programme_mission (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Compteurs transactionnels par délégation, année et type (lignes verrouillées par SELECT ... FOR UPDATE)
CREATE TABLE delegation_compteur (
  delegation_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  annee smallint NOT NULL,
  type_numero enum('ordm','etat') COLLATE utf8mb4_unicode_ci NOT NULL,
  dernier_numero int NOT NULL DEFAULT '0',
  PRIMARY KEY (delegation_id, annee, type_numero),
  CONSTRAINT fk_dc_delegation FOREIGN KEY (delegation_id) REFERENCES delegation_province (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Signataires habilités, historisés (portée : une délégation ou une coordination)
CREATE TABLE signataire_autorisation (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  portee enum('delegation','coordination') COLLATE utf8mb4_unicode_ci NOT NULL,
  delegation_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  coordination_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  nom_fr varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  nom_ar varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  qualite_fr varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  qualite_ar varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  numero_autorisation varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  date_autorisation date DEFAULT NULL,
  date_debut date NOT NULL,
  date_fin date DEFAULT NULL,
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sig_delegation (delegation_id, date_debut),
  KEY idx_sig_coordination (coordination_id, date_debut),
  CONSTRAINT fk_sig_delegation FOREIGN KEY (delegation_id) REFERENCES delegation_province (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sig_coordination FOREIGN KEY (coordination_id) REFERENCES coordination_region (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_sig_portee CHECK (
    (portee = 'delegation' AND delegation_id IS NOT NULL AND coordination_id IS NULL)
    OR (portee = 'coordination' AND coordination_id IS NOT NULL AND delegation_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Ordre de mission : circuit complet, véhicule, montants, traçabilité de l'origine
ALTER TABLE ordre_mission
  MODIFY COLUMN statut enum('en_attente','approuve','rejete','annule','brouillon','soumise','validee_responsable','approuvee','payee','rejetee','annulee') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'soumise';
UPDATE ordre_mission SET statut = CASE statut WHEN 'en_attente' THEN 'soumise' WHEN 'approuve' THEN 'approuvee' WHEN 'rejete' THEN 'rejetee' WHEN 'annule' THEN 'annulee' ELSE statut END;
ALTER TABLE ordre_mission
  MODIFY COLUMN statut enum('brouillon','soumise','validee_responsable','approuvee','payee','rejetee','annulee') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'soumise',
  MODIFY COLUMN date_retour datetime DEFAULT NULL,
  ADD COLUMN origine enum('saisie','generation') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'saisie',
  ADD COLUMN source_legacy enum('propre','programme') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN id_legacy bigint DEFAULT NULL,
  ADD COLUMN parent_ordre_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN deplacement_remboursable tinyint(1) NOT NULL DEFAULT '1',
  ADD COLUMN frais_transport decimal(10,2) NOT NULL DEFAULT '0.00',
  ADD COLUMN vehicule_immatriculation varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN vehicule_nom varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN puissance_fiscale_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN distance_km decimal(8,2) DEFAULT NULL,
  ADD COLUMN nombre_trajets int DEFAULT NULL,
  ADD COLUMN taux_km decimal(6,2) DEFAULT NULL,
  ADD COLUMN montant_indemnites decimal(10,2) NOT NULL DEFAULT '0.00',
  ADD COLUMN montant_km decimal(10,2) NOT NULL DEFAULT '0.00',
  ADD COLUMN paye_par char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN date_paiement datetime DEFAULT NULL,
  ADD COLUMN date_validation_approximative tinyint(1) NOT NULL DEFAULT '0',
  ADD COLUMN modifie tinyint(1) NOT NULL DEFAULT '0',
  ADD COLUMN retour_non_renseigne tinyint(1) NOT NULL DEFAULT '0',
  ADD COLUMN anomalie_depart_arrivee tinyint(1) NOT NULL DEFAULT '0',
  ADD COLUMN ecart_moteur_historique tinyint(1) NOT NULL DEFAULT '0',
  ADD COLUMN supprime_le datetime DEFAULT NULL,
  ADD UNIQUE KEY uq_om_legacy (source_legacy, id_legacy),
  ADD KEY idx_om_numero (numero_ordm),
  ADD KEY idx_om_etat (numero_etat),
  ADD KEY idx_om_parent (parent_ordre_id),
  ADD KEY idx_om_payeur (paye_par),
  ADD CONSTRAINT fk_om_parent FOREIGN KEY (parent_ordre_id) REFERENCES ordre_mission (id) ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT fk_om_puissance FOREIGN KEY (puissance_fiscale_id) REFERENCES puissance_fiscale (id) ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT fk_om_payeur FOREIGN KEY (paye_par) REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE;

CREATE TABLE ordre_mission_historique (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  ordre_mission_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  acteur_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ancien_statut varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  nouveau_statut varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  commentaire text COLLATE utf8mb4_unicode_ci,
  migre tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Ligne créée par la migration : auteur inconnu',
  cree_le datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  seq bigint NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (id),
  UNIQUE KEY uq_omh_seq (seq),
  KEY idx_omh_ordre (ordre_mission_id),
  CONSTRAINT fk_omh_ordre FOREIGN KEY (ordre_mission_id) REFERENCES ordre_mission (id) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_omh_acteur FOREIGN KEY (acteur_id) REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Journal et correspondance de migration (rejouable, réversible)
CREATE TABLE migration_run (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  source varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  mode enum('a_blanc','reel') COLLATE utf8mb4_unicode_ci NOT NULL,
  statut enum('en_cours','terminee','echouee','annulee') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'en_cours',
  debut datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fin datetime DEFAULT NULL,
  rapport json DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE migration_map (
  run_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  table_legacy varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  id_legacy varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  table_cible varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  id_cible char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  empreinte char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SHA-256 de la ligne source, pour détecter les changements entre deux exécutions',
  cree_le datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (run_id, table_legacy, id_legacy),
  KEY idx_mm_source (table_legacy, id_legacy),
  KEY idx_mm_cible (table_cible, id_cible),
  CONSTRAINT fk_mm_run FOREIGN KEY (run_id) REFERENCES migration_run (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Valeurs initiales de référence lues dans Suivi RH (juin 2024). Toutes « non_valide » : à faire confirmer avant production.
INSERT INTO puissance_fiscale (code, libelle_fr, libelle_ar, taux_km, date_effet) VALUES
('six_or_less','6 ou moins','6 فما دون',1.20,'2024-01-01'),('seven','7','7',1.75,'2024-01-01'),('eight','8','8',1.75,'2024-01-01'),
('nine','9','9',1.75,'2024-01-01'),('ten_or_more','10 ou plus','10 فما فوق',2.30,'2024-01-01');
INSERT INTO bareme_indemnite (echelle, taux_repas_1, taux_repas_2, date_effet)
SELECT e.echelle, b.t1, b.t2, '2024-01-01' FROM
 (SELECT 1 AS echelle UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 99) e
 JOIN (SELECT 1 AS lo, 7 AS hi, 40 AS t1, 35 AS t2 UNION SELECT 8, 10, 60, 48 UNION SELECT 11, 11, 80, 65 UNION SELECT 12, 13, 40, 35 UNION SELECT 99, 99, 100, 80) b ON e.echelle BETWEEN b.lo AND b.hi;
INSERT INTO bareme_repas (repas, seuil_presence_min, intervalle_min, date_effet) VALUES
('petit_dejeuner',300,360,'2024-06-13'),('dejeuner',180,240,'2024-06-13'),('diner',180,180,'2024-06-13');
INSERT INTO parametre (cle, valeur, date_effet, reference_texte, statut_validation) VALUES
('mission.repas.creneau_petit_dejeuner_apres','18:00','2024-06-13',NULL,'non_valide'),
('mission.repas.creneau_dejeuner_jusqua','11:00','2024-06-13',NULL,'non_valide'),
('mission.repas.creneau_diner_jusqua','18:00','2024-06-13',NULL,'non_valide'),
('mission.repas.repas_par_jour','3','2024-06-13',NULL,'non_valide');
