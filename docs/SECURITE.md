# Sécurité

## Authentification
- Mots de passe : argon2id. Un hash bcrypt hérité est accepté puis remplacé à la première connexion.
- Politique : 12 caractères minimum, majuscule, minuscule, chiffre, symbole (valeurs à valider avec la DGSSI).
- Verrouillage de 15 minutes après 5 échecs. Limitation de 20 tentatives par minute et par IP.
- Le login inconnu et le mauvais mot de passe renvoient la même réponse (message et durée de calcul alignés).
- Le compte `admin` du dump a un hash factice : il est remplacé au démarrage par `TAWASSOL_ADMIN_INITIAL_PASSWORD` et son changement est obligatoire. Sans cette variable, le compte reste inutilisable.
- Mot de passe oublié : réponse identique que le compte existe ou non, limitation par IP, jeton aléatoire de 256 bits stocké haché, usage unique, 30 minutes ; toutes les sessions sont révoquées à la réinitialisation.
- 2FA (TOTP) : colonnes prévues, activation pour RH et admin en phase 4.

## Jetons
- Accès : 15 minutes. Refresh : 30 jours, rotatif, stocké haché, révocable. Réutilisation détectée donc famille révoquée. Déconnexion de tous les appareils disponible.
- Web : les jetons sont dans des cookies `httpOnly` (secure en production, SameSite=Lax) gérés par le serveur Next.js (modèle BFF). Le JavaScript de la page ne les lit jamais.
- Pièces jointes : type réel vérifié sur le contenu, nom assaini, stockage hors webroot sous nom aléatoire, liens signés de 5 minutes. Pas d'antivirus : à brancher (ClamAV) avant la production.

## Autorisation
Voir `ARCHITECTURE.md`. Les tests d'autorisation négatifs sont dans `AuthorizationIT`.

## Données personnelles (loi 09-08)
- Journal d'audit (`journal_audit`) : connexions, échecs, verrouillages, changements de mot de passe, consultation de la fiche d'un autre agent. Aucun mot de passe, jeton ou montant dans le journal.
- Les consultations de bulletins, primes et crédits sont journalisées (`SALAIRE_READ`, `SALAIRE_PDF`, `PRIME_READ`, `CREDIT_READ`).
- Durées de conservation et déclaration CNDP : à définir (voir `CONFORMITE.md`).

## Séparation des tâches
Le demandeur n'est jamais son propre valideur : routage qui l'écarte, et refus explicite pour un suppléant. Le rejet exige un commentaire écrit, consigné et notifié à l'agent. Les décisions sont journalisées.

## Espace RH
- Réservé au rôle RH sur tout `/api/v1/rh/**`, vérifié côté serveur. Les imports financiers n'ont aucun effet visible avant publication ; chaque lot, publication et annulation est journalisé.
- Fichiers importés : type reconnu par le contenu (xlsx, csv), 10 Mo maximum (paramètre), ancien .xls refusé, chemins de fichier assainis. Le rapport d'erreurs CSV est en UTF-8 avec BOM, champs entre guillemets, et neutralise les débuts de cellule `= + - @` (injection de formule).
- Le journal d'audit des modifications de dossiers ne contient pas les données sensibles.

## En-têtes et transport
HSTS, X-Frame-Options, Referrer-Policy, CORS limité aux origines configurées. CSRF désactivé volontairement : l'API est sans état et n'émet pas de cookie d'authentification. À chiffrer en TLS devant l'API (reverse proxy).

## Secrets
Aucun secret dans le dépôt. `JWT_SECRET` (32 caractères minimum) est obligatoire et l'application refuse de démarrer sans lui. Le profil `dev` fournit des valeurs faibles et ne doit jamais être utilisé en production.

## Module Déplacements, documents et reprise

- **Permissions fines** `mission.*` portées par le rôle, lues en base à chaque appel (une révocation est immédiate) ; le rôle standard du profil s'applique à défaut. Chaque action sensible est revérifiée côté serveur, l'interface ne fait qu'afficher.
- **Séparation des tâches** : l'agent ne traite jamais sa propre mission ; l'approbateur est une autre personne que le valideur de niveau 1 ; le paiement est validé par une autre personne que celles qui ont approuvé et finalisé. Un conflit de statut simultané est refusé (verrou optimiste).
- **Périmètre** : une seule clause (MissionService.clauseDePerimetre) pour la liste, les exports et l'analytique ; une mission hors périmètre répond 404.
- **Vérification publique** (QR) : jeton HMAC de 144 bits lié au type, à l'identifiant et au numéro ; réponse sans donnée personnelle, identique pour un jeton faux et un document inconnu, limitée à 120 requêtes par minute et par adresse.
- **Exports Excel** : cellules texte commençant par `=`, `+`, `-`, `@` neutralisées (injection de formule) ; chaque export est journalisé.
- **Documents** : en-têtes `nosniff`, empreinte SHA-256 de chaque PDF émis journalisée ; l'attestation émise est conservée telle quelle.
- **Reprise de Suivi RH** : source en lecture seule, CNIE chiffrée (AES-256-GCM) avec empreinte HMAC et jamais écrite dans un rapport, comptes d'entité migrés désactivés avec changement de mot de passe obligatoire, mode réel refusé sans référence de validation écrite ni clé de chiffrement.

