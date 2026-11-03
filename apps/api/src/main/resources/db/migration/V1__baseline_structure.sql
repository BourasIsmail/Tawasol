-- V1 : structure de base, reprise fidèle du dump tawassol (21 tables).
-- Ne pas modifier : toute évolution passe par une nouvelle migration.
SET FOREIGN_KEY_CHECKS=0;

CREATE TABLE `annonce` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `cree_par` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `titre` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `est_active` tinyint(1) NOT NULL DEFAULT '1',
  `publie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expire_le` datetime DEFAULT NULL,
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_annonce_active` (`est_active`),
  KEY `idx_annonce_expire` (`expire_le`),
  KEY `idx_annonce_createur` (`cree_par`),
  CONSTRAINT `fk_annonce_createur` FOREIGN KEY (`cree_par`) REFERENCES `utilisateur` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `coordination_region` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `code_region` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_region_ar` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_region_fr` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_coord_ar` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_coord_fr` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `telephone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `adresse` text COLLATE utf8mb4_unicode_ci,
  `est_active` tinyint(1) NOT NULL DEFAULT '1',
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code_region` (`code_region`),
  KEY `idx_coord_code` (`code_region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `credit` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `personnel_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `banque` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `num_dossier` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type_credit` enum('bancaire','interne_AOS') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'bancaire',
  `mensualite` decimal(10,2) NOT NULL,
  `montant_global` decimal(12,2) NOT NULL DEFAULT '0.00',
  `montant_restant` decimal(12,2) NOT NULL DEFAULT '0.00',
  `nb_mois_restants` int NOT NULL DEFAULT '0',
  `date_debut` date DEFAULT NULL,
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_credit_personnel` (`personnel_id`),
  KEY `idx_credit_type` (`type_credit`),
  CONSTRAINT `fk_credit_personnel` FOREIGN KEY (`personnel_id`) REFERENCES `personnel` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `delegation_province` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `coordination_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code_province` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_province_ar` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_province_fr` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_deleg_ar` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_deleg_fr` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `telephone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `telephone_inwi` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `telephone_flotte` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `adresse` text COLLATE utf8mb4_unicode_ci,
  `est_active` tinyint(1) NOT NULL DEFAULT '1',
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code_province` (`code_province`),
  KEY `idx_deleg_coord` (`coordination_id`),
  CONSTRAINT `fk_deleg_coord` FOREIGN KEY (`coordination_id`) REFERENCES `coordination_region` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `demande` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `personnel_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type_demande_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `traite_par` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `objet` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `statut` enum('brouillon','soumise','approuvee','rejetee') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'brouillon',
  `date_traitement` datetime DEFAULT NULL,
  `commentaire_rh` text COLLATE utf8mb4_unicode_ci,
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_demande_personnel` (`personnel_id`),
  KEY `idx_demande_type` (`type_demande_id`),
  KEY `idx_demande_statut` (`statut`),
  KEY `idx_demande_traiteur` (`traite_par`),
  CONSTRAINT `fk_demande_personnel` FOREIGN KEY (`personnel_id`) REFERENCES `personnel` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_demande_traiteur` FOREIGN KEY (`traite_par`) REFERENCES `utilisateur` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_demande_type` FOREIGN KEY (`type_demande_id`) REFERENCES `type_demande` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `document` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `type_doc_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cree_par` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `titre` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  `url_fichier` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contenu` text COLLATE utf8mb4_unicode_ci,
  `est_publie` tinyint(1) NOT NULL DEFAULT '0',
  `publie_le` datetime DEFAULT NULL,
  `expire_le` datetime DEFAULT NULL,
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_doc_type` (`type_doc_id`),
  KEY `idx_doc_publie` (`est_publie`),
  KEY `idx_doc_expire` (`expire_le`),
  KEY `idx_doc_createur` (`cree_par`),
  CONSTRAINT `fk_doc_createur` FOREIGN KEY (`cree_par`) REFERENCES `utilisateur` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_doc_type` FOREIGN KEY (`type_doc_id`) REFERENCES `type_document` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `examen_grade` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `grade_cible_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `annee` year NOT NULL,
  `date_examen` date NOT NULL,
  `date_depot` date DEFAULT NULL,
  `nb_postes` int NOT NULL DEFAULT '0',
  `lieu` varchar(250) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `details` text COLLATE utf8mb4_unicode_ci,
  `resultats_ecrit` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resultats_final` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_examen_annee` (`annee`),
  KEY `idx_examen_grade_cible` (`grade_cible_id`),
  CONSTRAINT `fk_examen_grade` FOREIGN KEY (`grade_cible_id`) REFERENCES `grade` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `grade` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `code` int NOT NULL,
  `libelle_fr` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `libelle_ar` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `echelle` int DEFAULT NULL,
  `nb_echelons` int NOT NULL DEFAULT '10' COMMENT 'Nombre total d''échelons dans cette échelle',
  `taux` decimal(10,4) DEFAULT NULL,
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_grade_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `moyen_transport` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `libelle_fr` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `libelle_ar` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `est_actif` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_transport_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Moyens de transport pour les ordres de mission';

CREATE TABLE `note_annuelle` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `personnel_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `saisie_par` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `annee` year NOT NULL,
  `note` decimal(5,2) DEFAULT NULL,
  `appreciation` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_note_agent_annee` (`personnel_id`,`annee`),
  KEY `idx_note_personnel` (`personnel_id`),
  KEY `idx_note_saisie` (`saisie_par`),
  CONSTRAINT `fk_note_personnel` FOREIGN KEY (`personnel_id`) REFERENCES `personnel` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_note_saisie` FOREIGN KEY (`saisie_par`) REFERENCES `utilisateur` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ordre_mission` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `personnel_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FK → personnel',
  `programme_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'FK → programme_mission',
  `depart_delegation_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'FK → delegation_province (départ)',
  `arrival_delegation_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'FK → delegation_province (destination)',
  `moyen_transport_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'FK → moyen_transport',
  `objet` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `numero_ordm` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ex: KB / 7 / 2025',
  `numero_etat` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ex: EN-PG / KB - 20 / 2025',
  `date_ordm` date DEFAULT NULL COMMENT 'Date de l''ordre de mission',
  `date_depart` datetime NOT NULL,
  `date_retour` datetime NOT NULL,
  `annee` year NOT NULL,
  `cycle` tinyint DEFAULT NULL COMMENT 'Trimestre : 1, 2, 3 ou 4',
  `nombre_repas_taux_1` int NOT NULL DEFAULT '0',
  `indemnite_taux_1` decimal(10,2) NOT NULL DEFAULT '0.00',
  `nombre_repas_taux_2` int NOT NULL DEFAULT '0',
  `indemnite_taux_2` decimal(10,2) NOT NULL DEFAULT '0.00',
  `montant_total` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'Montant total de l''indemnité de déplacement',
  `statut` enum('en_attente','approuve','rejete','annule') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'en_attente',
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_om_personnel` (`personnel_id`),
  KEY `idx_om_programme` (`programme_id`),
  KEY `idx_om_annee` (`annee`),
  KEY `idx_om_statut` (`statut`),
  KEY `idx_om_depart` (`depart_delegation_id`),
  KEY `idx_om_arrival` (`arrival_delegation_id`),
  KEY `fk_om_transport` (`moyen_transport_id`),
  CONSTRAINT `fk_om_arrival` FOREIGN KEY (`arrival_delegation_id`) REFERENCES `delegation_province` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_om_depart` FOREIGN KEY (`depart_delegation_id`) REFERENCES `delegation_province` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_om_personnel` FOREIGN KEY (`personnel_id`) REFERENCES `personnel` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_om_programme` FOREIGN KEY (`programme_id`) REFERENCES `programme_mission` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_om_transport` FOREIGN KEY (`moyen_transport_id`) REFERENCES `moyen_transport` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Ordres de mission avec indemnités de déplacement';

CREATE TABLE `personnel` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `grade_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `structure_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `matricule` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_ar` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prenom_ar` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_fr` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prenom_fr` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `date_recrutement` date NOT NULL,
  `email` varchar(250) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `photo` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `echelon` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `echelle` int DEFAULT NULL,
  `date_echelon` date DEFAULT NULL,
  `est_actif` tinyint(1) NOT NULL DEFAULT '1',
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_personnel_matricule` (`matricule`),
  KEY `idx_personnel_grade` (`grade_id`),
  KEY `idx_personnel_structure` (`structure_id`),
  KEY `idx_personnel_actif` (`est_actif`),
  CONSTRAINT `fk_personnel_grade` FOREIGN KEY (`grade_id`) REFERENCES `grade` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_personnel_structure` FOREIGN KEY (`structure_id`) REFERENCES `structure` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `piece_jointe` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `demande_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_fichier` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `url_fichier` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type_mime` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `taille_kb` int DEFAULT NULL,
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pj_demande` (`demande_id`),
  CONSTRAINT `fk_pj_demande` FOREIGN KEY (`demande_id`) REFERENCES `demande` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `prime` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `personnel_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type_prime` enum('gratification','indemnite','autre') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'gratification',
  `montant_brut` decimal(10,2) NOT NULL,
  `ir` decimal(10,2) NOT NULL DEFAULT '0.00',
  `montant_net` decimal(10,2) NOT NULL,
  `date_prime` date NOT NULL,
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_prime_personnel` (`personnel_id`),
  KEY `idx_prime_date` (`date_prime`),
  KEY `idx_prime_type` (`type_prime`),
  CONSTRAINT `fk_prime_personnel` FOREIGN KEY (`personnel_id`) REFERENCES `personnel` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `programme_mission` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_fr` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_ar` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  `montant_max` decimal(10,2) DEFAULT NULL COMMENT 'Montant max indemnité par déplacement',
  `est_actif` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_prog_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Catégories de personnel pour les ordres de mission';

CREATE TABLE `reclamation` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `personnel_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `traite_par` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `objet` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `statut` enum('nouvelle','en_cours','resolue','rejetee') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'nouvelle',
  `reponse` text COLLATE utf8mb4_unicode_ci,
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recl_personnel` (`personnel_id`),
  KEY `idx_recl_statut` (`statut`),
  KEY `idx_recl_traiteur` (`traite_par`),
  CONSTRAINT `fk_recl_personnel` FOREIGN KEY (`personnel_id`) REFERENCES `personnel` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_recl_traiteur` FOREIGN KEY (`traite_par`) REFERENCES `utilisateur` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `salaire` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `personnel_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `grade_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `annee` smallint NOT NULL,
  `mois` tinyint NOT NULL COMMENT '1=Janvier ... 12=Décembre',
  `echelon` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `salaire_net` decimal(10,2) NOT NULL,
  `alloc_familiale` decimal(10,2) NOT NULL DEFAULT '0.00',
  `retenue_mutuelle` decimal(10,2) NOT NULL DEFAULT '0.00',
  `rappel` decimal(10,2) NOT NULL DEFAULT '0.00',
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_salaire_agent_mois` (`personnel_id`,`annee`,`mois`),
  KEY `idx_salaire_personnel` (`personnel_id`),
  KEY `idx_salaire_grade` (`grade_id`),
  KEY `idx_salaire_periode` (`annee`,`mois`),
  CONSTRAINT `fk_salaire_grade` FOREIGN KEY (`grade_id`) REFERENCES `grade` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_salaire_personnel` FOREIGN KEY (`personnel_id`) REFERENCES `personnel` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `structure` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `delegation_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('association','etablissement','centre','complexe') COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_ar` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nom_fr` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  `adresse` text COLLATE utf8mb4_unicode_ci,
  `est_active` tinyint(1) NOT NULL DEFAULT '1',
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_struct_delegation` (`delegation_id`),
  KEY `idx_struct_parent` (`parent_id`),
  CONSTRAINT `fk_struct_delegation` FOREIGN KEY (`delegation_id`) REFERENCES `delegation_province` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_struct_parent` FOREIGN KEY (`parent_id`) REFERENCES `structure` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `type_demande` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `libelle` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `est_actif` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_type_dem_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `type_document` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `libelle` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dossier` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `libelle` (`libelle`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `utilisateur` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  `personnel_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `login` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `mot_de_passe` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('agent','rh','responsable','admin') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'agent',
  `est_actif` tinyint(1) NOT NULL DEFAULT '1',
  `derniere_cnx` datetime DEFAULT NULL,
  `cree_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modifie_le` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_utilisateur_login` (`login`),
  UNIQUE KEY `uq_utilisateur_personnel` (`personnel_id`),
  KEY `idx_utilisateur_role` (`role`),
  KEY `idx_utilisateur_actif` (`est_actif`),
  CONSTRAINT `fk_utilisateur_personnel` FOREIGN KEY (`personnel_id`) REFERENCES `personnel` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS=1;
