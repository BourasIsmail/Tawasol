-- V6 : espace agent (phase 2).
-- Réclamations typées : le signalement d'erreur sur la fiche crée une réclamation « correction de données ».
ALTER TABLE reclamation
  ADD COLUMN type_reclamation enum('generale','correction_donnees') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'generale',
  ADD COLUMN champ_concerne varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN valeur_proposee varchar(250) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN date_traitement datetime DEFAULT NULL;

-- Accusé de lecture des documents (notes de service)
CREATE TABLE document_lecture (
  document_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  utilisateur_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  lu_le datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (document_id, utilisateur_id),
  CONSTRAINT fk_dl_document FOREIGN KEY (document_id) REFERENCES document (id) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_dl_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Pièces jointes : empreinte du fichier stocké (la clé de stockage est dans url_fichier)
ALTER TABLE piece_jointe ADD COLUMN sha256 char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL;

-- Paramètres supplémentaires. Valeurs d'exemple : tous « non_valide ».
INSERT INTO parametre (cle, valeur, date_effet, reference_texte, statut_validation) VALUES
('avance.endettement_max_pct','33','2026-01-01',NULL,'non_valide'),
('conge.jours_non_ouvres','SATURDAY,SUNDAY','2026-01-01',NULL,'non_valide'),
('demande.pj.max_par_demande','5','2026-01-01',NULL,'non_valide'),
('lien_signe.duree_secondes','300','2026-01-01',NULL,'non_valide');
