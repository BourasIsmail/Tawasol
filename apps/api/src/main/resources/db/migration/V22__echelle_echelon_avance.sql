-- Échelle et échelon : la paie donne « ech » = échelle et « echl » = échelon, chacun avec sa date. L'échelle a donc sa propre date.
ALTER TABLE personnel ADD COLUMN date_echelle DATE NULL AFTER date_echelon;

-- La demande d'avance sur salaire n'existe pas : le type est désactivé (jamais proposé, refusé à la création) et ses paramètres supprimés.
UPDATE type_demande SET est_actif = 0 WHERE code = 'AVANCE_SALAIRE';
DELETE FROM parametre WHERE cle IN ('avance.plafond_mad', 'avance.endettement_max_pct');
