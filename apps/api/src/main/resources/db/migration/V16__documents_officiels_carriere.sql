-- Documents officiels numérotés (attestations) : un numéro par document, vérifiable publiquement, jamais réutilisé.
CREATE TABLE document_officiel (
  id           CHAR(36)     NOT NULL DEFAULT (UUID()),
  type         ENUM('attestation_travail','attestation_salaire') NOT NULL,
  numero       VARCHAR(30)  NOT NULL,
  demande_id   CHAR(36)     NOT NULL,
  personnel_id CHAR(36)     NOT NULL,
  emis_le      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  emis_par     CHAR(36)     NULL,
  sha256       CHAR(64)     NOT NULL,
  contenu      MEDIUMBLOB   NOT NULL COMMENT 'Fichier exactement tel qu''il a été émis (archivage à valeur probante)',
  annule       TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uq_docoff_numero (numero),
  UNIQUE KEY uq_docoff_demande (demande_id),
  KEY idx_docoff_personnel (personnel_id),
  CONSTRAINT fk_docoff_demande FOREIGN KEY (demande_id) REFERENCES demande (id),
  CONSTRAINT fk_docoff_personnel FOREIGN KEY (personnel_id) REFERENCES personnel (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO parametre (id, cle, valeur, date_effet, reference_texte, statut_validation)
SELECT UUID(), 'attestation.signataire_fr', 'Le Chef du Service des Ressources Humaines', '2026-01-01', 'À valider par la RH', 'non_valide' WHERE NOT EXISTS (SELECT 1 FROM parametre WHERE cle = 'attestation.signataire_fr');
INSERT INTO parametre (id, cle, valeur, date_effet, reference_texte, statut_validation)
SELECT UUID(), 'attestation.signataire_ar', 'رئيس مصلحة الموارد البشرية', '2026-01-01', 'À valider par la RH', 'non_valide' WHERE NOT EXISTS (SELECT 1 FROM parametre WHERE cle = 'attestation.signataire_ar');

-- Avancement : événements de carrière tracés avec la décision qui les fonde.
ALTER TABLE historique_carriere ADD KEY idx_carriere_personnel_date (personnel_id, date_effet);
