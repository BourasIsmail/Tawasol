-- Dans Suivi RH, le champ « immatriculation » reçoit parfois du texte libre (jusqu'à 86 caractères, en arabe).
ALTER TABLE ordre_mission MODIFY vehicule_immatriculation VARCHAR(150) NULL, MODIFY vehicule_nom VARCHAR(150) NULL;
