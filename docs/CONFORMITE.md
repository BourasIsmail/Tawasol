# Registre de conformité TAWASSOL (version initiale)

Aucune règle n'est inventée : chacune est rattachée à un texte à confirmer et sera implémentée comme paramètre daté (date d'effet, référence du texte). Tous les statuts sont « Non validé » tant que la RH et le service juridique n'ont pas répondu.

| # | Règle ou texte de référence | Objet | Module | Statut (RH/juridique, date) | Question à poser |
|---|---|---|---|---|---|
| 1 | Statut général de la fonction publique (dahir n° 1-58-008 du 24/02/1958) | Positions administratives, avancement, notation, discipline | Carrière, Notation | Non validé | Le statut général s'applique-t-il tel quel au personnel de l'Entraide Nationale ? |
| 2 | Statut particulier / règlement du personnel de l'Entraide Nationale | Toutes règles propres à l'établissement | Tous modules | Non validé | Pouvez-vous fournir le texte en vigueur ? |
| 3 | Congé annuel : droit, report, décompte en jours ouvrables | Solde, durée, jours fériés | Demandes, Congés | Non validé | Quel droit annuel et quel mode de décompte (ouvrables/ouvrés) ? |
| 4 | Congés spéciaux (maternité, paternité, décès, mariage, pèlerinage, maladie courte/longue durée) | Durées, justificatifs, effets sur rémunération et avancement | Demandes, Congés | Non validé | Quelles durées et justificatifs ? Effets sur l'avancement ? |
| 5 | Avancement d'échelon : durée et rythme selon la notation | Éligibilité automatique | Carrière | Non validé | Quelle durée par échelon et par grade ? |
| 6 | Avancement de grade : examen d'aptitude, concours professionnel, choix, quotas | Examens, nb_postes | Carrière, Examens | Non validé | Quelles voies et quels quotas ? |
| 7 | Positions administratives : activité, détachement, disponibilité… | Historisation | Dossier agent | Non validé | Quelles positions et quels effets ? |
| 8 | Notation : barème, critères, délais, recours (commission paritaire) | Saisie, publication, observations de l'agent | Notation | Non validé | Barème, délais et procédure de recours ? |
| 9 | Indemnités de déplacement : barèmes par grade/catégorie, taux de repas, plafonds | Calcul d'indemnité | Ordres de mission | Non validé | Quels barèmes en vigueur ? |
| 10 | Régime indemnitaire, primes, IR, cotisations (CMR/CNSS, mutuelle, allocations familiales) | Restitution des données de paie | Paie, primes | Non validé | Le portail doit-il seulement restituer les données ? |
| 11 | Loi 09-08 (CNDP) : finalités, durées de conservation, droit d'accès et de rectification, journalisation | Données personnelles | Sécurité, Audit | Non validé | Déclaration CNDP et durées de conservation par type de donnée ? |
| 12 | Loi 05-20 (cybersécurité) et directives DGSSI | Sécurité, hébergement, sauvegardes | Infra, Auth | Non validé | Exigences d'hébergement et de sauvegarde ? |
| 13 | Loi 43-20 (services de confiance : signature et cachet électroniques) | Documents officiels | PDF | Non validé | Quel prestataire de signature et de cachet ? |
| 14 | Loi 55-19 (simplification des procédures administratives) | Délais de réponse, motivation des rejets | Demandes | Non validé | Quels délais réglementaires de réponse ? |
| 15 | Loi 31-13 (accès à l'information) et loi 69-99 (archives) | Publication, conservation à valeur probante | Documents, Archivage | Non validé | Durées d'archivage par type de document ? |
| 16 | Langue : arabe officiel, version française si nécessaire | Documents bilingues, RTL | Documents, UI | Non validé | Mentions réglementaires et modèles à fournir ? |
| 17 | Jours fériés officiels et calendrier hégirien | Paramètre daté | Congés, Calendrier | Non validé | Quel texte fixe les fêtes religieuses de l'année ? |
| 18 | Séparation des tâches | Qui saisit ne valide pas | Autorisation | Non validé | Règle validée par la direction ? |
| 19 | Indemnités de déplacement : taux de repas par échelle (40/35, 60/48, 80/65, 100/80 lus dans Suivi RH) | Barème daté copié dans chaque mission | Missions | Non validé | Quel texte fixe ces taux et depuis quelle date ? |
| 20 | Décompte des repas : présence 5 h / 3 h / 3 h, intervalles 6 h / 4 h / 3 h, bascule au taux 2 après 15 jours | Moteur de calcul paramétrable (bareme_repas) | Missions | Non validé | Quels seuils sont réglementaires (première semaine d'usage ou actuels) ? |
| 21 | Indemnité kilométrique 1,20 / 1,75 / 2,30 MAD/km selon la puissance fiscale | Voiture privée | Missions | Non validé | Texte et date d'effet ? |
| 22 | Plafonds annuels par programme (700 à 5 000 MAD) | Consommation des plafonds | Missions, Programmes | Non validé | Base légale des plafonds ? |
| 23 | Génération de missions par programme avec dates et destinations tirées au hasard | Non reproduite : planification réelle uniquement, historique migré avec l'origine « generation » | Planification | Non validé | La direction confirme-t-elle la planification réelle, ou un forfait avec son texte ? |
| 24 | Ordre de mission : mentions, signataire habilité à la date, numéro unique, QR, montant en toutes lettres (arabe et français) | Document officiel bilingue | PDF | Non validé | Modèle et mentions à fournir |
| 25 | Conservation des ordres de mission et états payés (loi 69-99) | Archivage à valeur probante | Archivage | Non validé | Durée de conservation ? |
| 26 | Attestations de travail et de salaire : numérotées, signées par le service RH, vérifiables par QR, archivées telles qu'émises | Fichier PDF conservé dans document_officiel avec son empreinte SHA-256 ; jamais régénéré | Documents | Non validé | Mentions obligatoires, signataire officiel et durée de conservation (loi 69-99) à confirmer |
| 27 | Avancement d'échelon : durée dans l'échelon (24 mois par défaut) et décision de référence | Jamais automatique : validé par la RH avec la référence de la décision, tracé dans l'historique de carrière | Carrière | Non validé | Texte statutaire fixant la durée par grade |
| 28 | Conservation et traçabilité des ordres de mission et des états de paiement (empreinte du PDF journalisée à chaque émission) | Journal d'audit et table de correspondance de la reprise | Archivage | Non validé | Durée de conservation à fixer |
| 29 | Séparation des tâches de la chaîne de dépense (saisie, validation, approbation, paiement) | Contrôlée par le service et par les rôles : l'agent ne traite jamais sa propre mission ; le paiement est validé par une autre personne que celles qui ont approuvé et finalisé | Contrôle interne | Non validé | Valider la matrice des rôles avec le contrôle financier |
| 30 | Données personnelles : CNIE chiffrée (AES-256-GCM) avec empreinte HMAC, masquée dans tous les rapports | Clé fournie par variable d'environnement, jamais journalisée ; reprise réelle refusée sans clé | Loi 09-08 | Non validé | Déclaration CNDP et gestion de la clé à organiser avec le service informatique |
| 31 | Validation hiérarchique complète : tous les responsables, dans l'ordre, avant le traitement par la RH ou la cellule des déplacements | Chaîne figée à la soumission, étapes tracées (qui, quand, par suppléance) | Circuit de validation | Non validé | Confirmer que le siège joue le rôle de direction centrale et la règle de suppléance |
