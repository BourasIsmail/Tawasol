# Architecture TAWASSOL

## Vue d'ensemble
Une seule API REST (`/api/v1`) sert le web (Next.js) et le futur mobile (React Native). MySQL 8.4 est la source de vérité. Les clients TypeScript seront générés depuis l'OpenAPI de l'API.

```
web (Next.js) ┐
mobile (Expo) ┴──> API Spring Boot ──> MySQL 8.4
                         ├──> Redis (limitation de débit, cache)      [prévu]
                         └──> MinIO (pièces jointes, PDF)             [prévu]
```

## API
- Java 21, Spring Boot 3.5, threads virtuels, Spring Data JPA pour les entités simples, `NamedParameterJdbcTemplate` pour les requêtes de lecture et le calcul de périmètre.
- Flyway : le schéma est entièrement versionné. La base est créée vide.
- Erreurs homogènes `{status, code, message, details, timestamp}`. Les codes sont stables et traduits côté client.
- Fuseau `Africa/Casablanca` (`Clock` injectable). Le serveur MySQL doit utiliser le même fuseau (`--default-time-zone` dans le compose).

## Autorisation (principe central)
Un seul point de décision, `AccessGuard`, renvoie un `Scope` (tout, périmètre de structures, ou soi-même). Les requêtes sont filtrées dans le SQL, pas après coup.
- **Agent** : lui seul, plus le périmètre d'une suppléance en cours.
- **Responsable** : lui-même, ses structures et leurs sous-structures (récursif), plus les suppléances. Un périmètre peut cibler une structure, une délégation ou une coordination.
- **RH** : tout.
- **Admin** : aucun accès aux données des agents (403).
- Hors périmètre et inexistant donnent la même réponse 404, pour ne pas révéler l'existence d'un identifiant.
- Un responsable reçoit une vue synthétique (`niveau = SYNTHESE`), sans coordonnées ni données financières.

## Authentification
Jeton d'accès JWT de 15 minutes qui ne porte que l'identifiant. Rôle, état du compte et obligation de changer le mot de passe sont relus en base à chaque requête (une désactivation est immédiate). Refresh token opaque, haché (SHA-256), rotatif, par famille : la réutilisation d'un jeton consommé révoque toute la famille. Voir `SECURITE.md`.

## Données
- `V2` : périmètres (`responsable_perimetre`, CHECK une seule cible), suppléances, circuit de demande (7 statuts, historique, numéro unique), soldes, notation, notifications, journal d'audit, jours fériés, paramètres versionnés, historique de carrière, sessions.
- `V3` : champs agent manquants, ciblage des annonces et documents, modèles d'import, lots importés (brouillon puis publié), statut de publication des salaires, primes et crédits, rubriques de bulletin.
- Note MySQL : une action référentielle `ON UPDATE CASCADE` est interdite sur une colonne utilisée dans un CHECK. Les clés concernées utilisent `RESTRICT` (les UUID ne changent jamais).

## Phase 2 : espace agent
- **Demandes** : `DemandeService` applique les règles au moment de la soumission, par type. Congé : jours ouvrables (week-ends et jours fériés exclus, paramétrables), solde en tenant compte des demandes déjà soumises, chevauchement. Maladie : certificat obligatoire. Exceptionnel : motif et justificatif. Avance : plafond et capacité d'endettement (paramètres). Mission : crée l'`ordre_mission` lié. Les montants d'indemnité restent calculés par la RH (phase 4).
- **Numérotation** `DEM-AAAA-NNNNNN` : attribuée à la soumission (pas de trou), compteur par année dans `compteur_numero`.
- **Idempotence** : soumettre ou annuler deux fois renvoie l'état sans doublon d'historique. L'historique garde l'ordre d'insertion (`seq`).
- **Pièces jointes** : stockage disque sous un nom aléatoire (interface `StorageService`, MinIO possible plus tard). Type vérifié sur le contenu (PDF, JPG, PNG), taille et nombre paramétrables, téléchargement par lien signé HMAC à durée limitée.
- **Finance en lecture** : uniquement les lignes `publie` de l'agent du jeton. Les consultations de bulletin sont journalisées. Le PDF est bilingue (arabe en RTL), généré avec OpenPDF et une police embarquée.
- **Web** : Next.js sert de relais (BFF). Les jetons sont dans des cookies `httpOnly`, le rafraîchissement est partagé entre requêtes simultanées pour ne pas déclencher la détection de réutilisation.

## Phase 3 : espace responsable
- **Responsable compétent** : `HierarchyIndex` parcourt la structure de l'agent, ses parents, puis la délégation et la coordination, et prend le premier responsable actif qui n'est pas le demandeur. Le résultat est figé dans `demande.responsable_personnel_id` à la soumission. Aucun responsable, ou type de demande sans validation du responsable (ex. attestation) : le champ reste vide et la demande ira directement à la RH.
- **File de validation** : demandes dont le responsable désigné est l'utilisateur ou la personne qu'il supplée aujourd'hui, jamais la sienne. Un suppléant qui a lui-même une demande dans la file reçoit 403 (`SELF_VALIDATION_FORBIDDEN`).
- **Confidentialité** : pour un congé maladie, le responsable ne voit ni le motif ni les pièces ; le calendrier d'équipe affiche « absence ». Aucune donnée de paie dans ses réponses.
- **Notation** : l'agent noté est celui dont le responsable compétent est l'utilisateur (même règle de remontée), donc un responsable est noté par son supérieur. Brouillon, puis soumission ; une note soumise ou validée est verrouillée. Le barème et les dates viennent de `campagne_notation` (ouverte par la RH en phase 4).
- **Mot de passe oublié** : lien à usage unique de 30 minutes (paramètre), stocké haché, envoyé par e-mail à l'adresse de la fiche. Sans SMTP, rien n'est envoyé (journal seulement, corps visible en profil dev). La réinitialisation révoque toutes les sessions.

## Phase 4A : espace RH
- **Accès** : tout `/api/v1/rh/**` exige le rôle RH (`@PreAuthorize`). Responsable, agent et admin reçoivent 403.
- **Traitement des demandes** : traitables = validées par un responsable, ou soumises sans responsable (`responsable_personnel_id` vide). La RH ne traite pas sa propre demande (la file l'exclut, 403 explicite). L'approbation d'un congé annuel décompte `solde_conge.pris` une seule fois. Les ordres de mission passent par le module Missions (4B) : `USE_MISSION_MODULE`.
- **Dossier agent** : `PersonnelRhService`, mise à jour partielle. Chaque changement de structure, grade, échelon ou position crée un événement `historique_carriere`. L'audit ne copie pas CIN, e-mail, téléphone ni date de naissance.
- **Imports** : sans état, le fichier est renvoyé à chaque étape (analyse, contrôle, import). Format du fichier (encodage, séparateur, décimale, date) et correspondance des colonnes vivent dans `import_modele`, donc modifiables sans développement ; rubriques de bulletin possibles dans le modèle (`salaire_ligne`). Personnel : écriture directe ; salaires, primes, crédits : lot en brouillon, invisible des agents jusqu'à la publication. Une ligne remplacée (crédit) passe en « remplace », jamais supprimée. Un mois de salaires publié refuse tout nouvel import (une correction est une nouvelle écriture tracée, à construire). Un même fichier ne peut être importé deux fois. Le rapport d'erreurs est téléchargeable en CSV.
- **Notation RH** : ouverture de campagne, notes soumises, renvoi motivé, validation (verrou), publication en bloc à l'agent. La RH ne valide pas sa propre note ; elle peut saisir la note d'un agent sans responsable.
- **Publications** : annonces et documents ciblés (tous, délégation, structure). Documents en PDF uniquement, téléchargés par lien signé.

## Prévu aux phases suivantes
Missions et indemnités, carrière et avancement, PDF officiels numérotés avec QR, rapports (phase 4B), mobile (phase 5), durcissement : index, Redis, sauvegardes (phase 6).

## Limites connues de la phase 1
- Le limiteur de connexions est en mémoire (une instance). Passage à Redis (Bucket4j) au durcissement.
- La liste du personnel d'un responsable utilise `IN (:ids)` sur les structures du périmètre. À optimiser (jointure sur CTE) si un périmètre dépasse quelques milliers de structures.
- Les paramètres de sécurité (échecs, durée de verrouillage, longueur du mot de passe) sont dans `application.yml`. La table `parametre` les contient aussi, mais le code ne les lit pas encore.

## Modules ajoutés (déplacements, carrière, documents, reprise)

| Package | Rôle |
|---|---|
| `deplacement` | Moteur de repas (`RepasEngine`, fonction pure), indemnités (`IndemniteService`), numérotation atomique par délégation (`NumerotationMissionService`), circuit (`MissionService`), PDF bilingues (`MissionPdfService`), montant en lettres FR/AR, vérification publique, exports et analytique |
| `carriere` | Historique de carrière, propositions et validation d'avancement d'échelon, examens et concours |
| `document` | Attestations officielles numérotées et archivées |
| `migration` | Commande `tawassol-migrate` : reprise de Suivi RH (référentiels, personnel, comptes, déplacements, congés), essai à blanc, réconciliation |

Schéma : migrations V10 à V16 (barèmes datés, circuit et numéros, rôles et permissions, numéros hérités, statut finalisée, archive, documents officiels).

