-- V3 : champs agent manquants, ciblage des publications, import et publication des données financières.
-- Hypothèses documentées dans docs/HYPOTHESES.md : ces champs sont à valider avec la RH.

ALTER TABLE personnel
  ADD COLUMN date_naissance date DEFAULT NULL,
  ADD COLUMN cin varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN sexe enum('M','F') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN situation_familiale enum('celibataire','marie','divorce','veuf') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN telephone varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN position_administrative enum('activite','detachement','disponibilite','mise_a_disposition','autre') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'activite',
  ADD UNIQUE KEY uq_personnel_cin (cin);

ALTER TABLE annonce
  ADD COLUMN cible_type enum('tous','coordination','delegation','structure') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'tous',
  ADD COLUMN cible_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN modifie_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE document
  ADD COLUMN cible_type enum('tous','coordination','delegation','structure') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'tous',
  ADD COLUMN cible_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN accuse_lecture tinyint(1) NOT NULL DEFAULT '0';

-- Modèles d'import : le format du fichier est un réglage en base, pas du code
CREATE TABLE import_modele (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  type_import enum('personnel','salaire','prime','credit','note','examen') COLLATE utf8mb4_unicode_ci NOT NULL,
  nom varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  encodage varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UTF-8',
  separateur char(1) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ';',
  separateur_decimal char(1) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ',',
  format_date varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'dd/MM/yyyy',
  mapping json NOT NULL,
  est_actif tinyint(1) NOT NULL DEFAULT '1',
  cree_par char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modifie_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_import_modele_nom (type_import, nom),
  CONSTRAINT fk_im_createur FOREIGN KEY (cree_par) REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Lots importés : brouillon -> publié (irréversible)
CREATE TABLE import_lot (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  numero varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  type_import enum('personnel','salaire','prime','credit','note','examen') COLLATE utf8mb4_unicode_ci NOT NULL,
  modele_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  periode_annee smallint DEFAULT NULL,
  periode_mois tinyint DEFAULT NULL,
  fichier_nom varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  fichier_sha256 char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  statut enum('brouillon','publie','annule') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'brouillon',
  nb_lignes int NOT NULL DEFAULT '0',
  nb_erreurs int NOT NULL DEFAULT '0',
  rapport_erreurs json DEFAULT NULL,
  cree_par char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  publie_par char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  publie_le datetime DEFAULT NULL,
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_lot_numero (numero),
  KEY idx_lot_type_periode (type_import, periode_annee, periode_mois),
  KEY idx_lot_modele (modele_id),
  CONSTRAINT fk_lot_modele FOREIGN KEY (modele_id) REFERENCES import_modele (id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_lot_createur FOREIGN KEY (cree_par) REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_lot_publieur FOREIGN KEY (publie_par) REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE salaire
  ADD COLUMN lot_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN statut enum('brouillon','publie') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'brouillon',
  ADD COLUMN publie_le datetime DEFAULT NULL,
  ADD KEY idx_salaire_lot (lot_id),
  ADD CONSTRAINT fk_salaire_lot FOREIGN KEY (lot_id) REFERENCES import_lot (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE prime
  ADD COLUMN lot_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN statut enum('brouillon','publie') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'brouillon',
  ADD COLUMN publie_le datetime DEFAULT NULL,
  ADD KEY idx_prime_lot (lot_id),
  ADD CONSTRAINT fk_prime_lot FOREIGN KEY (lot_id) REFERENCES import_lot (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE credit
  ADD COLUMN lot_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN statut enum('brouillon','publie') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'brouillon',
  ADD COLUMN publie_le datetime DEFAULT NULL,
  ADD KEY idx_credit_lot (lot_id),
  ADD CONSTRAINT fk_credit_lot FOREIGN KEY (lot_id) REFERENCES import_lot (id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- Rubriques détaillées d'un bulletin (gains et retenues), pour un bulletin détaillé quand les colonnes existent
CREATE TABLE salaire_ligne (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  salaire_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  ordre smallint NOT NULL DEFAULT '0',
  code varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle_fr varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle_ar varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  sens enum('gain','retenue') COLLATE utf8mb4_unicode_ci NOT NULL,
  montant decimal(12,2) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_sl_salaire (salaire_id, ordre),
  CONSTRAINT fk_sl_salaire FOREIGN KEY (salaire_id) REFERENCES salaire (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
