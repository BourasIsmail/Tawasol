#!/usr/bin/env bash
# Exporte une base TAWASSOL déjà remplie en scripts SQL à charger directement dans une autre base MySQL 8.4.
#
#   scripts/export-sql.sh <base_source> <dossier_sortie> [utilisateur] [mot_de_passe]
#
# Produit, dans l'ordre de chargement :
#   00_schema.sql        table Flyway + schéma (concaténation des migrations V1..V18, valables MySQL 8.4)
#   01_reference.sql     historique Flyway, rôles, permissions, paramètres, barèmes, référentiels
#   02_organisation.sql  coordinations, délégations, structures, grades, programmes, compteurs
#   03_personnel.sql     personnel, périmètres, suppléances, carrière, comptes hérités (inactifs)
#   04_demandes.sql      demandes, historiques, chaîne de validation, documents officiels
#   05_missions.sql      ordres de mission et historiques
#   06_paie_notes.sql    lots d'import, salaires, crédits, primes, notes annuelles, examens
#   07_divers.sql        annonces, documents, notifications, archive Suivi RH
#   08_comptes_agents.sql  un compte par agent (identifiant = matricule, mot de passe initial « password », changement imposé)
#   charger.sh           charge tout dans une base VIDE
#
# Exclus : comptes admin et test.* (l'admin est recréé au démarrage de l'API), sessions, jetons, journal d'audit,
# tables techniques de l'outil de migration, appareils push.
# ATTENTION : les fichiers produits contiennent des données personnelles réelles. Ne jamais les commiter.
set -euo pipefail
SRC="${1:?base source}"; OUT="${2:?dossier de sortie}"; U="${3:-${MYSQL_USER:-tw}}"; P="${4:-${MYSQL_PWD:-}}"
HERE="$(cd "$(dirname "$0")" && pwd)"
MIG="$HERE/../apps/api/src/main/resources/db/migration"
mkdir -p "$OUT"
DUMP=$(command -v mariadb-dump || command -v mysqldump)
EXCL="(SELECT id FROM utilisateur WHERE login='admin' OR login LIKE 'test.%')"
args=(-u"$U" --no-create-info --skip-triggers --skip-comments --complete-insert --extended-insert --hex-blob --skip-tz-utc --single-transaction --default-character-set=utf8mb4 --insert-ignore --skip-add-locks --skip-disable-keys)
[ -n "$P" ] && args+=(-p"$P")

dump() { # fichier tables...
  local f="$1"; shift
  { echo "-- TAWASSOL : données exportées de $SRC. Contient des données personnelles."; echo "SET NAMES utf8mb4; SET FOREIGN_KEY_CHECKS=0; SET UNIQUE_CHECKS=0;"
    "$DUMP" "${args[@]}" "$SRC" "$@"
    echo "SET FOREIGN_KEY_CHECKS=1; SET UNIQUE_CHECKS=1;"; } > "$OUT/$f"
}

# 00 : schéma = migrations Flyway dans l'ordre numérique
{ echo "SET NAMES utf8mb4;"; echo "CREATE TABLE flyway_schema_history (installed_rank INT NOT NULL, version VARCHAR(50), description VARCHAR(200) NOT NULL, type VARCHAR(20) NOT NULL, script VARCHAR(1000) NOT NULL, checksum INT, installed_by VARCHAR(100) NOT NULL, installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, execution_time INT NOT NULL, success TINYINT(1) NOT NULL, PRIMARY KEY (installed_rank), KEY flyway_schema_history_s_idx (success));"; for f in $(ls "$MIG" | sort -V); do echo "-- $f"; cat "$MIG/$f"; echo; done; } > "$OUT/00_schema.sql"

# Les migrations sèment déjà ces référentiels avec des identifiants différents : on les vide puis on recharge ceux de la source,
# sinon les demandes et missions pointeraient vers des identifiants inexistants.
REF="role permission role_permission parametre bareme_indemnite bareme_repas jour_ferie type_demande type_document moyen_transport puissance_fiscale"
{ echo "SET NAMES utf8mb4; SET FOREIGN_KEY_CHECKS=0;"
  for t in role_permission role permission parametre bareme_indemnite bareme_repas jour_ferie type_demande type_document moyen_transport puissance_fiscale; do echo "DELETE FROM $t;"; done
  "$DUMP" "${args[@]}" "$SRC" $REF
  echo "SET FOREIGN_KEY_CHECKS=1;"; } > "$OUT/01_reference.sql"
dump 02_organisation.sql coordination_region delegation_province delegation_compteur structure grade responsabilite programme_mission programme_mission_plafond signataire_autorisation compteur_numero campagne_notation
dump 03_personnel.sql personnel responsable_perimetre personnel_programme solde_conge suppleance historique_carriere
{ echo "SET NAMES utf8mb4; SET FOREIGN_KEY_CHECKS=0;"; "$DUMP" "${args[@]}" --where="login <> 'admin' AND login NOT LIKE 'test.%'" "$SRC" utilisateur; echo "SET FOREIGN_KEY_CHECKS=1;"; } >> "$OUT/03_personnel.sql"
dump 04_demandes.sql demande demande_historique etape_validation piece_jointe document_officiel reclamation
dump 05_missions.sql ordre_mission ordre_mission_historique
dump 06_paie_notes.sql import_modele import_lot salaire salaire_ligne credit prime note_annuelle note_critere examen_grade
{ echo "SET NAMES utf8mb4; SET FOREIGN_KEY_CHECKS=0;"
  "$DUMP" "${args[@]}" "$SRC" annonce document legacy_archive
  "$DUMP" "${args[@]}" --where="utilisateur_id NOT IN $EXCL" "$SRC" notification document_lecture
  echo "SET FOREIGN_KEY_CHECKS=1;"; } > "$OUT/07_divers.sql"

# 08 : un compte par agent (identifiant = matricule), mot de passe initial identique « password » (hash Argon2id), changement imposé.
# Compte actif seulement si l'agent l'est ; responsable si l'agent a un périmètre actif. Les profils RH/admin se créent ensuite dans l'application.
HASH='$argon2id$v=19$m=16384,t=2,p=1$fi3twHZ7Imrye8rx4m3AbQ$Y8xLWnP1LhdMuhpL8Fz/c/9ItafzvrUhNzo97zytqlU'
cat > "$OUT/08_comptes_agents.sql" <<EOSQL
SET NAMES utf8mb4;
-- Mot de passe initial de tous ces comptes : "password". Il doit être changé à la première connexion.
INSERT INTO utilisateur (id, personnel_id, login, mot_de_passe, role, role_id, est_actif, doit_changer_mdp)
SELECT UUID(), p.id, p.matricule, '$HASH',
       CASE WHEN EXISTS (SELECT 1 FROM responsable_perimetre rp WHERE rp.personnel_id = p.id AND rp.est_actif = 1) THEN 'responsable' ELSE 'agent' END,
       (SELECT r.id FROM role r WHERE r.code = CASE WHEN EXISTS (SELECT 1 FROM responsable_perimetre rp WHERE rp.personnel_id = p.id AND rp.est_actif = 1) THEN 'responsable_standard' ELSE 'agent_standard' END),
       p.est_actif, 1
FROM personnel p
WHERE NOT EXISTS (SELECT 1 FROM utilisateur u WHERE u.personnel_id = p.id OR u.login = p.matricule);
EOSQL

cat > "$OUT/charger.sh" <<'EOS'
#!/usr/bin/env bash
# Charge les scripts dans une base VIDE :  MYSQL_PWD='...' ./charger.sh HOTE BASE UTILISATEUR
set -euo pipefail
H="${1:?hôte}"; DB="${2:?base}"; U="${3:?utilisateur}"
cd "$(dirname "$0")"
N=$(mysql -h"$H" -u"$U" -N "$DB" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")
[ "$N" = "0" ] || { echo "La base $DB n'est pas vide ($N tables) : arrêt. Rien n'a été modifié."; exit 1; }
for f in 0*.sql; do echo "== $f"; mysql -h"$H" -u"$U" --default-character-set=utf8mb4 --max-allowed-packet=256M "$DB" < "$f"; done
echo "Terminé. Démarrez l'API : elle recrée le compte admin."
EOS
chmod +x "$OUT/charger.sh"
ls -la "$OUT"

# Fichier unique « INSERT seulement » : à charger APRÈS le premier démarrage de l'API (Flyway crée alors le schéma et sème les référentiels).
{ echo "-- TAWASSOL : données seules (INSERT). A charger après le premier démarrage de l'API, dans la base créée par Flyway."
  echo "-- Contient des données personnelles : ne pas commiter. Mot de passe initial des comptes agents : password"
  cat "$OUT"/0[1-8]_*.sql; } > "$OUT/tawassol_data.sql"
