# TAWASSOL

Portail RH de l'Entraide Nationale (Maroc) : dossier agent, demandes administratives, notation, paie en lecture, missions, documents officiels. Web, mobile et API unique.

État : **Phase 4A (espace RH, première partie)** terminée côté API et web. Reste la phase 4B (missions et indemnités, carrière et avancement, PDF officiels, rapports), puis le mobile (phase 5). Voir `docs/ARCHITECTURE.md`. Les maquettes validées de la Phase 0 sont la référence de l'interface.

| Dossier | Contenu |
|---|---|
| `apps/api` | API Spring Boot 3 (Java 21), Flyway, JPA, Spring Security |
| `apps/web` | Next.js 15, next-intl (FR/AR, RTL) |
| `packages/shared` | Types, constantes et textes FR/AR partagés |
| `docs/` | `ARCHITECTURE.md`, `SECURITE.md`, `CONFORMITE.md`, `HYPOTHESES.md` |
| `scripts/gen-demo.py` | Génère le jeu de démonstration (80 agents) |

## Base de données externe (Docker)

`docker compose up --build` ne démarre plus MySQL : l'API se connecte à la base indiquée dans `.env` (`DB_URL`, `DB_USER`, `DB_PASSWORD`, voir `.env.example`). Le mot de passe ne va jamais dans git. **Au premier démarrage, Flyway applique les migrations V1 à V18 sur cette base** : à ne faire que sur une base dont le propriétaire l'a validé par écrit. Pour une base locale de secours : `docker compose --profile localdb up` avec `DB_URL` visant `mysql:3306`.

## Démarrage rapide (Docker, moins de 10 minutes)

```bash
cp .env.example .env
# renseigner DB_PASSWORD, DB_ROOT_PASSWORD, JWT_SECRET (openssl rand -base64 48)
# et TAWASSOL_ADMIN_INITIAL_PASSWORD (+ TAWASSOL_DEMO_PASSWORD pour la démo)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
```

- Web : http://localhost:3000 (le français et l'arabe : `/fr/login`, `/ar/login`)
- API : http://localhost:8080, documentation OpenAPI : http://localhost:8080/swagger-ui.html (profil dev)
- Santé : http://localhost:8080/actuator/health

En production, utilisez uniquement `docker-compose.yml` (profil `prod`, OpenAPI désactivé).

## Comptes de démonstration (profil `demo`)

Le mot de passe est celui de `TAWASSOL_DEMO_PASSWORD`. Identifiant = matricule.

| Rôle | Identifiant | Périmètre |
|---|---|---|
| agent | `48217` | lui-même |
| responsable | `31502` | Centre de Salé et ses sous-structures |
| rh | `29044` | tout le personnel |
| admin | `admin` | mot de passe initial puis changement obligatoire ; aucun accès aux données des agents |

## Développement sans Docker

Prérequis : Java 21, Maven 3.9, MySQL 8.4 (ou MariaDB 10.11 pour les tests), Node 22, pnpm 10.

```bash
# API
cd apps/api
export DB_URL='jdbc:mysql://localhost:3306/tawassol' DB_USER=tawassol DB_PASSWORD=...
SPRING_PROFILES_ACTIVE=dev,demo mvn spring-boot:run

# Web
pnpm install && pnpm dev:web
```

## Tests

```bash
cd apps/api
mvn test                                   # Testcontainers démarre MySQL 8.4 (Docker requis)
TEST_DB_URL='jdbc:mysql://localhost:3306/tawassol_test' TEST_DB_USER=tw TEST_DB_PASSWORD=tw mvn test   # base existante, vide
pnpm -r typecheck && pnpm --filter @tawassol/web build
```

La suite couvre notamment les autorisations négatives (un agent ne voit pas un autre agent, un responsable ne sort pas de son périmètre, l'admin technique n'accède pas aux données des agents).

## Espace agent (phase 2)

Fiche et vérification (« Tout est correct » / « Signaler une erreur »), demandes de tous les types avec brouillon, soumission, annulation, historique du circuit et pièces jointes, bulletins (PDF bilingue), primes, crédits, missions, solde de congés, note annuelle et observations, réclamations, documents et annonces ciblés, notifications. Web : `/fr/dashboard`, `/ar/dashboard`. Le navigateur ne voit jamais les jetons : le serveur Next.js les garde dans des cookies `httpOnly` et relaie les appels vers l'API (`/api/bff/*`).

Essai en local sans HTTPS : `COOKIE_SECURE=false` pour le serveur web.

## Espace responsable (phase 3)

File de validation avec routage automatique vers le responsable compétent (remontée dans l'arbre, jamais le demandeur lui-même), validation ou rejet avec commentaire obligatoire, arbre des structures et fiche synthétique sans données financières, notation annuelle (brouillon puis soumission à la RH), calendrier d'équipe, suppléance. S'y ajoutent les écrans « Mot de passe oublié », réinitialisation et premier changement de mot de passe. Web : `/fr/responsable/dashboard`. Compte de démonstration : `31502`.

## Espace RH (phase 4A)

Traitement des demandes (second niveau, délai de réponse signalé), dossiers agents avec historique de carrière automatique, grades, structures et délégations, **imports Excel/CSV** en trois temps sans état (analyse, contrôle, import) avec modèles d'import enregistrés en base, lots en brouillon puis publication irréversible, campagne de notation (contrôle, renvoi, validation, publication), réclamations, annonces et documents ciblés. Compte de démonstration : `29044`. Web : `/fr/rh/dashboard`. Sur mobile, seuls le tableau de bord et la file de traitement sont proposés.

Exemple de fichier de salaires acceptés : colonnes `Matricule`, `Net à payer`, `Allocations familiales`, `Retenue mutuelle` ; la correspondance est proposée automatiquement et se règle par modèle.

## Migrations

`apps/api/src/main/resources/db/migration` : `V1` reprend le dump d'origine (ne jamais la modifier), `V2` et `V3` ajoutent les évolutions du cahier des charges, `V4` les données de référence, `V5` fériés et paramètres, `V6` espace agent (réclamations typées, accusés de lecture), `V7` horodatage à la milliseconde et ordre de l'historique, `V8` responsable compétent par demande, campagne de notation, jetons de réinitialisation, `V9` lignes financières « remplacées » et commentaires de notation. `V10` à `V15` ajoutent le module Déplacements (barèmes, circuit, numéros hérités, statut finalisée), les rôles et permissions fines et la reprise de Suivi RH. `db/demo/V900` est chargée uniquement avec le profil `demo`.


## Module Déplacements et reprise de Suivi RH

- **Circuit** : brouillon, soumise, validée par **tous** les responsables de la hiérarchie dans l'ordre (structure, délégation, coordination, avec suppléance), approuvée (numéro d'ordre), finalisée (numéro d'état, montants figés), payée ; API `/api/v1/missions`. Permissions fines `mission.*`, séparation des tâches, périmètre appliqué à un seul endroit.
- **Calcul** : repas (seuils et taux datés, algorithme de Suivi RH vérifié sur 514 cas de référence), frais de transport, indemnité kilométrique. Tout barème non validé est signalé.
- **Documents** : ordre de mission, état des sommes dues et état kilométrique en PDF bilingue (Amiri, Noto Sans), QR de vérification publique, signataire habilité à la date de l'ordre, montant en lettres FR et AR. Exports Excel et analytique.
- **Reprise** : `docs/MIGRATION.md`. Commande `tawassol-migrate` (profil `migrate`), essai à blanc par défaut, mode réel refusé sans référence de validation écrite.

```bash
java -jar tawassol-api.jar --spring.profiles.active=migrate \
  --tawassol.migrate.source.url=jdbc:mysql://HOTE/suivirh --tawassol.migrate.source.user=... --tawassol.migrate.source.password=... \
  --tawassol.migrate.mode=a_blanc --tawassol.migrate.output-dir=./migration-out
```

Comptes de démonstration du module : `29044` (cellule des déplacements), `20063` (cellule de paie). Parcours complet testé par `scripts/e2e-deplacements.js`.
