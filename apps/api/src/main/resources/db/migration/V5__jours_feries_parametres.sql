-- V5 : jours fériés, paramètres initiaux et réglages par type de demande.
-- Les fêtes religieuses dépendent de l'observation lunaire : a_confirmer = 1.
-- Tous les paramètres sont « non_valide » tant que la RH / le service juridique n'a pas confirmé le texte.

UPDATE type_demande SET validation_responsable = 0 WHERE code = 'ATTESTATION';

-- Le hash du compte admin du dump est factice : changement de mot de passe obligatoire à la première connexion.
UPDATE utilisateur SET doit_changer_mdp = 1 WHERE login = 'admin';

INSERT INTO jour_ferie (date_ferie, libelle_fr, libelle_ar, type_ferie, a_confirmer) VALUES
('2026-01-01','Nouvel an','رأس السنة الميلادية','civil',0),
('2026-01-11','Manifeste de l''indépendance','ذكرى تقديم وثيقة الاستقلال','civil',0),
('2026-01-14','Nouvel an amazigh','السنة الأمازيغية','civil',0),
('2026-03-20','Aïd Al Fitr (1er jour)','عيد الفطر','religieux',1),
('2026-03-21','Aïd Al Fitr (2e jour)','عيد الفطر','religieux',1),
('2026-05-01','Fête du travail','عيد الشغل','civil',0),
('2026-05-27','Aïd Al Adha (1er jour)','عيد الأضحى','religieux',1),
('2026-05-28','Aïd Al Adha (2e jour)','عيد الأضحى','religieux',1),
('2026-06-17','1er Moharram','فاتح محرم','religieux',1),
('2026-07-30','Fête du trône','عيد العرش','civil',0),
('2026-08-14','Allégeance Oued Eddahab','ذكرى استرجاع وادي الذهب','civil',0),
('2026-08-20','Révolution du roi et du peuple','ثورة الملك والشعب','civil',0),
('2026-08-21','Fête de la jeunesse','عيد الشباب','civil',0),
('2026-08-26','Aïd Al Mawlid (1er jour)','عيد المولد النبوي','religieux',1),
('2026-08-27','Aïd Al Mawlid (2e jour)','عيد المولد النبوي','religieux',1),
('2026-11-06','Marche verte','ذكرى المسيرة الخضراء','civil',0),
('2026-11-18','Fête de l''indépendance','عيد الاستقلال','civil',0),
('2027-01-01','Nouvel an','رأس السنة الميلادية','civil',0),
('2027-01-11','Manifeste de l''indépendance','ذكرى تقديم وثيقة الاستقلال','civil',0),
('2027-01-14','Nouvel an amazigh','السنة الأمازيغية','civil',0),
('2027-05-01','Fête du travail','عيد الشغل','civil',0),
('2027-07-30','Fête du trône','عيد العرش','civil',0),
('2027-08-14','Allégeance Oued Eddahab','ذكرى استرجاع وادي الذهب','civil',0),
('2027-08-20','Révolution du roi et du peuple','ثورة الملك والشعب','civil',0),
('2027-08-21','Fête de la jeunesse','عيد الشباب','civil',0),
('2027-11-06','Marche verte','ذكرى المسيرة الخضراء','civil',0),
('2027-11-18','Fête de l''indépendance','عيد الاستقلال','civil',0);

INSERT INTO parametre (cle, valeur, date_effet, reference_texte, statut_validation) VALUES
('conge.annuel.droit_jours','22','2026-01-01',NULL,'non_valide'),
('avance.plafond_mad','5000','2026-01-01',NULL,'non_valide'),
('avancement.echelon.duree_mois','24','2026-01-01',NULL,'non_valide'),
('demande.delai_reponse_jours','5','2026-01-01',NULL,'non_valide'),
('notation.bareme_max','20','2026-01-01',NULL,'non_valide'),
('piece_jointe.taille_max_mo','5','2026-01-01','Cahier des charges TAWASSOL §3 (paramétrable)','non_valide'),
('securite.mdp.longueur_min','12','2026-01-01',NULL,'non_valide'),
('securite.connexion.max_echecs','5','2026-01-01',NULL,'non_valide'),
('securite.connexion.verrouillage_minutes','15','2026-01-01',NULL,'non_valide');
