#!/usr/bin/env python3
"""Génère db/demo/V900__demo_data.sql : jeu de démonstration anonymisé et déterministe.
3 coordinations utilisées, 6 délégations, 15 structures, 80 agents, quelques demandes dans chaque statut.
Les noms sont des combinaisons aléatoires de prénoms et noms courants : aucun agent réel."""
import random, sys
random.seed(2026)
q = lambda s: "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"
U = lambda kind, n: f"00000000-0000-4000-{kind}000-{n:012d}"
F = [("Samira","سميرة"),("Nadia","نادية"),("Latifa","لطيفة"),("Salma","سلمى"),("Imane","إيمان"),("Khadija","خديجة"),("Fatima","فاطمة"),("Amal","أمل"),("Hanane","حنان"),("Zineb","زينب"),("Meryem","مريم"),("Houda","هدى")]
M = [("Karim","كريم"),("Hassan","حسن"),("Omar","عمر"),("Rachid","رشيد"),("Youssef","يوسف"),("Mohamed","محمد"),("Ahmed","أحمد"),("Said","سعيد"),("Abdelilah","عبد الإله"),("Khalid","خالد"),("Mehdi","المهدي"),("Anas","أنس")]
N = [("El Idrissi","الإدريسي"),("Benjelloun","بنجلون"),("Alami","العلمي"),("Benali","بنعلي"),("Chraibi","الشرايبي"),("Naciri","الناصري"),("Amrani","العمراني"),("Fassi","الفاسي"),("Tazi","التازي"),("Alaoui","العلوي"),("Berrada","برادة"),("Bennani","بناني"),("Lahlou","لحلو"),("Skalli","السقلي"),("Tahiri","الطاهري"),("Kettani","الكتاني"),("Zniber","زنيبر"),("Mernissi","المرنيسي"),("Cherkaoui","الشرقاوي"),("Hajji","الحاجي")]
DEL = [("SAL","R04","سلا","Salé"),("RAB","R04","الرباط","Rabat"),("KEN","R04","القنيطرة","Kénitra"),("CAS","R06","الدار البيضاء أنفا","Casablanca-Anfa"),("MOH","R06","المحمدية","Mohammedia"),("TNG","R01","طنجة أصيلة","Tanger-Assilah")]
# (délégation index, parent index ou None, type, code, nom fr, nom ar)
STR = [(0,None,"centre","CEF-SAL","Centre d'éducation et de formation de Salé","مركز التربية والتكوين سلا"),
 (0,0,"etablissement","ATL-SAL","Atelier couture de Salé","ورشة الخياطة سلا"),
 (0,None,"association","ASS-AMAL","Association Al Amal","جمعية الأمل"),
 (1,None,"complexe","CPX-RAB","Complexe social de Rabat","المركب الاجتماعي الرباط"),
 (1,3,"centre","CTR-RAB1","Centre d'accueil de Rabat","مركز الاستقبال الرباط"),
 (1,3,"etablissement","ETB-RAB2","Établissement de protection de Rabat","مؤسسة الحماية الرباط"),
 (2,None,"centre","CEF-KEN","Centre d'éducation et de formation de Kénitra","مركز التربية والتكوين القنيطرة"),
 (2,6,"etablissement","ATL-KEN","Atelier broderie de Kénitra","ورشة التطريز القنيطرة"),
 (3,None,"complexe","CPX-CAS","Complexe social d'Anfa","المركب الاجتماعي أنفا"),
 (3,8,"centre","CTR-CAS1","Centre de jour d'Anfa","مركز النهار أنفا"),
 (3,None,"association","ASS-NOUR","Association Annour","جمعية النور"),
 (4,None,"centre","CEF-MOH","Centre de formation de Mohammedia","مركز التكوين المحمدية"),
 (4,11,"etablissement","ETB-MOH","Foyer de Mohammedia","دار المحمدية"),
 (5,None,"centre","CEF-TNG","Centre d'éducation de Tanger","مركز التربية طنجة"),
 (5,13,"etablissement","ATL-TNG","Atelier d'artisanat de Tanger","ورشة الصناعة التقليدية طنجة")]
GR = [(11,"Administrateur 2e grade","متصرف من الدرجة الثانية",11),(12,"Administrateur 3e grade","متصرف من الدرجة الثالثة",10),(13,"Technicien 3e grade","تقني من الدرجة الثالثة",8),(14,"Rédacteur 2e grade","محرر من الدرجة الثانية",8),(15,"Adjoint administratif 2e grade","مساعد إداري من الدرجة الثانية",6),(16,"Adjoint technique 2e grade","مساعد تقني من الدرجة الثانية",6),(17,"Assistant social","مساعد اجتماعي",9)]
o = ["-- V900 : jeu de démonstration anonymisé (profil demo uniquement). Généré par scripts/gen-demo.py : ne pas modifier à la main.\n"]
for i,(c,r,ar,fr) in enumerate(DEL):
    o.append(f"INSERT INTO delegation_province (id, coordination_id, code_province, nom_province_ar, nom_province_fr, nom_deleg_ar, nom_deleg_fr) VALUES ({q(U('b',i+1))}, (SELECT id FROM coordination_region WHERE code_region = {q(r)}), {q(c)}, {q(ar)}, {q(fr)}, {q('مندوبية '+ar)}, {q('Délégation de '+fr)});")
for i,(d,p,t,c,fr,ar) in enumerate(STR):
    o.append(f"INSERT INTO structure (id, delegation_id, parent_id, type, code, nom_ar, nom_fr) VALUES ({q(U('c',i+1))}, {q(U('b',d+1))}, {q(U('c',p+1)) if p is not None else 'NULL'}, {q(t)}, {q(c)}, {q(ar)}, {q(fr)});")
for i,(c,fr,ar,e) in enumerate(GR):
    o.append(f"INSERT INTO grade (id, code, libelle_fr, libelle_ar, echelle, nb_echelons) VALUES ({q(U('e',i+1))}, {c}, {q(fr)}, {q(ar)}, {e}, 10);")
agents = []
named = {0:("Samira","سميرة","El Idrissi","الإدريسي",48217,0),1:("Karim","كريم","Benjelloun","بنجلون",31502,0),2:("Nadia","نادية","Tazi","التازي",29044,0)}
for k in range(80):
    if k in named: fn,fa,ln,la,mat,s = named[k]
    else:
        fn,fa = random.choice(F+M); ln,la = random.choice(N); mat = 20000+k*7; s = random.randrange(len(STR))
    grade = random.randrange(len(GR)); echelon = random.randint(1,10); year = random.randint(2004,2024)
    sex = 'F' if (fn,fa) in F else 'M'
    agents.append(dict(id=U('d',k+1),fn=fn,fa=fa,ln=ln,la=la,mat=mat,s=s,g=grade))
    o.append(f"INSERT INTO personnel (id, grade_id, structure_id, matricule, nom_ar, prenom_ar, nom_fr, prenom_fr, date_recrutement, email, echelon, echelle, date_echelon, sexe, position_administrative) VALUES ({q(U('d',k+1))}, {q(U('e',grade+1))}, {q(U('c',s+1))}, {q(str(mat))}, {q(la)}, {q(fa)}, {q(ln)}, {q(fn)}, '{year}-{random.randint(1,12):02d}-{random.randint(1,28):02d}', {q((fn+'.'+ln).lower().replace(' ','')+'@demo.example.ma')}, {q(str(echelon))}, {GR[grade][3]}, '{random.randint(2019,2025)}-{random.randint(1,12):02d}-01', {q(sex)}, 'activite');")
# comptes : mot de passe défini au démarrage (profil demo) par AccountBootstrap
def user(k, role):
    a = agents[k]; o.append(f"INSERT INTO utilisateur (id, personnel_id, login, mot_de_passe, role) VALUES ({q(U('f',k+1))}, {q(a['id'])}, {q(str(a['mat']))}, '$DEMO$', {q(role)});")
user(0,'agent'); user(1,'responsable'); user(2,'rh')
for k in (3,4,5,6,7,8): user(k,'responsable')
user(9,'rh')
# rôles fins du module Déplacements : 29044 = cellule des déplacements (approuve, finalise), 20063 = cellule de paie (valide le paiement)
o.append("UPDATE utilisateur SET role_id = (SELECT id FROM role WHERE code = 'cellule_deplacements') WHERE login = '29044';")
o.append("UPDATE utilisateur SET role_id = (SELECT id FROM role WHERE code = 'cellule_paie') WHERE login = '20063';")
for k in (10,11,12,13): user(k,'agent')
# périmètres : Karim = centre de Salé ; un responsable par délégation ; un responsable de coordination (Rabat-Salé-Kénitra)
def perim(k, typ, col, val): o.append(f"INSERT INTO responsable_perimetre (personnel_id, type_perimetre, {col}, date_debut) VALUES ({q(agents[k]['id'])}, {q(typ)}, {val}, '2025-09-01');")
perim(1,'structure','structure_id',q(U('c',1)))
for j,k in enumerate((3,4,5,6,7)): perim(k,'delegation','delegation_id',q(U('b',j+2)))
perim(8,'coordination','coordination_id',"(SELECT id FROM coordination_region WHERE code_region = 'R04')")
for k in range(80):
    o.append(f"INSERT INTO solde_conge (personnel_id, annee, droit, pris, reporte) VALUES ({q(agents[k]['id'])}, 2026, 22.0, {random.choice([0,2,5,7.5,10])}, 0.0);")
# demandes dans chaque statut
types = ["CONGE_ANNUEL","ATTESTATION","AVANCE_SALAIRE","FORMATION","CONGE_MALADIE","ORDRE_MISSION","CONGE_ANNUEL","AUTRE"]
statuts = ["brouillon","soumise","validee_responsable","approuvee","rejetee_responsable","rejetee","annulee"]
ks = [i for i, a in enumerate(agents) if a['s'] in (0, 1) and i != 1]
n = 0
numbered = 0
for st in statuts:
    for rep in range(2):
        n += 1; k = ks[n % len(ks)]
        t = types[n % len(types)]
        if st != 'brouillon': numbered += 1
        num = "NULL" if st == 'brouillon' else q('DEM-2026-%06d' % numbered)
        o.append(f"INSERT INTO demande (id, numero, personnel_id, type_demande_id, objet, statut, date_debut, date_fin, nb_jours) VALUES ({q(U('a',n))}, {num}, {q(agents[k]['id'])}, (SELECT id FROM type_demande WHERE code = {q(t)}), {q('Demande de démonstration ' + str(n))}, {q(st)}, '2026-11-23', '2026-11-27', 5.0);")
        if st in ('soumise','validee_responsable','rejetee_responsable','approuvee','rejetee'):
            o.append(f"UPDATE demande SET responsable_personnel_id = {q(agents[1]['id'])} WHERE id = {q(U('a',n))};")
        flow = {"brouillon":[],"soumise":["soumise"],"validee_responsable":["soumise","validee_responsable"],"approuvee":["soumise","validee_responsable","approuvee"],"rejetee_responsable":["soumise","rejetee_responsable"],"rejetee":["soumise","validee_responsable","rejetee"],"annulee":["soumise","annulee"]}[st]
        prev = 'brouillon'
        for s2 in flow:
            c = "Motif de démonstration" if s2.startswith("rejetee") else "NULL"
            o.append(f"INSERT INTO demande_historique (demande_id, ancien_statut, nouveau_statut, commentaire) VALUES ({q(U('a',n))}, {q(prev)}, {q(s2)}, {q(c) if c!='NULL' else 'NULL'});"); prev = s2
o.append("INSERT INTO campagne_notation (annee, date_ouverture, date_cloture, bareme_max) VALUES (2026, '2026-11-02', '2026-12-15', 20);")
# le compteur de numéros doit refléter les numéros déjà attribués, sinon la prochaine soumission entre en collision
o.append(f"INSERT INTO compteur_numero (cle, annee, valeur) VALUES ('DEM', 2026, {numbered});")
open(sys.argv[1] if len(sys.argv)>1 else 'apps/api/src/main/resources/db/demo/V900__demo_data.sql','w',encoding='utf8').write("\n".join(o)+"\n")
print(len(o),"lignes SQL")
