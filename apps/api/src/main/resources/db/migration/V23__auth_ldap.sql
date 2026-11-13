-- Authentification par l'annuaire Active Directory. Un compte « local » (auth_locale = 1) garde son mot de passe TAWASSOL
-- (compte de secours administrateur) ; tous les autres sont authentifiés par l'annuaire quand LDAP_ENABLED=true.
ALTER TABLE utilisateur ADD COLUMN auth_locale TINYINT(1) NOT NULL DEFAULT 0;
UPDATE utilisateur SET auth_locale = 1 WHERE login = 'admin';
