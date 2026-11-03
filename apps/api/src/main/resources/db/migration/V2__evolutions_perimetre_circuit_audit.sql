-- V2 : évolutions du cahier des charges (§4). Ne modifie jamais V1.
-- Conventions : UUID char(36), utf8mb4_unicode_ci, InnoDB, cree_le/modifie_le, soft delete via est_actif,
-- ENUM pour les statuts, DECIMAL pour tous les montants.

-- 1. Périmètre des responsables : exactement une cible renseignée
-- MySQL interdit une action référentielle (ON UPDATE CASCADE) sur une colonne utilisée dans un CHECK : on met RESTRICT
-- sur les colonnes concernées (les UUID ne changent jamais).
CREATE TABLE responsable_perimetre (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  personnel_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  type_perimetre enum('structure','delegation','coordination') COLLATE utf8mb4_unicode_ci NOT NULL,
  structure_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  delegation_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  coordination_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  date_debut date NOT NULL,
  date_fin date DEFAULT NULL,
  est_actif tinyint(1) NOT NULL DEFAULT '1',
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modifie_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rp_personnel (personnel_id),
  KEY idx_rp_structure (structure_id),
  KEY idx_rp_delegation (delegation_id),
  KEY idx_rp_coordination (coordination_id),
  CONSTRAINT fk_rp_personnel FOREIGN KEY (personnel_id) REFERENCES personnel (id) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_rp_structure FOREIGN KEY (structure_id) REFERENCES structure (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_rp_delegation FOREIGN KEY (delegation_id) REFERENCES delegation_province (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_rp_coordination FOREIGN KEY (coordination_id) REFERENCES coordination_region (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_rp_une_cible CHECK (
    (structure_id IS NOT NULL) + (delegation_id IS NOT NULL) + (coordination_id IS NOT NULL) = 1
    AND ((type_perimetre = 'structure' AND structure_id IS NOT NULL)
      OR (type_perimetre = 'delegation' AND delegation_id IS NOT NULL)
      OR (type_perimetre = 'coordination' AND coordination_id IS NOT NULL))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Suppléance
CREATE TABLE suppleance (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  responsable_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  suppleant_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  date_debut date NOT NULL,
  date_fin date NOT NULL,
  est_actif tinyint(1) NOT NULL DEFAULT '1',
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_supp_responsable (responsable_id),
  KEY idx_supp_suppleant (suppleant_id, date_debut, date_fin),
  CONSTRAINT fk_supp_responsable FOREIGN KEY (responsable_id) REFERENCES personnel (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_supp_suppleant FOREIGN KEY (suppleant_id) REFERENCES personnel (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_supp_dates CHECK (date_fin >= date_debut),
  CONSTRAINT ck_supp_distincts CHECK (responsable_id <> suppleant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Compteurs de numéros lisibles (unique et séquentiel par année)
CREATE TABLE compteur_numero (
  cle varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  annee smallint NOT NULL,
  valeur int NOT NULL DEFAULT '0',
  PRIMARY KEY (cle, annee)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Demande : circuit à deux niveaux
ALTER TABLE demande
  MODIFY COLUMN statut enum('brouillon','soumise','validee_responsable','rejetee_responsable','approuvee','rejetee','annulee')
    COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'brouillon',
  ADD COLUMN numero varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER id,
  ADD COLUMN date_debut date DEFAULT NULL,
  ADD COLUMN date_fin date DEFAULT NULL,
  ADD COLUMN nb_jours decimal(5,1) DEFAULT NULL,
  ADD COLUMN valide_par char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN date_validation_responsable datetime DEFAULT NULL,
  ADD COLUMN commentaire_responsable text COLLATE utf8mb4_unicode_ci,
  ADD COLUMN donnees_json json DEFAULT NULL,
  ADD UNIQUE KEY uq_demande_numero (numero),
  ADD KEY idx_demande_valideur (valide_par),
  ADD CONSTRAINT fk_demande_valideur FOREIGN KEY (valide_par) REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE;

CREATE TABLE demande_historique (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  demande_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  acteur_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ancien_statut varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  nouveau_statut varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  commentaire text COLLATE utf8mb4_unicode_ci,
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_dh_demande (demande_id, cree_le),
  KEY idx_dh_acteur (acteur_id),
  CONSTRAINT fk_dh_demande FOREIGN KEY (demande_id) REFERENCES demande (id) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_dh_acteur FOREIGN KEY (acteur_id) REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Type de demande : validation du responsable paramétrable par type
ALTER TABLE type_demande
  ADD COLUMN validation_responsable tinyint(1) NOT NULL DEFAULT '1',
  ADD COLUMN delai_reponse_jours int DEFAULT NULL COMMENT 'Délai réglementaire de réponse : à valider';

-- 5. Solde de congés
CREATE TABLE solde_conge (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  personnel_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  annee smallint NOT NULL,
  droit decimal(5,1) NOT NULL DEFAULT '0.0',
  pris decimal(5,1) NOT NULL DEFAULT '0.0',
  reporte decimal(5,1) NOT NULL DEFAULT '0.0',
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modifie_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_solde_personnel_annee (personnel_id, annee),
  CONSTRAINT fk_solde_personnel FOREIGN KEY (personnel_id) REFERENCES personnel (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Ordre de mission lié à une demande
ALTER TABLE ordre_mission
  ADD COLUMN demande_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN valide_par char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN date_validation datetime DEFAULT NULL,
  ADD COLUMN motif_rejet text COLLATE utf8mb4_unicode_ci,
  ADD UNIQUE KEY uq_om_demande (demande_id),
  ADD KEY idx_om_valideur (valide_par),
  ADD CONSTRAINT fk_om_demande FOREIGN KEY (demande_id) REFERENCES demande (id) ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT fk_om_valideur FOREIGN KEY (valide_par) REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE;

-- 7. Notation annuelle : circuit responsable -> RH
ALTER TABLE note_annuelle
  ADD COLUMN statut enum('brouillon','soumise','validee','publiee') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'brouillon',
  ADD COLUMN valide_par char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN date_validation datetime DEFAULT NULL,
  ADD COLUMN observations text COLLATE utf8mb4_unicode_ci,
  ADD COLUMN observations_agent text COLLATE utf8mb4_unicode_ci,
  ADD KEY idx_note_valideur (valide_par),
  ADD KEY idx_note_statut (statut),
  ADD CONSTRAINT fk_note_valideur FOREIGN KEY (valide_par) REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE;

CREATE TABLE note_critere (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  note_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  code varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  valeur decimal(5,2) NOT NULL,
  coefficient decimal(5,2) NOT NULL DEFAULT '1.00',
  PRIMARY KEY (id),
  UNIQUE KEY uq_note_critere (note_id, code),
  CONSTRAINT fk_nc_note FOREIGN KEY (note_id) REFERENCES note_annuelle (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Notifications
CREATE TABLE notification (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  utilisateur_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  type varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  titre varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  message text COLLATE utf8mb4_unicode_ci,
  lien varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  lu tinyint(1) NOT NULL DEFAULT '0',
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notif_utilisateur (utilisateur_id, lu, cree_le),
  CONSTRAINT fk_notif_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE appareil_push (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  utilisateur_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  token varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  plateforme enum('ios','android','web') COLLATE utf8mb4_unicode_ci NOT NULL,
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_push_token (token),
  KEY idx_push_utilisateur (utilisateur_id),
  CONSTRAINT fk_push_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Journal d'audit (écrit par l'application, jamais modifié ni supprimé)
CREATE TABLE journal_audit (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  utilisateur_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  action varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  entite varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  entite_id varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  avant json DEFAULT NULL,
  apres json DEFAULT NULL,
  ip varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  user_agent varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_date (cree_le),
  KEY idx_audit_utilisateur (utilisateur_id, cree_le),
  KEY idx_audit_entite (entite, entite_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Jours fériés et paramètres versionnés
CREATE TABLE jour_ferie (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  date_ferie date NOT NULL,
  libelle_fr varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle_ar varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  type_ferie enum('civil','religieux') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'civil',
  a_confirmer tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Fêtes religieuses : dépend de l''observation lunaire',
  PRIMARY KEY (id),
  UNIQUE KEY uq_ferie_date (date_ferie)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE parametre (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  cle varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  valeur varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  date_effet date NOT NULL,
  reference_texte varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Texte réglementaire ; NULL = à valider',
  statut_validation enum('non_valide','valide') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'non_valide',
  valide_par char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  valide_le datetime DEFAULT NULL,
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_parametre_cle_effet (cle, date_effet),
  CONSTRAINT fk_parametre_valideur FOREIGN KEY (valide_par) REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Historique de carrière
CREATE TABLE historique_carriere (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  personnel_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  type_evenement enum('affectation','echelon','grade','position') COLLATE utf8mb4_unicode_ci NOT NULL,
  ancienne_valeur varchar(250) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  nouvelle_valeur varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  date_effet date NOT NULL,
  decision_ref varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  cree_par char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_hc_personnel (personnel_id, date_effet),
  CONSTRAINT fk_hc_personnel FOREIGN KEY (personnel_id) REFERENCES personnel (id) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_hc_createur FOREIGN KEY (cree_par) REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. Sessions : refresh tokens rotatifs et révocables
CREATE TABLE refresh_token (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  utilisateur_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  token_hash char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  famille char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  appareil varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ip varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  expire_le datetime NOT NULL,
  revoque tinyint(1) NOT NULL DEFAULT '0',
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_refresh_hash (token_hash),
  KEY idx_refresh_utilisateur (utilisateur_id),
  KEY idx_refresh_famille (famille),
  CONSTRAINT fk_refresh_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Comptes : changement de mot de passe obligatoire, verrouillage temporaire, 2FA optionnelle
ALTER TABLE utilisateur
  ADD COLUMN doit_changer_mdp tinyint(1) NOT NULL DEFAULT '0',
  ADD COLUMN echecs_connexion int NOT NULL DEFAULT '0',
  ADD COLUMN verrouille_jusqua datetime DEFAULT NULL,
  ADD COLUMN totp_secret varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN totp_actif tinyint(1) NOT NULL DEFAULT '0';
