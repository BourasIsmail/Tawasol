-- Les codes de programme de Suivi RH atteignent 33 caractères (ex. personnelle_de_entraide_nationale).
ALTER TABLE programme_mission MODIFY code VARCHAR(60) NOT NULL;
