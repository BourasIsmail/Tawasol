-- Reprise de Suivi RH (M4) : types de congé absents du portail et archive en lecture seule des données sans équivalent.
INSERT INTO type_demande (id, code, libelle, description, validation_responsable)
SELECT UUID(), t.code, t.libelle, 'Type repris de Suivi RH', 1 FROM (
  SELECT 'CONGE_PERMISSION_ABSENCE' AS code, 'Permission d''absence' AS libelle UNION ALL
  SELECT 'CONGE_MATERNITE', 'Congé de maternité, paternité, adoption ou allaitement' UNION ALL
  SELECT 'CONGE_SANS_SOLDE', 'Congé sans solde' UNION ALL
  SELECT 'CONGE_ACCIDENT_TRAVAIL', 'Congé pour maladie ou accident survenu dans l''exercice des fonctions') t
WHERE NOT EXISTS (SELECT 1 FROM type_demande x WHERE x.code = t.code);

CREATE TABLE legacy_archive (
  id            CHAR(36)     NOT NULL DEFAULT (UUID()),
  table_legacy  VARCHAR(60)  NOT NULL,
  id_legacy     VARCHAR(40)  NOT NULL,
  donnees       JSON         NOT NULL,
  cree_le       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_legacy_archive (table_legacy, id_legacy)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Données de Suivi RH sans équivalent dans TAWASSOL, conservées en lecture seule (loi 69-99).';

ALTER TABLE demande_historique ADD COLUMN migre TINYINT(1) NOT NULL DEFAULT 0;
