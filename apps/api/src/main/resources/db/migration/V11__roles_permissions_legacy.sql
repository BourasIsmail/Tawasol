-- V11 : permissions fines, responsabilités, colonnes de reprise des données de Suivi RH (phase M1).

-- Rôles et permissions fines (remplacent les ~45 booléens de l'ancien système). Le profil (agent, responsable, rh, admin) reste dans utilisateur.role.
CREATE TABLE role (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  code varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle_fr varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle_ar varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  profil enum('agent','responsable','rh','admin') COLLATE utf8mb4_unicode_ci NOT NULL,
  est_actif tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (id),
  UNIQUE KEY uq_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE permission (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  code varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  domaine varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle_fr varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle_ar varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role_permission (
  role_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  permission_id char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_rp2_role FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_rp2_permission FOREIGN KEY (permission_id) REFERENCES permission (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE utilisateur
  ADD COLUMN role_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN est_compte_legacy tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Compte d''entité migré : désactivé, conservé pour l''audit',
  ADD COLUMN id_legacy bigint DEFAULT NULL,
  ADD KEY idx_utilisateur_role_id (role_id),
  ADD UNIQUE KEY uq_utilisateur_legacy (id_legacy),
  ADD CONSTRAINT fk_utilisateur_role FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE SET NULL ON UPDATE CASCADE;

INSERT INTO permission (code, domaine, libelle_fr, libelle_ar) VALUES
('mission.demander','mission','Demander un ordre de mission pour soi','طلب أمر بمهمة لنفسه'),
('mission.saisir_perimetre','mission','Saisir une mission pour un agent du périmètre','إدخال مهمة لموظف من النطاق'),
('mission.valider_niveau1','mission','Valider une mission (premier niveau)','المصادقة على مهمة (المستوى الأول)'),
('mission.valider','mission','Valider la mission (cellule des déplacements)','المصادقة على المهمة (خلية التنقلات)'),
('mission.corriger','mission','Corriger les montants et les dates','تصحيح المبالغ والتواريخ'),
('mission.valider_paiement','mission','Valider le paiement','مصادقة الأداء'),
('mission.annuler_paiement','mission','Annuler un paiement','إلغاء أداء'),
('mission.bareme','mission','Gérer barèmes, plafonds, seuils et signataires','تدبير الجداول والسقوف والعتبات والموقعين'),
('mission.etats','mission','Générer états et exports','إنشاء الكشوف والتصديرات'),
('mission.analytics','mission','Consulter les analytics des missions','الاطلاع على تحليلات المهام'),
('mission.planifier','mission','Planifier les missions par programme','تخطيط المهام حسب البرنامج'),
('role.gerer','securite','Gérer rôles et permissions','تدبير الأدوار والصلاحيات'),
('migration.executer','securite','Exécuter la migration depuis Suivi RH','تنفيذ النقل من Suivi RH');
INSERT INTO role (code, libelle_fr, libelle_ar, profil) VALUES
('agent_standard','Agent','موظف','agent'),
('responsable_standard','Responsable','رئيس','responsable'),
('gestionnaire_perimetre','Gestionnaire de périmètre (saisie sans validation)','مدبر النطاق (إدخال بدون مصادقة)','responsable'),
('cellule_deplacements','Cellule des déplacements','خلية التنقلات','rh'),
('cellule_paie','Cellule de paie','خلية الأداء','rh'),
('supervision','Supervision (lecture)','الإشراف (قراءة)','rh'),
('rh_generaliste','RH généraliste','الموارد البشرية العامة','rh'),
('admin_technique','Administrateur technique','المدير التقني','admin');
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON
 (r.code='agent_standard' AND p.code IN ('mission.demander','mission.etats'))
 OR (r.code='responsable_standard' AND p.code IN ('mission.demander','mission.saisir_perimetre','mission.valider_niveau1','mission.etats','mission.analytics','mission.planifier'))
 OR (r.code='gestionnaire_perimetre' AND p.code IN ('mission.demander','mission.saisir_perimetre','mission.etats'))
 OR (r.code='cellule_deplacements' AND p.code IN ('mission.demander','mission.saisir_perimetre','mission.valider','mission.corriger','mission.bareme','mission.etats','mission.analytics'))
 OR (r.code='cellule_paie' AND p.code IN ('mission.demander','mission.valider_paiement','mission.annuler_paiement','mission.etats'))
 OR (r.code='supervision' AND p.code IN ('mission.demander','mission.etats','mission.analytics'))
 OR (r.code='rh_generaliste' AND p.code IN ('mission.demander','mission.analytics','mission.planifier'))
 OR (r.code='admin_technique' AND p.code IN ('role.gerer','migration.executer'));

-- Responsabilités (directeur, délégué provincial, coordinateur régional, ...) : alimentent responsable_perimetre après validation par la RH
CREATE TABLE responsabilite (
  id char(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()),
  code varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle_fr varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  libelle_ar varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  id_legacy bigint DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_responsabilite_code (code),
  UNIQUE KEY uq_responsabilite_legacy (id_legacy)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Personnel : colonnes de reprise. date_recrutement devient facultative (ne jamais inventer une date).
-- La CNIE est conservée chiffrée avec une empreinte HMAC pour détecter les doublons sans l'exposer.
ALTER TABLE personnel
  MODIFY COLUMN date_recrutement date DEFAULT NULL,
  ADD COLUMN id_legacy bigint DEFAULT NULL,
  ADD COLUMN cnie_chiffre varbinary(512) DEFAULT NULL,
  ADD COLUMN cnie_empreinte char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN responsabilite_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN situation_service enum('en_activite','sortie_de_service') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'en_activite',
  ADD COLUMN stagiaire tinyint(1) NOT NULL DEFAULT '0',
  ADD COLUMN administration_origine varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN mission_texte text COLLATE utf8mb4_unicode_ci,
  ADD COLUMN taches_assignees text COLLATE utf8mb4_unicode_ci,
  ADD COLUMN unite_administrative_id char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN a_completer tinyint(1) NOT NULL DEFAULT '0',
  ADD COLUMN nom_prenom_fr_legacy varchar(250) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN nom_prenom_ar_legacy varchar(250) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD UNIQUE KEY uq_personnel_legacy (id_legacy),
  ADD UNIQUE KEY uq_personnel_cnie_empreinte (cnie_empreinte),
  ADD KEY idx_personnel_responsabilite (responsabilite_id),
  ADD KEY idx_personnel_unite (unite_administrative_id),
  ADD CONSTRAINT fk_personnel_responsabilite FOREIGN KEY (responsabilite_id) REFERENCES responsabilite (id) ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT fk_personnel_unite FOREIGN KEY (unite_administrative_id) REFERENCES structure (id) ON DELETE SET NULL ON UPDATE CASCADE;

-- Structures : unités administratives du siège, marqueur « à compléter », identifiant de reprise
ALTER TABLE structure
  MODIFY COLUMN type enum('association','etablissement','centre','complexe','unite_administrative') COLLATE utf8mb4_unicode_ci NOT NULL,
  ADD COLUMN a_completer tinyint(1) NOT NULL DEFAULT '0',
  ADD COLUMN id_legacy bigint DEFAULT NULL,
  ADD UNIQUE KEY uq_structure_legacy (id_legacy);

-- Identifiants de reprise sur les autres tables migrées
ALTER TABLE coordination_region ADD COLUMN id_legacy bigint DEFAULT NULL, ADD UNIQUE KEY uq_coordination_legacy (id_legacy);
ALTER TABLE delegation_province ADD COLUMN id_legacy bigint DEFAULT NULL, ADD UNIQUE KEY uq_delegation_legacy (id_legacy);
ALTER TABLE grade ADD COLUMN id_legacy bigint DEFAULT NULL, ADD UNIQUE KEY uq_grade_legacy (id_legacy);
ALTER TABLE programme_mission ADD COLUMN id_legacy bigint DEFAULT NULL, ADD UNIQUE KEY uq_programme_legacy (id_legacy);
ALTER TABLE jour_ferie ADD COLUMN id_legacy bigint DEFAULT NULL, ADD UNIQUE KEY uq_ferie_legacy (id_legacy);
ALTER TABLE reclamation ADD COLUMN id_legacy bigint DEFAULT NULL, ADD UNIQUE KEY uq_reclamation_legacy (id_legacy);
ALTER TABLE demande ADD COLUMN id_legacy bigint DEFAULT NULL, ADD COLUMN source_legacy varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL, ADD UNIQUE KEY uq_demande_legacy (source_legacy, id_legacy);
