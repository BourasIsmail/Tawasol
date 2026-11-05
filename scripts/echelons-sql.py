#!/usr/bin/env python3
"""Génère le SQL qui corrige échelle, échelon et leurs dates à partir des bulletins de paie (table salaire_fonctionnaire de la paie).

    scripts/echelons-sql.py <base_source> <fichier_sql_sortie> [utilisateur] [mot_de_passe]

Règle confirmée par la RH : dans la paie, « ech » = échelle et « echl » = échelon ; « date_ech » = date d'échelle et « date_echl » = date d'échelon.
Le SQL est rejouable, s'appuie sur le matricule, ne modifie que les agents présents dans les bulletins, et s'arrête immédiatement si la base n'est pas
TAWASSOL à jour (migration V22). Il contient des données personnelles : ne jamais le commiter.
"""
import re
import subprocess
import sys

src, out = sys.argv[1], sys.argv[2]
user = sys.argv[3] if len(sys.argv) > 3 else 'tw'
pwd = sys.argv[4] if len(sys.argv) > 4 else ''

cmd = ['mysql', '-u' + user, '-N', '--default-character-set=utf8mb4', src]
if pwd:
    cmd.insert(2, '-p' + pwd)
rows = subprocess.run(cmd + ['-e', "SELECT TRIM(matricule), annee, mois, ech, echl, date_ech, date_echl FROM salaire_fonctionnaire ORDER BY matricule, annee, mois"],
                      capture_output=True, text=True, check=True).stdout.rstrip('\n').split('\n')

def txt(v):
    v = (v or '').strip()
    return None if v in ('', 'NULL') else v

def num(v):
    v = txt(v)
    return v if v and re.fullmatch(r'\d{1,3}', v) else None

def date(v):
    v = txt(v)
    return v[:10] if v and re.fullmatch(r'(19|20)\d\d-\d\d-\d\d.*', v) and not v.startswith('0000') else None

def q(v):
    return 'NULL' if v is None else "'" + v.replace("'", "''") + "'"

valeurs = []
for r in rows:
    if not r:
        continue
    mat, annee, mois, ech, echl, d_ech, d_echl = (r.split('\t') + [''] * 7)[:7]
    if not txt(mat):
        continue
    valeurs.append('(%s,%d,%d,%s,%s,%s,%s)' % (q(mat.strip()), int(annee), int(mois), q(num(ech)), q(num(echl)), q(date(d_ech)), q(date(d_echl))))

o = []
o.append("-- TAWASSOL : correction de l'échelle, de l'échelon et de leurs dates d'après les bulletins de paie (%d bulletins)." % len(valeurs))
o.append("-- « ech » = échelle, « echl » = échelon. A charger dans la base TAWASSOL APRÈS la migration V22. Rejouable. Données personnelles : ne pas diffuser.")
o.append("SET NAMES utf8mb4;")
o.append("-- GARDE-FOU : s'arrête ici si la base n'est pas TAWASSOL à jour (colonne date_echelle absente).")
o.append("SELECT COUNT(date_echelle) AS garde_base_tawassol_v22 FROM personnel;")
o.append("DROP TEMPORARY TABLE IF EXISTS tmp_bulletin;")
o.append("DROP TEMPORARY TABLE IF EXISTS tmp_dernier;")
o.append("CREATE TEMPORARY TABLE tmp_bulletin (mat VARCHAR(20) NOT NULL, annee SMALLINT NOT NULL, mois TINYINT NOT NULL, ech VARCHAR(10) NULL, echl VARCHAR(10) NULL, date_ech DATE NULL, date_echl DATE NULL, KEY (mat), KEY (annee, mois)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;")
for i in range(0, len(valeurs), 500):
    o.append("INSERT INTO tmp_bulletin (mat, annee, mois, ech, echl, date_ech, date_echl) VALUES " + ",".join(valeurs[i:i + 500]) + ";")
o.append("-- chaque bulletin porte l'échelon (echl), pas l'échelle")
o.append("UPDATE salaire s JOIN personnel p ON p.id = s.personnel_id JOIN tmp_bulletin t ON t.mat = p.matricule AND t.annee = s.annee AND t.mois = s.mois SET s.echelon = t.echl WHERE t.echl IS NOT NULL;")
o.append("-- dernier bulletin à échelon lisible de chaque agent (MySQL interdit de citer deux fois une table temporaire dans une requête : table intermédiaire)")
o.append("DROP TEMPORARY TABLE IF EXISTS tmp_periode;")
o.append("CREATE TEMPORARY TABLE tmp_periode (mat VARCHAR(20) NOT NULL, p INT NOT NULL, PRIMARY KEY (mat)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;")
o.append("INSERT INTO tmp_periode SELECT mat, MAX(annee * 100 + mois) FROM tmp_bulletin WHERE echl IS NOT NULL GROUP BY mat;")
o.append("CREATE TEMPORARY TABLE tmp_dernier (mat VARCHAR(20) NOT NULL, ech VARCHAR(10) NULL, echl VARCHAR(10) NOT NULL, date_ech DATE NULL, date_echl DATE NULL, PRIMARY KEY (mat)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;")
o.append("-- agrégats : deux lignes du même mois pour un matricule ne doivent pas faire échouer la requête (ONLY_FULL_GROUP_BY)")
o.append("INSERT INTO tmp_dernier SELECT t.mat, MAX(t.ech), MAX(t.echl), MAX(t.date_ech), MAX(t.date_echl) FROM tmp_bulletin t JOIN tmp_periode m ON m.mat = t.mat AND m.p = t.annee * 100 + t.mois WHERE t.echl IS NOT NULL GROUP BY t.mat;")
o.append("UPDATE personnel p JOIN tmp_dernier d ON d.mat = p.matricule SET p.echelon = d.echl, p.echelle = COALESCE(CAST(d.ech AS UNSIGNED), p.echelle), p.date_echelle = d.date_ech, p.date_echelon = d.date_echl;")
o.append("SELECT COUNT(*) AS agents_corriges FROM personnel p JOIN tmp_dernier d ON d.mat = p.matricule;")
o.append("DROP TEMPORARY TABLE IF EXISTS tmp_dernier;")
o.append("DROP TEMPORARY TABLE IF EXISTS tmp_periode;")
o.append("DROP TEMPORARY TABLE IF EXISTS tmp_bulletin;")
open(out, 'w', encoding='utf8').write('\n'.join(o) + '\n')
print('%d bulletins' % len(valeurs))
