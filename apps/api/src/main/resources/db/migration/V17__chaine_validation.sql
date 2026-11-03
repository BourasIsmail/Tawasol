-- Chaîne de validation hiérarchique : tous les responsables de la hiérarchie valident dans l'ordre, du plus proche au plus haut.
-- La chaîne est figée à la soumission (un changement d'organigramme ultérieur ne la modifie pas).
CREATE TABLE etape_validation (
  id                       CHAR(36)    NOT NULL DEFAULT (UUID()),
  objet_type               ENUM('demande','mission') NOT NULL,
  objet_id                 CHAR(36)    NOT NULL,
  ordre                    INT         NOT NULL,
  niveau                   ENUM('structure','delegation','coordination') NOT NULL,
  responsable_personnel_id CHAR(36)    NOT NULL,
  statut                   ENUM('en_attente','validee','rejetee','ignoree') NOT NULL DEFAULT 'en_attente',
  decide_par               CHAR(36)    NULL COMMENT 'Utilisateur qui a décidé (le responsable ou son suppléant)',
  par_suppleance           TINYINT(1)  NOT NULL DEFAULT 0,
  date_decision            DATETIME    NULL,
  commentaire              TEXT        NULL,
  cree_le                  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_etape_ordre (objet_type, objet_id, ordre),
  KEY idx_etape_responsable (responsable_personnel_id, statut),
  CONSTRAINT fk_etape_responsable FOREIGN KEY (responsable_personnel_id) REFERENCES personnel (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
