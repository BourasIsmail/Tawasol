-- Soldes de congé : provenance des chiffres, catégorie d'agent, report paramétrable (désactivé par défaut).

-- saisi         : valeur entrée ou corrigée par la RH (fait foi)
-- officiel      : solde d'ouverture fourni par la RH à une date de bascule (porté par la colonne « reporte »)
-- reconstitue   : jours pris recalculés à partir des congés annuels approuvés ; à vérifier par la RH
ALTER TABLE solde_conge
  ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'saisi' AFTER reporte,
  ADD COLUMN a_verifier TINYINT(1) NOT NULL DEFAULT 0 AFTER source;

-- Catégorie d'agent (titulaire, contractuel, occasionnel…) : sert à choisir le droit annuel. Valeurs libres, à définir par la RH.
ALTER TABLE personnel ADD COLUMN categorie_agent VARCHAR(30) NULL AFTER stagiaire;

INSERT IGNORE INTO parametre (cle, valeur, date_effet, reference_texte, statut_validation) VALUES
('conge.report.annees_max','0','2026-01-01','Report désactivé par défaut : règle à fournir par la RH et le juridique','non_valide'),
('conge.report.plafond_jours','0','2026-01-01','Plafond du report : règle à fournir par la RH et le juridique','non_valide');

-- Le droit annuel par défaut reste une valeur d'exemple à faire valider ; un droit par catégorie s'ajoute avec une clé
-- conge.annuel.droit_jours.<CATEGORIE> (ex. conge.annuel.droit_jours.CONTRACTUEL).
UPDATE parametre SET reference_texte = 'Valeur d''exemple (~22 jours ouvrables) : à valider par la RH et le juridique, y compris pour les non titulaires'
 WHERE cle = 'conge.annuel.droit_jours' AND reference_texte IS NULL;
