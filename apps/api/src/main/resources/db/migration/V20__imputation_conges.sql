-- Imputation des jours d'un congé annuel sur les années de solde (modèle de Suivi RH) :
-- un congé consomme d'abord le reste de l'année précédente, puis l'année courante ; chaque ligne dit combien de jours sont pris sur quelle année.
-- Le solde d'une année = droit + report saisi - somme des jours imputés à cette année.
CREATE TABLE conge_imputation (
  demande_id CHAR(36) NOT NULL,
  annee SMALLINT NOT NULL,
  jours DECIMAL(5,1) NOT NULL,
  PRIMARY KEY (demande_id, annee),
  KEY idx_conge_imputation_annee (annee),
  CONSTRAINT fk_conge_imputation_demande FOREIGN KEY (demande_id) REFERENCES demande (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Congés annuels déjà approuvés dans TAWASSOL avant cette version : imputés à l'année de leur date de début.
-- Les congés repris de Suivi RH reçoivent leur vraie imputation lors de la reprise (id_legacy non nul : exclus ici).
INSERT INTO conge_imputation (demande_id, annee, jours)
SELECT d.id, YEAR(d.date_debut), d.nb_jours
  FROM demande d JOIN type_demande t ON t.id = d.type_demande_id
 WHERE t.code = 'CONGE_ANNUEL' AND d.statut = 'approuvee' AND d.id_legacy IS NULL AND d.date_debut IS NOT NULL AND d.nb_jours IS NOT NULL AND d.nb_jours > 0;
