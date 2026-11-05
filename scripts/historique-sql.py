#!/usr/bin/env python3
"""Génère les SQL de reprise des salaires des années antérieures, des notes complémentaires et des dates de recrutement.

    scripts/historique-sql.py <base_reprise> <base_deja_chargee> <dossier_sortie> [utilisateur] [mot_de_passe]

<base_reprise>       base TAWASSOL où la reprise a été faite (ex. tawassol_real).
<base_deja_chargee>  base qui représente ce que la cible contient déjà : seules les lignes absentes de celle-ci sont écrites (ex. la base chargée par le précédent export).
                     Passer « - » pour tout écrire.

Produit : maj_salaires_AAAA.sql (un par année), maj_notes_recrutement.sql. Tous sont rejouables, s'appuient sur le matricule (indépendants des identifiants
internes), s'arrêtent dès la première ligne si la base n'est pas TAWASSOL, et ne citent jamais deux fois une table temporaire dans une requête (MySQL).
Ils contiennent des données personnelles et financières : ne jamais les commiter.
"""
import subprocess
import sys
import uuid

src, ref, out = sys.argv[1], sys.argv[2], sys.argv[3]
user = sys.argv[4] if len(sys.argv) > 4 else 'tw'
pwd = sys.argv[5] if len(sys.argv) > 5 else ''

def run(sql):
    cmd = ['mysql', '-u' + user, '-N', '--default-character-set=utf8mb4', src]
    if pwd:
        cmd.insert(2, '-p' + pwd)
    r = subprocess.run(cmd + ['-e', sql], capture_output=True, text=True, check=True).stdout.rstrip('\n')
    return [l.split('\t') for l in r.split('\n') if l]

def q(v):
    return 'NULL' if v is None or v == 'NULL' else "'" + str(v).replace('\\', '\\\\').replace("'", "''") + "'"

def n(v):
    return 'NULL' if v is None or v == 'NULL' else v

def tmp_table(name, colonnes):
    return "CREATE TEMPORARY TABLE %s (%s) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;" % (name, colonnes)

def inserts(table, cols, valeurs, taille=500):
    return ["INSERT INTO %s (%s) VALUES %s;" % (table, cols, ",".join(valeurs[i:i + taille])) for i in range(0, len(valeurs), taille)]

# ---------------------------------------------------------------- salaires, un fichier par année
exclu = "" if ref == '-' else (
    " AND NOT EXISTS (SELECT 1 FROM %s.salaire r JOIN %s.personnel rp ON rp.id = r.personnel_id WHERE rp.matricule = p.matricule AND r.annee = s.annee AND r.mois = s.mois)" % (ref, ref))
annees = [int(r[0]) for r in run("SELECT DISTINCT s.annee FROM salaire s JOIN personnel p ON p.id = s.personnel_id WHERE 1=1" + exclu + " ORDER BY 1")]
for annee in annees:
    lignes = run("SELECT p.matricule, g.code, s.annee, s.mois, s.echelon, s.salaire_net, s.alloc_familiale, s.retenue_mutuelle, s.rappel FROM salaire s JOIN personnel p ON p.id = s.personnel_id "
                 "LEFT JOIN grade g ON g.id = s.grade_id WHERE s.annee = %d%s ORDER BY s.mois, p.matricule" % (annee, exclu))
    mois = sorted({int(l[3]) for l in lignes})
    valeurs = ["(%s,%s,%s,%s,%s,%s,%s,%s,%s)" % (q(l[0]), n(l[1]), l[2], l[3], q(l[4]), l[5], l[6], l[7], l[8]) for l in lignes]
    o = ["-- TAWASSOL : bulletins de paie %d (%d lignes), lots REPRISE-S-%d01 à REPRISE-S-%d12 selon les mois concernés." % (annee, len(lignes), annee, annee),
         "-- A charger dans la base TAWASSOL, APRÈS les migrations. Rejouable. Données financières personnelles : ne pas diffuser.",
         "SET NAMES utf8mb4;",
         "-- GARDE-FOU : s'arrête ici si la base sélectionnée n'est pas TAWASSOL.",
         "SELECT COUNT(lot_id) AS garde_base_tawassol FROM salaire;",
         "DROP TEMPORARY TABLE IF EXISTS tmp_salaire;",
         tmp_table("tmp_salaire", "mat VARCHAR(20) NOT NULL, grade_code INT NULL, annee SMALLINT NOT NULL, mois TINYINT NOT NULL, echelon VARCHAR(10) NULL, net DECIMAL(12,2) NOT NULL, alloc DECIMAL(10,2) NOT NULL, mutuelle DECIMAL(10,2) NOT NULL, rappel DECIMAL(10,2) NOT NULL, KEY (mat), KEY (annee, mois)")]
    o += inserts("tmp_salaire", "mat, grade_code, annee, mois, echelon, net, alloc, mutuelle, rappel", valeurs)
    o.append("-- contrôle : matricules du fichier absents de cette base (ces lignes ne sont pas importées)")
    o.append("SELECT COUNT(*) AS matricules_inconnus FROM tmp_salaire t LEFT JOIN personnel p ON p.matricule = t.mat WHERE p.id IS NULL;")
    for m in mois:
        numero = 'REPRISE-S-%d%02d' % (annee, m)
        o.append("INSERT IGNORE INTO import_lot (id, numero, type_import, periode_annee, periode_mois, fichier_nom, fichier_sha256, statut, nb_lignes, publie_le) VALUES (%s,%s,'salaire',%d,%d,'salaire_fonctionnaire.sql (reprise)',%s,'publie',0,NOW());"
                 % (q(str(uuid.uuid5(uuid.NAMESPACE_URL, 'tawassol/' + numero))), q(numero), annee, m, q('0' * 64)))
    o.append("-- rejouable : les bulletins de ces mois sont réécrits")
    o.append("DELETE s FROM salaire s JOIN personnel p ON p.id = s.personnel_id JOIN tmp_salaire t ON t.mat = p.matricule AND t.annee = s.annee AND t.mois = s.mois;")
    o.append("INSERT INTO salaire (id, personnel_id, grade_id, annee, mois, echelon, salaire_net, alloc_familiale, retenue_mutuelle, rappel, lot_id, statut, publie_le) "
             "SELECT UUID(), p.id, g.id, t.annee, t.mois, t.echelon, t.net, t.alloc, t.mutuelle, t.rappel, l.id, 'publie', NOW() FROM tmp_salaire t JOIN personnel p ON p.matricule = t.mat "
             "LEFT JOIN grade g ON g.code = t.grade_code JOIN import_lot l ON l.numero = CONCAT('REPRISE-S-', t.annee, LPAD(t.mois, 2, '0'));")
    o.append("UPDATE import_lot l SET l.nb_lignes = (SELECT COUNT(*) FROM salaire s WHERE s.lot_id = l.id) WHERE l.type_import = 'salaire' AND l.periode_annee = %d;" % annee)
    o.append("SELECT COUNT(*) AS bulletins_%d, COUNT(DISTINCT personnel_id) AS agents FROM salaire WHERE annee = %d;" % (annee, annee))
    o.append("DROP TEMPORARY TABLE IF EXISTS tmp_salaire;")
    open('%s/maj_salaires_%d.sql' % (out, annee), 'w', encoding='utf8').write('\n'.join(o) + '\n')
    print('maj_salaires_%d.sql : %d bulletins' % (annee, len(lignes)))

# ---------------------------------------------------------------- notes complémentaires et dates de recrutement
exclu_n = "" if ref == '-' else (
    " AND NOT EXISTS (SELECT 1 FROM %s.note_annuelle r JOIN %s.personnel rp ON rp.id = r.personnel_id WHERE rp.matricule = p.matricule AND r.annee = na.annee)" % (ref, ref))
notes = run("SELECT p.matricule, na.annee, na.note FROM note_annuelle na JOIN personnel p ON p.id = na.personnel_id WHERE 1=1" + exclu_n + " ORDER BY na.annee, p.matricule")
recrut = run("SELECT matricule, CAST(date_recrutement AS CHAR) FROM personnel WHERE date_recrutement IS NOT NULL ORDER BY matricule")
o = ["-- TAWASSOL : notes annuelles complémentaires (%d) et dates de recrutement (%d)." % (len(notes), len(recrut)),
     "-- A charger dans la base TAWASSOL, APRÈS les migrations. Rejouable : une note ou une date déjà présente n'est jamais écrasée. Données personnelles : ne pas diffuser.",
     "SET NAMES utf8mb4;",
     "-- GARDE-FOU : s'arrête ici si la base sélectionnée n'est pas TAWASSOL.",
     "SELECT COUNT(personnel_id) AS garde_notes FROM note_annuelle;",
     "SELECT COUNT(date_recrutement) AS garde_personnel FROM personnel;",
     "DROP TEMPORARY TABLE IF EXISTS tmp_note;", "DROP TEMPORARY TABLE IF EXISTS tmp_embauche;",
     tmp_table("tmp_note", "mat VARCHAR(20) NOT NULL, annee SMALLINT NOT NULL, note DECIMAL(5,2) NOT NULL, KEY (mat)")]
o += inserts("tmp_note", "mat, annee, note", ["(%s,%s,%s)" % (q(l[0]), l[1], l[2]) for l in notes])
o.append("INSERT INTO note_annuelle (id, personnel_id, annee, note, statut, publie_le, date_validation) SELECT UUID(), p.id, t.annee, t.note, 'publiee', NOW(), NOW() FROM tmp_note t JOIN personnel p ON p.matricule = t.mat "
         "WHERE NOT EXISTS (SELECT 1 FROM note_annuelle x WHERE x.personnel_id = p.id AND x.annee = t.annee);")
o.append("SELECT COUNT(*) AS notes_total FROM note_annuelle;")
o.append(tmp_table("tmp_embauche", "mat VARCHAR(20) NOT NULL, d DATE NOT NULL, PRIMARY KEY (mat)"))
o += inserts("tmp_embauche", "mat, d", ["(%s,%s)" % (q(l[0]), q(l[1])) for l in recrut])
o.append("UPDATE personnel p JOIN tmp_embauche t ON t.mat = p.matricule SET p.date_recrutement = t.d WHERE p.date_recrutement IS NULL;")
o.append("SELECT COUNT(date_recrutement) AS dates_de_recrutement FROM personnel;")
o.append("DROP TEMPORARY TABLE IF EXISTS tmp_note;")
o.append("DROP TEMPORARY TABLE IF EXISTS tmp_embauche;")
open(out + '/maj_notes_recrutement.sql', 'w', encoding='utf8').write('\n'.join(o) + '\n')
print('maj_notes_recrutement.sql : %d notes, %d dates de recrutement' % (len(notes), len(recrut)))
