#!/usr/bin/env bash
# Redéploiement de TAWASSOL sur la VM : sauvegarde, mise à jour du code, reconstruction, migrations, chargement du SQL des soldes de congé, contrôles.
#
#   bash scripts/redeployer-vm.sh [fichier.sql] [--yes] [--sans-docker]
#
#   fichier.sql   delta des soldes de congé (défaut : ~/maj_soldes_conge_v2.sql). Mettre « aucun » pour ne charger aucun SQL.
#   --yes         ne pas demander la confirmation (à n'utiliser qu'avec la validation écrite déjà obtenue)
#   --sans-docker ne pas toucher à git ni à docker (essais)
#
# La base (DB_URL, DB_USER, DB_PASSWORD) est lue dans le .env du dépôt : aucun mot de passe n'est tapé ni affiché.
# ATTENTION : le démarrage de la nouvelle version applique les migrations Flyway (V19, V20…) sur la base indiquée dans le .env.
set -euo pipefail

SQL="$HOME/maj_soldes_conge_v2.sql"; OUI=0; DOCKER=1
for a in "$@"; do
  case "$a" in
    --yes) OUI=1 ;;
    --sans-docker) DOCKER=0 ;;
    aucun) SQL="" ;;
    *) SQL="$a" ;;
  esac
done
REPO_DIR="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
cd "$REPO_DIR"

etape() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
echec() { printf '\n\033[31mERREUR : %s\033[0m\n' "$*" >&2; exit 1; }

etape "1/8 Préparation"
[ -f .env ] || echec "fichier .env introuvable dans $REPO_DIR (copiez .env.example en .env et renseignez-le)"
set -a; . ./.env; set +a
: "${DB_URL:?DB_URL absent du .env}"; : "${DB_USER:?DB_USER absent du .env}"; : "${DB_PASSWORD:?DB_PASSWORD absent du .env}"
HOTE=$(printf '%s' "$DB_URL" | sed -E 's#^jdbc:mysql://([^:/?]+).*#\1#')
PORT=$(printf '%s' "$DB_URL" | sed -nE 's#^jdbc:mysql://[^:/?]+:([0-9]+).*#\1#p'); PORT="${PORT:-3306}"
BASE=$(printf '%s' "$DB_URL" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#')
[ -n "$HOTE" ] && [ -n "$BASE" ] || echec "DB_URL illisible : $DB_URL"
export MYSQL_PWD="$DB_PASSWORD"
SQLCMD=(mysql -h "$HOTE" -P "$PORT" -u "$DB_USER" --protocol=TCP --default-character-set=utf8mb4 --max-allowed-packet=256M)
for c in mysql mysqldump; do command -v "$c" >/dev/null || echec "$c introuvable (sudo apt-get install -y mysql-client)"; done
[ "$DOCKER" = 0 ] || { command -v docker >/dev/null || echec "docker introuvable"; command -v git >/dev/null || echec "git introuvable"; }
if [ -n "$SQL" ]; then [ -f "$SQL" ] || echec "fichier SQL introuvable : $SQL (copiez-le sur la VM, par exemple avec scp, ou passez « aucun »)"; fi
"${SQLCMD[@]}" -N "$BASE" -e "SELECT 1" >/dev/null 2>&1 || echec "connexion impossible à $HOTE:$PORT/$BASE avec l'utilisateur $DB_USER"
echo "Base      : $HOTE:$PORT/$BASE (utilisateur $DB_USER)"
echo "Dépôt     : $REPO_DIR"
echo "SQL       : ${SQL:-aucun}"

if [ "$OUI" = 0 ]; then
  echo
  echo "Cette opération applique les migrations de schéma de la nouvelle version sur la base ci-dessus"
  echo "et charge le SQL des soldes de congé. Elle suppose la VALIDATION ÉCRITE du propriétaire de la base."
  read -r -p "Tapez OUI pour continuer : " rep
  [ "$rep" = "OUI" ] || echec "abandon (rien n'a été modifié)"
fi

etape "2/8 Sauvegarde de la base"
DOSSIER="$HOME/sauvegardes-tawassol"; mkdir -p "$DOSSIER"; chmod 700 "$DOSSIER"
SAUVEGARDE="$DOSSIER/${BASE}_$(date +%Y%m%d_%H%M%S).sql.gz"
OPT_TS=""; mysqldump --help 2>/dev/null | grep -q -- '--no-tablespaces' && OPT_TS="--no-tablespaces"
mysqldump -h "$HOTE" -P "$PORT" -u "$DB_USER" --protocol=TCP --single-transaction --default-character-set=utf8mb4 $OPT_TS "$BASE" | gzip > "$SAUVEGARDE"
[ -s "$SAUVEGARDE" ] && [ "$(gzip -l "$SAUVEGARDE" | awk 'NR==2{print $2}')" -gt 1000 ] || echec "sauvegarde vide ou incomplète : $SAUVEGARDE"
chmod 600 "$SAUVEGARDE"
echo "Sauvegarde : $SAUVEGARDE ($(du -h "$SAUVEGARDE" | cut -f1))"

etape "3/8 Mise à jour du code"
if [ "$DOCKER" = 1 ]; then git pull --ff-only origin main; git log --oneline -1; else echo "(ignorée : --sans-docker)"; fi

etape "4/8 Reconstruction et redémarrage"
if [ "$DOCKER" = 1 ]; then docker compose up -d --build; else echo "(ignorée : --sans-docker)"; fi

etape "5/8 Attente des migrations (Flyway)"
if [ "$DOCKER" = 1 ]; then
  ATTENDU=$(ls apps/api/src/main/resources/db/migration | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | tail -1)
  echo "Version de schéma attendue : V$ATTENDU"
  for i in $(seq 1 60); do
    V=$("${SQLCMD[@]}" -N "$BASE" -e "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success = 1" 2>/dev/null || echo 0)
    [ "${V:-0}" -ge "$ATTENDU" ] && break
    printf '.'; sleep 5
  done
  echo
  [ "${V:-0}" -ge "$ATTENDU" ] || { docker compose logs --tail 40 api; echec "la base n'est pas passée à V$ATTENDU après 5 minutes (voir les journaux ci-dessus)"; }
  echo "Schéma à la version V$V."
  for i in $(seq 1 30); do curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1 && break; printf '.'; sleep 3; done; echo
  curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1 || { docker compose logs --tail 40 api; echec "l'API ne répond pas sur le port 8080"; }
  echo "API en service."
else echo "(ignorée : --sans-docker)"; fi

etape "6/8 Chargement du SQL des soldes de congé"
if [ -n "$SQL" ]; then
  JOURNAL="$DOSSIER/chargement_$(date +%Y%m%d_%H%M%S).log"
  if "${SQLCMD[@]}" "$BASE" < "$SQL" > "$JOURNAL" 2>&1; then echo "Chargement terminé. Journal : $JOURNAL"; sed -n '1,12p' "$JOURNAL"
  else cat "$JOURNAL" >&2; echec "le chargement du SQL a échoué (message ci-dessus ; journal : $JOURNAL)"; fi
else echo "(aucun fichier SQL demandé)"; fi

etape "7/8 Contrôles"
ORPH=$("${SQLCMD[@]}" -N "$BASE" -e "SELECT COUNT(*) FROM conge_imputation ci LEFT JOIN demande d ON d.id = ci.demande_id WHERE d.id IS NULL")
NB=$("${SQLCMD[@]}" -N "$BASE" -e "SELECT COUNT(*) FROM conge_imputation")
JOURS=$("${SQLCMD[@]}" -N "$BASE" -e "SELECT COALESCE(SUM(jours),0) FROM conge_imputation")
ECART=$("${SQLCMD[@]}" -N "$BASE" -e "SELECT COALESCE(SUM(a.j - b.p),0) FROM (SELECT annee, SUM(jours) j FROM conge_imputation GROUP BY annee) a LEFT JOIN (SELECT annee, SUM(pris) p FROM solde_conge WHERE source IN ('reconstitue','systeme') GROUP BY annee) b ON b.annee = a.annee")
echo "Imputations de congés      : $NB ($JOURS jours)"
echo "Imputations orphelines     : $ORPH (doit être 0)"
echo "Écart imputés / soldes     : $ECART (doit être 0)"
[ "$ORPH" = "0" ] || echec "des imputations n'ont aucune demande correspondante : rechargez le fichier maj_soldes_conge_v2.sql"
if [ -n "$SQL" ]; then
  [ "$NB" -gt 0 ] || echec "aucune imputation chargée"
  [ "${ECART%.*}" = "0" ] || echec "les soldes ne correspondent pas aux imputations (écart $ECART)"
fi
echo "Soldes de l'agent P4071 (s'il existe) :"
"${SQLCMD[@]}" "$BASE" -e "SELECT s.annee, s.pris AS jours_pris, 22 - s.pris AS reste_si_droit_22 FROM solde_conge s JOIN personnel p ON p.id = s.personnel_id WHERE p.matricule = 'P4071' ORDER BY s.annee DESC" 2>/dev/null || true

etape "8/8 État des services"
if [ "$DOCKER" = 1 ]; then docker compose ps; fi
printf '\n\033[32mRedéploiement terminé.\033[0m Sauvegarde conservée : %s\n' "$SAUVEGARDE"
