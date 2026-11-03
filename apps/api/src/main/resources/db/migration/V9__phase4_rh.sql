-- V9 : espace RH (phase 4A). Une ligne financière remplacée par un import ultérieur n'est jamais supprimée : statut « remplace ».
ALTER TABLE salaire MODIFY COLUMN statut enum('brouillon','publie','remplace') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'brouillon';
ALTER TABLE prime MODIFY COLUMN statut enum('brouillon','publie','remplace') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'brouillon';
ALTER TABLE credit MODIFY COLUMN statut enum('brouillon','publie','remplace') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'brouillon';
ALTER TABLE credit ADD KEY idx_credit_dossier (personnel_id, num_dossier);

-- Notation : commentaire de la RH lors d'un renvoi pour correction, date de publication à l'agent
ALTER TABLE note_annuelle
  ADD COLUMN commentaire_rh text COLLATE utf8mb4_unicode_ci,
  ADD COLUMN publie_le datetime DEFAULT NULL;

INSERT INTO parametre (cle, valeur, date_effet, reference_texte, statut_validation) VALUES
('import.taille_max_mo','10','2026-01-01',NULL,'non_valide');
