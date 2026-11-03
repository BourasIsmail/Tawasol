-- V8 : espace responsable (phase 3) et mot de passe oublié.

-- Responsable compétent pour une demande, déterminé à la soumission (remontée dans l'arbre, suppléances comprises à la lecture).
-- NULL = pas de responsable : la demande va directement à la RH.
ALTER TABLE demande
  ADD COLUMN responsable_personnel_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD KEY idx_demande_responsable (responsable_personnel_id, statut),
  ADD CONSTRAINT fk_demande_responsable FOREIGN KEY (responsable_personnel_id) REFERENCES personnel (id) ON DELETE SET NULL ON UPDATE CASCADE;

-- Campagne de notation : ouverte par la RH (phase 4), consultée par les responsables
CREATE TABLE campagne_notation (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  annee smallint NOT NULL,
  date_ouverture date NOT NULL,
  date_cloture date NOT NULL,
  bareme_max decimal(5,2) NOT NULL DEFAULT '20.00' COMMENT 'Barème : à valider',
  est_active tinyint(1) NOT NULL DEFAULT '1',
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_campagne_annee (annee),
  CONSTRAINT ck_campagne_dates CHECK (date_cloture >= date_ouverture)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Jetons de réinitialisation de mot de passe (usage unique, courte durée, stockés hachés)
CREATE TABLE reset_mdp_token (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  utilisateur_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  token_hash char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  expire_le datetime NOT NULL,
  utilise_le datetime DEFAULT NULL,
  cree_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reset_hash (token_hash),
  KEY idx_reset_utilisateur (utilisateur_id),
  CONSTRAINT fk_reset_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO parametre (cle, valeur, date_effet, reference_texte, statut_validation) VALUES
('securite.reset_mdp.duree_minutes','30','2026-01-01',NULL,'non_valide');
