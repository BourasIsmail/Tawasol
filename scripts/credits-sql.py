#!/usr/bin/env python3
"""Génère le SQL d'import des crédits et retenues de la paie (table `credit` de la base de paie) dans TAWASSOL.

    scripts/credits-sql.py <base_source> <fichier_sql_sortie> [AAAA-MM] [utilisateur] [mot_de_passe]

<base_source> : base MySQL contenant la table `credit` de la paie (colonnes mat, som, banque, mensualite, num_dossier, m_global, m_restant, nb_mois_rest).
AAAA-MM       : période du dernier état de paie correspondant (défaut 2026-09, à confirmer par la RH).

Le SQL produit est rejouable (réécrit le lot, ne double rien), s'appuie sur le matricule (indépendant des identifiants internes) et s'arrête
immédiatement si la base sélectionnée n'est pas TAWASSOL. Il contient des données financières personnelles : ne jamais le commiter.
"""
import hashlib
import subprocess
import sys
import uuid

src = sys.argv[1]
out = sys.argv[2]
periode = sys.argv[3] if len(sys.argv) > 3 else '2026-09'
user = sys.argv[4] if len(sys.argv) > 4 else 'tw'
pwd = sys.argv[5] if len(sys.argv) > 5 else ''
annee, mois = (int(x) for x in periode.split('-'))

cmd = ['mysql', '-u' + user, '-N', '--default-character-set=utf8mb4', src]
if pwd:
    cmd.insert(2, '-p' + pwd)
rows = subprocess.run(cmd + ['-e', "SELECT mat, banque, num_dossier, mensualite, COALESCE(m_global, 0), m_restant, nb_mois_rest FROM credit ORDER BY mat, num_dossier"],
                      capture_output=True, text=True, check=True).stdout.rstrip('\n').split('\n')
lignes = [r.split('\t') for r in rows if r]

def sql(v):
    return "'" + str(v).replace('\\', '\\\\').replace("'", "''") + "'"

def type_credit(banque):
    # interne_AOS : prêts de l'action sociale de l'Entraide Nationale ; tout le reste (banques, sociétés de crédit, avances, retenues) : « bancaire »,
    # le libellé exact de l'organisme est conservé dans « banque » (H-CREDIT-2)
    return 'interne_AOS' if banque.upper().startswith('A.O.S.') else 'bancaire'

valeurs = []
for mat, banque, dossier, mens, glob, rest, nb in lignes:
    valeurs.append('(%s,%s,%s,%s,%s,%s,%s,%s)' % (sql(mat.strip()), sql(banque.strip()), sql(dossier.strip()), mens, glob, rest, nb, sql(type_credit(banque))))

empreinte = hashlib.sha256(('\n'.join(sorted('|'.join(l) for l in lignes))).encode('utf8')).hexdigest()
lot_id = str(uuid.uuid5(uuid.NAMESPACE_URL, 'tawassol/credits/' + empreinte))
numero = 'REPRISE-C-%04d%02d' % (annee, mois)

o = []
o.append("-- TAWASSOL : crédits et retenues de la paie (%d lignes, %d agents), lot %s, période %s." % (len(lignes), len({l[0] for l in lignes}), numero, periode))
o.append("-- A charger dans la base TAWASSOL, APRÈS les migrations. Rejouable. Données financières personnelles : ne pas diffuser.")
o.append("SET NAMES utf8mb4;")
o.append("-- GARDE-FOU : s'arrête ici si la base sélectionnée n'est pas TAWASSOL (la base de paie a aussi une table « credit », mais sans cette colonne).")
o.append("SELECT COUNT(personnel_id) AS garde_base_tawassol FROM credit;")
o.append("DROP TEMPORARY TABLE IF EXISTS tmp_credit;")
o.append("CREATE TEMPORARY TABLE tmp_credit (mat VARCHAR(20) NOT NULL, banque VARCHAR(150) NOT NULL, num_dossier VARCHAR(100) NOT NULL, mensualite DECIMAL(10,2) NOT NULL, montant_global DECIMAL(12,2) NOT NULL, montant_restant DECIMAL(12,2) NOT NULL, nb_mois INT NOT NULL, type_credit VARCHAR(20) NOT NULL, KEY (mat)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;")
for i in range(0, len(valeurs), 400):
    o.append("INSERT INTO tmp_credit (mat, banque, num_dossier, mensualite, montant_global, montant_restant, nb_mois, type_credit) VALUES " + ",".join(valeurs[i:i + 400]) + ";")
o.append("-- contrôle : matricules du fichier absents de cette base (ces lignes ne sont pas importées)")
o.append("SELECT COUNT(*) AS matricules_inconnus FROM tmp_credit t LEFT JOIN personnel p ON p.matricule = t.mat WHERE p.id IS NULL;")
o.append("INSERT IGNORE INTO import_lot (id, numero, type_import, periode_annee, periode_mois, fichier_nom, fichier_sha256, statut, nb_lignes, nb_erreurs, publie_le, cree_le) VALUES (%s,%s,'credit',%d,%d,%s,%s,'publie',%d,0,NOW(),NOW());"
         % (sql(lot_id), sql(numero), annee, mois, sql('credit.sql (reprise de la paie)'), sql(empreinte), len(lignes)))
o.append("-- rejouable : les crédits de ce lot sont réécrits")
o.append("DELETE FROM credit WHERE lot_id = %s;" % sql(lot_id))
o.append("INSERT INTO credit (id, personnel_id, banque, num_dossier, type_credit, mensualite, montant_global, montant_restant, nb_mois_restants, lot_id, statut, publie_le)")
o.append("SELECT UUID(), p.id, t.banque, t.num_dossier, t.type_credit, t.mensualite, t.montant_global, t.montant_restant, t.nb_mois, %s, 'publie', NOW() FROM tmp_credit t JOIN personnel p ON p.matricule = t.mat;" % sql(lot_id))
o.append("-- un crédit publié d'un lot antérieur pour le même dossier est remplacé (même règle que l'import Excel)")
o.append("UPDATE credit old JOIN credit nw ON nw.personnel_id = old.personnel_id AND nw.num_dossier = old.num_dossier AND nw.lot_id = %s SET old.statut = 'remplace' WHERE old.statut = 'publie' AND old.lot_id <> %s;" % (sql(lot_id), sql(lot_id)))
o.append("SELECT COUNT(*) AS credits_du_lot, COUNT(DISTINCT personnel_id) AS agents FROM credit WHERE lot_id = %s;" % sql(lot_id))
o.append("DROP TEMPORARY TABLE IF EXISTS tmp_credit;")
open(out, 'w', encoding='utf8').write('\n'.join(o) + '\n')
print('%d lignes, %d agents, lot %s (%s)' % (len(lignes), len({l[0] for l in lignes}), numero, lot_id))
