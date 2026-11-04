-- Circuit de validation : nombre de niveaux de responsables avant la RH.
-- 1 (défaut) = le responsable direct valide, puis la RH ; 0 = tous les responsables de la hiérarchie, dans l'ordre.
INSERT IGNORE INTO parametre (cle, valeur, date_effet, reference_texte, statut_validation) VALUES
('validation.chaine.niveaux_max','1','2026-01-01','Décision de l''Entraide Nationale : responsable direct puis RH (0 = toute la hiérarchie) ; à confirmer par écrit','non_valide');
