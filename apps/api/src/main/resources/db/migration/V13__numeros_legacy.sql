-- Numéros hérités de Suivi RH : conservés tels quels (références déjà imprimées et signées), jamais reformatés.
-- Les colonnes numero_ordm / numero_etat ne reçoivent que les numéros émis par TAWASSOL : ils sont uniques.
ALTER TABLE ordre_mission
  ADD COLUMN numero_ordm_legacy VARCHAR(60) NULL AFTER numero_etat,
  ADD COLUMN numero_etat_legacy VARCHAR(60) NULL AFTER numero_ordm_legacy,
  ADD COLUMN anomalie_numero ENUM('aucune','numero_absent','etat_en_double') NOT NULL DEFAULT 'aucune' AFTER numero_etat_legacy,
  ADD COLUMN date_emission DATETIME NULL AFTER date_ordm,
  DROP INDEX idx_om_numero,
  DROP INDEX idx_om_etat,
  ADD UNIQUE KEY uq_om_numero_ordm (numero_ordm),
  ADD UNIQUE KEY uq_om_numero_etat (numero_etat),
  ADD KEY idx_om_ordm_legacy (numero_ordm_legacy),
  ADD KEY idx_om_etat_legacy (numero_etat_legacy);
