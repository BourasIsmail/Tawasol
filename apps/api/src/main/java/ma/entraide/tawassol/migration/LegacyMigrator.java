package ma.entraide.tawassol.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import ma.entraide.tawassol.deplacement.RepasEngine;
import java.sql.Date;
import java.util.*;

/**
 * Reprise des référentiels, du personnel et des comptes de Suivi RH (phase M2).
 * La source est lue en lecture seule. Chaque ligne reprise est consignée dans migration_map.
 * Rejouable : une ligne dont id_legacy existe déjà dans la cible n'est jamais dupliquée.
 */
public class LegacyMigrator {
    private final JdbcTemplate src;
    private final JdbcTemplate dst;
    private final ObjectMapper om = new ObjectMapper();
    private final CnieProtector cnie;
    private final String runId = UUID.randomUUID().toString();
    private final Map<String, Map<String, Object>> compteurs = new LinkedHashMap<>();
    private final List<Map<String, Object>> anomalies = new ArrayList<>();
    private final List<String[]> controleNoms = new ArrayList<>();
    private final List<Map<String, Object>> comptesPerimetres = new ArrayList<>();

    // correspondances id Suivi RH -> id cible
    private final Map<Long, String> coordination = new HashMap<>();
    private final Map<Long, String> delegation = new HashMap<>();
    private final Map<Long, String> delegationSymbole = new HashMap<>();
    private final Map<Long, String> structureUnite = new HashMap<>();
    private final Map<Long, String> grade = new HashMap<>();
    private final Map<Long, String> responsabilite = new HashMap<>();
    private final Map<Long, String> programme = new HashMap<>();
    private final Map<String, String> placeholder = new HashMap<>();
    private String delegationSiege;

    public LegacyMigrator(JdbcTemplate source, JdbcTemplate target, CnieProtector cnie) {
        this.src = source; this.dst = target; this.cnie = cnie;
    }

    public String runId() { return runId; }
    public List<String[]> controleNoms() { return controleNoms; }
    public List<Map<String, Object>> anomalies() { return anomalies; }
    public List<Map<String, Object>> comptesPerimetres() { return comptesPerimetres; }

    public Map<String, Object> rapport() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("run_id", runId);
        r.put("tables", compteurs);
        Map<String, Long> parCode = new TreeMap<>();
        for (Map<String, Object> a : anomalies) parCode.merge((String) a.get("code"), 1L, Long::sum);
        r.put("anomalies_par_code", parCode);
        r.put("anomalies_echantillon", anomalies.size() > 60 ? anomalies.subList(0, 60) : anomalies);
        r.put("noms_a_controler", controleNoms.size());
        return r;
    }

    /** Exécute toutes les étapes dans l'ordre des dépendances. À appeler dans une transaction. */
    public void executer() {
        coordinationsEtDelegations();
        unitesAdministratives();
        grades();
        referentielsSimples();
        programmes();
        joursFeries();
        personnes();
        comptes();
        verifierBaremes();
        deplacements();
        conges();
        messages();
        initialiserCompteurs();
        reconciliation();
    }

    // ------------------------------------------------------------------ utilitaires

    private Map<String, Object> c(String table) {
        return compteurs.computeIfAbsent(table, k -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("lus", 0); m.put("crees", 0); m.put("deja_presents", 0); m.put("ignores", 0); return m; });
    }
    private void inc(String table, String k) { Map<String, Object> m = c(table); m.put(k, (Integer) m.get(k) + 1); }
    private void anomalie(String code, String table, Object id, String detail) {
        Map<String, Object> a = new LinkedHashMap<>(); a.put("code", code); a.put("table", table); a.put("id_legacy", String.valueOf(id)); a.put("detail", detail); anomalies.add(a);
    }
    private String newId() { return UUID.randomUUID().toString(); }
    private void mapper(String tLegacy, Object idLegacy, String tCible, String idCible) {
        dst.update("INSERT INTO migration_map (run_id, table_legacy, id_legacy, table_cible, id_cible) VALUES (?,?,?,?,?)", runId, tLegacy, String.valueOf(idLegacy), tCible, idCible);
    }
    private String trad(String json, String lang) {
        if (json == null || json.isBlank()) return "";
        try { JsonNode n = om.readTree(json); if (n.isTextual()) return n.asText(); JsonNode v = n.get(lang); return v == null || v.isNull() ? "" : v.asText(); }
        catch (Exception e) { return json; }
    }
    private String idParLegacy(String table, long idLegacy) {
        List<String> l = dst.queryForList("SELECT id FROM " + table + " WHERE id_legacy = ?", String.class, idLegacy);
        return l.isEmpty() ? null : l.get(0);
    }
    private static String tronque(String s, int max) { return s == null ? null : (s.length() > max ? s.substring(0, max) : s); }
    private static long lng(Object o) { return ((Number) o).longValue(); }

    private Date sqlDate(Object o) {
        if (o == null) return null;
        if (o instanceof Date d) return d;
        if (o instanceof java.util.Date d) return new Date(d.getTime());
        if (o instanceof java.time.LocalDateTime d) return Date.valueOf(d.toLocalDate());
        if (o instanceof java.time.LocalDate d) return Date.valueOf(d);
        return Date.valueOf(o.toString().substring(0, 10));
    }

    // ------------------------------------------------------------------ coordinations / délégations

    private void coordinationsEtDelegations() {
        String t = "coordinations";
        for (Map<String, Object> r : src.queryForList("SELECT * FROM coordinations ORDER BY id")) {
            inc(t, "lus"); long id = lng(r.get("id"));
            String nomFr = trad((String) r.get("name_trans"), "fr"), nomAr = trad((String) r.get("name_trans"), "ar");
            boolean siege = "siege_centrale".equals(r.get("code")) || nomFr.toLowerCase().startsWith("si");
            String cible = idParLegacy("coordination_region", id);
            if (cible != null) { inc(t, "deja_presents"); }
            else {
                String code = siege ? "SIEGE" : String.format("R%02d", id);
                List<String> ex = dst.queryForList("SELECT id FROM coordination_region WHERE code_region = ?", String.class, code);
                if (!ex.isEmpty()) {
                    cible = ex.get(0);
                    dst.update("UPDATE coordination_region SET id_legacy = ? WHERE id = ?", id, cible);
                } else if (siege) {
                    cible = newId();
                    dst.update("INSERT INTO coordination_region (id, code_region, nom_region_ar, nom_region_fr, nom_coord_ar, nom_coord_fr, est_siege, id_legacy) VALUES (?,?,?,?,?,?,1,?)", cible, code, nomAr, nomFr, nomAr, nomFr, id);
                } else {
                    cible = newId();
                    dst.update("INSERT INTO coordination_region (id, code_region, nom_region_ar, nom_region_fr, nom_coord_ar, nom_coord_fr, id_legacy) VALUES (?,?,?,?,?,?,?)", cible, code, nomAr, nomFr, nomAr, nomFr, id);
                    anomalie("REGION_NON_PREVUE", t, id, "Région absente du référentiel : créée, à valider par la RH");
                }
                inc(t, "crees"); mapper(t, id, "coordination_region", cible);
            }
            if (siege) dst.update("UPDATE coordination_region SET est_siege = 1 WHERE id = ?", cible);
            coordination.put(id, cible);
            signataire("coordination", cible, null, r, t, id);
        }
        t = "delegations";
        for (Map<String, Object> r : src.queryForList("SELECT * FROM delegations ORDER BY id")) {
            inc(t, "lus"); long id = lng(r.get("id"));
            String symbole = (String) r.get("symbols");
            String nomFr = trad((String) r.get("name_trans"), "fr"), nomAr = trad((String) r.get("name_trans"), "ar");
            long coordLegacy = lng(r.get("coordination_id"));
            String coordCible = coordination.get(coordLegacy);
            boolean siege = Boolean.TRUE.equals(dst.queryForObject("SELECT est_siege = 1 FROM coordination_region WHERE id = ?", Boolean.class, coordCible));
            String cible = idParLegacy("delegation_province", id);
            if (cible == null) {
                List<String> ex = dst.queryForList("SELECT id FROM delegation_province WHERE symbole = ?", String.class, symbole);
                if (!ex.isEmpty()) { cible = ex.get(0); dst.update("UPDATE delegation_province SET id_legacy = ? WHERE id = ?", id, cible); inc(t, "deja_presents"); }
                else {
                    cible = newId();
                    dst.update("INSERT INTO delegation_province (id, coordination_id, code_province, nom_province_ar, nom_province_fr, nom_deleg_ar, nom_deleg_fr, symbole, est_siege, id_legacy) VALUES (?,?,?,?,?,?,?,?,?,?)",
                            cible, coordCible, tronque(symbole, 10), nomAr, nomFr, nomAr, nomFr, symbole, siege ? 1 : 0, id);
                    inc(t, "crees"); mapper(t, id, "delegation_province", cible);
                }
            } else inc(t, "deja_presents");
            if (siege) { dst.update("UPDATE delegation_province SET est_siege = 1 WHERE id = ?", cible); delegationSiege = cible; }
            delegation.put(id, cible); delegationSymbole.put(id, symbole);
            signataire("delegation", null, cible, r, t, id);
            compteur(cible, "etat", (String) r.get("num_etat_counter"), t, id);
            compteur(cible, "ordm", (String) r.get("num_ordm_counter"), t, id);
        }
        if (delegationSiege == null) anomalie("SIEGE_ABSENT", "delegations", "-", "Aucune délégation marquée siège : les unités administratives ne peuvent pas être rattachées");
    }

    private void signataire(String portee, String coordCible, String delegCible, Map<String, Object> r, String table, long id) {
        Object d = r.get("autorisation_signature_date");
        if (d == null) { anomalie("SIGNATAIRE_ABSENT", table, id, "Aucune autorisation de signature"); return; }
        Date debut = sqlDate(d);
        String champ = "coordination".equals(portee) ? "coordination_id" : "delegation_id";
        String cible = "coordination".equals(portee) ? coordCible : delegCible;
        Integer n = dst.queryForObject("SELECT COUNT(*) FROM signataire_autorisation WHERE portee = ? AND " + champ + " = ? AND date_debut = ?", Integer.class, portee, cible, debut);
        if (n != null && n > 0) return;
        String qualite = (String) r.get("autorisation_signature_personne");
        Object num = r.get("autorisation_signature_numero");
        // Suivi RH ne conserve que la qualité du signataire, jamais son nom : il reste à renseigner par la RH.
        dst.update("INSERT INTO signataire_autorisation (id, portee, " + champ + ", nom_fr, nom_ar, qualite_fr, qualite_ar, numero_autorisation, date_autorisation, date_debut) VALUES (?,?,?,?,?,?,?,?,?,?)",
                newId(), portee, cible, "", "", trad(qualite, "fr"), trad(qualite, "ar"), num == null ? null : num.toString(), debut, debut);
        inc("signataire_autorisation", "crees");
    }

    private void compteur(String delegCible, String type, String json, String table, long id) {
        if (json == null || json.isBlank()) return;
        try {
            JsonNode n = om.readTree(json);
            if (!n.isObject()) return;
            n.fields().forEachRemaining(e -> {
                int annee = Integer.parseInt(e.getKey()); int valeur = e.getValue().asInt();
                dst.update("INSERT INTO delegation_compteur (delegation_id, annee, type_numero, dernier_numero) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE dernier_numero = GREATEST(dernier_numero, VALUES(dernier_numero))", delegCible, annee, type, valeur);
            });
        } catch (Exception e) { anomalie("COMPTEUR_ILLISIBLE", table, id, "Compteur " + type + " illisible"); }
    }

    // ------------------------------------------------------------------ unités administratives

    private void unitesAdministratives() {
        String t = "structures";
        for (Map<String, Object> r : src.queryForList("SELECT * FROM structures ORDER BY id")) {
            inc(t, "lus"); long id = lng(r.get("id"));
            String cible = idParLegacy("structure", id);
            if (cible != null) { inc(t, "deja_presents"); structureUnite.put(id, cible); continue; }
            if (delegationSiege == null) { inc(t, "ignores"); continue; }
            String code = (String) r.get("code");
            if (code.length() > 30) { anomalie("CODE_TRONQUE", t, id, "Code de plus de 30 caractères"); code = code.substring(0, 30); }
            List<String> ex = dst.queryForList("SELECT id FROM structure WHERE code = ?", String.class, code);
            if (!ex.isEmpty()) { anomalie("CODE_EN_DOUBLE", t, id, "Code déjà utilisé dans la cible : unité ignorée"); inc(t, "ignores"); continue; }
            cible = newId();
            dst.update("INSERT INTO structure (id, delegation_id, type, code, nom_ar, nom_fr, id_legacy) VALUES (?,?,'unite_administrative',?,?,?,?)",
                    cible, delegationSiege, code, trad((String) r.get("name_trans"), "ar"), trad((String) r.get("name_trans"), "fr"), id);
            inc(t, "crees"); mapper(t, id, "structure", cible); structureUnite.put(id, cible);
        }
    }

    private String structureAcompleter(long delegLegacy) {
        String deleg = delegation.get(delegLegacy);
        return placeholder.computeIfAbsent(deleg, k -> {
            String symbole = delegationSymbole.get(delegLegacy);
            String code = tronque("AC-" + symbole, 30);
            List<String> ex = dst.queryForList("SELECT id FROM structure WHERE code = ?", String.class, code);
            if (!ex.isEmpty()) return ex.get(0);
            String id = newId();
            dst.update("INSERT INTO structure (id, delegation_id, type, code, nom_ar, nom_fr, a_completer) VALUES (?,?,'unite_administrative',?,?,?,1)", id, deleg, code, "(إلى تحديد) " + symbole, "(À compléter) " + symbole);
            inc("structure_a_completer", "crees");
            return id;
        });
    }

    // ------------------------------------------------------------------ grades et référentiels

    private void grades() {
        String t = "grades";
        for (Map<String, Object> r : src.queryForList("SELECT * FROM grades ORDER BY id")) {
            inc(t, "lus"); long id = lng(r.get("id"));
            String cible = idParLegacy("grade", id);
            if (cible != null) { inc(t, "deja_presents"); grade.put(id, cible); continue; }
            if (id > Integer.MAX_VALUE) { anomalie("GRADE_CODE_TROP_GRAND", t, id, "Identifiant hors limites du code"); inc(t, "ignores"); continue; }
            cible = newId();
            dst.update("INSERT INTO grade (id, code, libelle_fr, libelle_ar, echelle, id_legacy) VALUES (?,?,?,?,?,?)", cible, (int) id,
                    trad((String) r.get("name_trans"), "fr"), trad((String) r.get("name_trans"), "ar"), r.get("echelle"), id);
            inc(t, "crees"); mapper(t, id, "grade", cible); grade.put(id, cible);
        }
    }

    private void referentielsSimples() {
        String t = "responsabilites";
        for (Map<String, Object> r : src.queryForList("SELECT * FROM responsabilites ORDER BY id")) {
            inc(t, "lus"); long id = lng(r.get("id"));
            String cible = idParLegacy("responsabilite", id);
            if (cible == null) {
                List<String> ex = dst.queryForList("SELECT id FROM responsabilite WHERE code = ?", String.class, r.get("code"));
                if (!ex.isEmpty()) { cible = ex.get(0); dst.update("UPDATE responsabilite SET id_legacy = ? WHERE id = ?", id, cible); inc(t, "deja_presents"); }
                else {
                    cible = newId();
                    dst.update("INSERT INTO responsabilite (id, code, libelle_fr, libelle_ar, id_legacy) VALUES (?,?,?,?,?)", cible, r.get("code"), trad((String) r.get("name_trans"), "fr"), trad((String) r.get("name_trans"), "ar"), id);
                    inc(t, "crees"); mapper(t, id, "responsabilite", cible);
                }
            } else inc(t, "deja_presents");
            responsabilite.put(id, cible);
        }
        t = "moyen_transports";
        for (Map<String, Object> r : src.queryForList("SELECT * FROM moyen_transports ORDER BY id")) {
            inc(t, "lus");
            Integer n = dst.queryForObject("SELECT COUNT(*) FROM moyen_transport WHERE code = ?", Integer.class, r.get("code"));
            if (n != null && n > 0) { inc(t, "deja_presents"); continue; }
            String cible = newId();
            dst.update("INSERT INTO moyen_transport (id, code, libelle_fr, libelle_ar) VALUES (?,?,?,?)", cible, r.get("code"), trad((String) r.get("name_trans"), "fr"), trad((String) r.get("name_trans"), "ar"));
            inc(t, "crees"); mapper(t, r.get("id"), "moyen_transport", cible);
        }
        t = "puissance_fiscales";
        for (Map<String, Object> r : src.queryForList("SELECT * FROM puissance_fiscales ORDER BY id")) {
            inc(t, "lus");
            List<Map<String, Object>> ex = dst.queryForList("SELECT id, taux_km FROM puissance_fiscale WHERE code = ?", r.get("code"));
            BigDecimal taux = new BigDecimal(r.get("taux").toString()).setScale(2, java.math.RoundingMode.HALF_UP);
            if (!ex.isEmpty()) {
                inc(t, "deja_presents");
                if (((BigDecimal) ex.get(0).get("taux_km")).compareTo(taux) != 0) anomalie("TAUX_KM_DIFFERENT", t, r.get("id"), "Taux kilométrique différent de la cible : " + taux);
                continue;
            }
            // la date d'effet réelle n'est pas connue : date de création de la ligne dans Suivi RH, statut non validé
            String cible = newId();
            dst.update("INSERT INTO puissance_fiscale (id, code, libelle_fr, libelle_ar, taux_km, date_effet, reference_texte, statut_validation) VALUES (?,?,?,?,?,?,'Suivi RH - à valider','non_valide')",
                    cible, r.get("code"), trad((String) r.get("name_trans"), "fr"), trad((String) r.get("name_trans"), "ar"), taux, sqlDate(r.get("created_at")));
            inc(t, "crees"); mapper(t, r.get("id"), "puissance_fiscale", cible);
        }
    }

    private void programmes() {
        String t = "programmes";
        for (Map<String, Object> r : src.queryForList("SELECT * FROM programmes ORDER BY id")) {
            inc(t, "lus"); long id = lng(r.get("id"));
            String cible = idParLegacy("programme_mission", id);
            if (cible == null) {
                List<String> ex = dst.queryForList("SELECT id FROM programme_mission WHERE code = ?", String.class, r.get("code"));
                if (!ex.isEmpty()) { cible = ex.get(0); dst.update("UPDATE programme_mission SET id_legacy = ? WHERE id = ?", id, cible); inc(t, "deja_presents"); }
                else {
                    cible = newId();
                    int fois = r.get("nombre_fois_ans") == null ? 0 : ((Number) r.get("nombre_fois_ans")).intValue();
                    dst.update("INSERT INTO programme_mission (id, code, nom_fr, nom_ar, nombre_cycles_an, id_legacy) VALUES (?,?,?,?,?,?)", cible, r.get("code"), trad((String) r.get("name_trans"), "fr"), trad((String) r.get("name_trans"), "ar"), fois, id);
                    inc(t, "crees"); mapper(t, id, "programme_mission", cible);
                    try {
                        JsonNode p = om.readTree((String) r.get("montant_max_fois"));
                        Date effet = sqlDate(r.get("created_at"));
                        final String pid = cible;
                        p.fields().forEachRemaining(e -> {
                            dst.update("INSERT INTO programme_mission_plafond (id, programme_id, echelle, plafond, date_effet, reference_texte, statut_validation) VALUES (?,?,?,?,?,'Suivi RH montant_max_fois - à valider','non_valide')", newId(), pid, Integer.parseInt(e.getKey()), new BigDecimal(e.getValue().asText()), effet);
                            inc("programme_mission_plafond", "crees");
                        });
                    } catch (Exception e) { anomalie("PLAFONDS_ILLISIBLES", t, id, "montant_max_fois illisible"); }
                }
            } else inc(t, "deja_presents");
            programme.put(id, cible);
        }
    }

    private void joursFeries() {
        String t = "holidays";
        for (Map<String, Object> r : src.queryForList("SELECT * FROM holidays WHERE deleted_at IS NULL ORDER BY jour, id")) {
            inc(t, "lus"); Date jour = sqlDate(r.get("jour"));
            Integer n = dst.queryForObject("SELECT COUNT(*) FROM jour_ferie WHERE date_ferie = ?", Integer.class, jour);
            if (n != null && n > 0) { inc(t, "deja_presents"); continue; }
            String nom = (String) r.get("nom");
            String l = nom.toLowerCase();
            boolean religieux = l.contains("aïd") || l.contains("aid ") || l.contains("mawlid") || l.contains("musulman") || l.contains("hégire");
            // l'arabe n'existe pas dans la source : repris du français en attendant la saisie de la RH
            String cible = newId();
            dst.update("INSERT INTO jour_ferie (id, date_ferie, libelle_fr, libelle_ar, type_ferie, a_confirmer, id_legacy) VALUES (?,?,?,?,?,?,?)", cible, jour, nom, nom, religieux ? "religieux" : "civil", religieux ? 1 : 0, r.get("id"));
            inc(t, "crees"); mapper(t, r.get("id"), "jour_ferie", cible);
        }
        anomalie("LIBELLE_AR_A_COMPLETER", t, "*", "Libellés arabes des jours fériés absents de la source");
    }

    // ------------------------------------------------------------------ personnes

    private void personnes() {
        String t = "personnes";
        Set<String> empreintes = new HashSet<>();
        for (Map<String, Object> r : src.queryForList("SELECT * FROM personnes ORDER BY id")) {
            inc(t, "lus"); long id = lng(r.get("id"));
            if (idParLegacy("personnel", id) != null) { inc(t, "deja_presents"); continue; }
            String matricule = (String) r.get("matricule");
            if (matricule == null || matricule.isBlank() || matricule.length() > 10) { anomalie("MATRICULE_INVALIDE", t, id, "Matricule vide ou trop long : personne non reprise"); inc(t, "ignores"); continue; }
            Integer dup = dst.queryForObject("SELECT COUNT(*) FROM personnel WHERE matricule = ?", Integer.class, matricule);
            if (dup != null && dup > 0) { anomalie("MATRICULE_EN_DOUBLE", t, id, "Matricule " + matricule + " déjà présent : jamais de fusion automatique"); inc(t, "ignores"); continue; }
            if (r.get("affectation_id") == null || !delegation.containsKey(lng(r.get("affectation_id")))) { anomalie("AFFECTATION_INCONNUE", t, id, "Délégation d'affectation absente"); inc(t, "ignores"); continue; }
            long aff = lng(r.get("affectation_id"));

            String nomFr = Optional.ofNullable((String) r.get("nom_prenom_fr")).orElse(""), nomAr = Optional.ofNullable((String) r.get("nom_prenom_ar")).orElse("");
            LegacyNames.Split sf = LegacyNames.fr(nomFr), sa = LegacyNames.ar(nomAr);
            if (sf.ambigu() || sa.ambigu()) controleNoms.add(new String[]{matricule, nomFr, sf.nom(), sf.prenom(), nomAr, sa.nom(), sa.prenom()});

            Long structLegacy = r.get("structure_id") == null ? null : lng(r.get("structure_id"));
            String unite = structLegacy == null ? null : structureUnite.get(structLegacy);
            String structure = unite != null ? unite : structureAcompleter(aff);
            boolean aCompleter = unite == null;
            boolean sortie = r.get("situation_administrative_id") != null && lng(r.get("situation_administrative_id")) == 2;
            boolean supprime = r.get("deleted_at") != null;
            Long gl = r.get("grade_id") == null ? null : lng(r.get("grade_id"));
            String gradeCible = gl == null ? null : grade.get(gl);
            if (gl != null && gradeCible == null) anomalie("GRADE_INCONNU", t, id, "Grade " + gl + " non repris");
            Integer echelle = gradeCible == null ? null : dst.queryForObject("SELECT echelle FROM grade WHERE id = ?", Integer.class, gradeCible);

            byte[] chiffre = null; String empreinte = null;
            String cn = (String) r.get("cnie");
            if (cn != null && !cn.isBlank()) {
                empreinte = cnie.empreinte(cn);
                if (!empreintes.add(empreinte) || dst.queryForObject("SELECT COUNT(*) FROM personnel WHERE cnie_empreinte = ?", Integer.class, empreinte) > 0) {
                    anomalie("CNIE_EN_DOUBLE", t, id, "Même CNIE qu'une autre personne (matricule " + matricule + ")"); empreinte = null; chiffre = null;
                } else chiffre = cnie.chiffrer(cn);
            } else anomalie("CNIE_ABSENTE", t, id, "CNIE non renseignée (matricule " + matricule + ")");

            String cible = newId();
            Long resp = r.get("responsabilite_id") == null ? null : lng(r.get("responsabilite_id"));
            dst.update("INSERT INTO personnel (id, grade_id, structure_id, matricule, nom_ar, prenom_ar, nom_fr, prenom_fr, echelle, est_actif, id_legacy, cnie_chiffre, cnie_empreinte, responsabilite_id, situation_service, stagiaire, administration_origine, mission_texte, taches_assignees, unite_administrative_id, a_completer, nom_prenom_fr_legacy, nom_prenom_ar_legacy) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    cible, gradeCible, structure, matricule, sa.nom(), sa.prenom(), sf.nom(), sf.prenom(), echelle, (sortie || supprime) ? 0 : 1, id, chiffre, empreinte,
                    resp == null ? null : responsabilite.get(resp), sortie ? "sortie_de_service" : "en_activite", Boolean.TRUE.equals(r.get("stagiaire")) || (r.get("stagiaire") instanceof Number n && n.intValue() == 1) ? 1 : 0,
                    r.get("administration_origine_id") == null ? null : administration(lng(r.get("administration_origine_id"))),
                    r.get("mission"), r.get("assigned_tasks"), unite, aCompleter ? 1 : 0, nomFr, nomAr);
            inc(t, "crees"); mapper(t, id, "personnel", cible);
            if (r.get("programme_id") != null && programme.containsKey(lng(r.get("programme_id")))) {
                dst.update("INSERT INTO personnel_programme (id, personnel_id, programme_id, date_debut) VALUES (?,?,?,?)", newId(), cible, programme.get(lng(r.get("programme_id"))), sqlDate(r.get("created_at")));
                inc("personnel_programme", "crees");
            }
            if (unite == null) inc("personnel_a_completer", "crees");
        }
    }

    private final Map<Long, String> administrations = new HashMap<>();
    private String administration(long id) {
        return administrations.computeIfAbsent(id, k -> {
            List<String> l = src.queryForList("SELECT name_trans FROM administrations WHERE id = ?", String.class, k);
            return l.isEmpty() ? null : tronque(trad(l.get(0), "fr"), 150);
        });
    }

    // ------------------------------------------------------------------ comptes

    private static final Map<String, String[]> ROLES = Map.ofEntries(
            Map.entry("super_administrateur", new String[]{"admin_technique", "admin"}),
            Map.entry("superviseur", new String[]{"supervision", "rh"}),
            Map.entry("delegation", new String[]{"responsable_standard", "responsable"}),
            Map.entry("coordination", new String[]{"responsable_standard", "responsable"}),
            Map.entry("structure", new String[]{"responsable_standard", "responsable"}),
            Map.entry("cellule_des_conges", new String[]{"rh_generaliste", "rh"}),
            Map.entry("cellule_des_rh", new String[]{"rh_generaliste", "rh"}),
            Map.entry("cellule_des_deplacements", new String[]{"cellule_deplacements", "rh"}),
            Map.entry("cellule_de_paie", new String[]{"cellule_paie", "rh"}));

    private void comptes() {
        String t = "users";
        Map<Long, String> codeRole = new HashMap<>();
        for (Map<String, Object> r : src.queryForList("SELECT id, code FROM roles")) codeRole.put(lng(r.get("id")), (String) r.get("code"));
        for (Map<String, Object> r : src.queryForList("SELECT * FROM users ORDER BY id")) {
            inc(t, "lus"); long id = lng(r.get("id"));
            if (idParLegacy("utilisateur", id) != null) { inc(t, "deja_presents"); continue; }
            String email = (String) r.get("email");
            String login = email == null || email.isBlank() ? "legacy-" + id : email.trim().toLowerCase();
            Integer dup = dst.queryForObject("SELECT COUNT(*) FROM utilisateur WHERE login = ?", Integer.class, login);
            if (dup != null && dup > 0) { anomalie("LOGIN_EN_DOUBLE", t, id, "Identifiant déjà présent : compte ignoré"); inc(t, "ignores"); continue; }
            String[] m = ROLES.get(codeRole.get(lng(r.get("role_id"))));
            if (m == null) { anomalie("ROLE_INCONNU", t, id, "Rôle d'origine non reconnu : compte ignoré"); inc(t, "ignores"); continue; }
            String roleId = dst.queryForObject("SELECT id FROM role WHERE code = ?", String.class, m[0]);
            String cible = newId();
            // compte d'entité : migré DÉSACTIVÉ, mot de passe à changer, jamais supprimé (audit)
            dst.update("INSERT INTO utilisateur (id, login, mot_de_passe, role, est_actif, doit_changer_mdp, role_id, est_compte_legacy, id_legacy) VALUES (?,?,?,?,0,1,?,1,?)",
                    cible, login, r.get("password"), m[1], roleId, id);
            inc(t, "crees"); mapper(t, id, "utilisateur", cible);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("login", login); p.put("role_origine", codeRole.get(lng(r.get("role_id")))); p.put("role_cible", m[0]);
            p.put("coordination", r.get("coordination_id"));
            p.put("delegation", r.get("delegation_id")); p.put("unite_administrative", r.get("structure_id"));
            comptesPerimetres.add(p);
        }
    }

    // ------------------------------------------------------------------ contrôle des barèmes

    private void verifierBaremes() {
        for (Map<String, Object> g : src.queryForList("SELECT echelle, MIN(taux_1) t1, MAX(taux_1) t1m, MIN(taux_2) t2, MAX(taux_2) t2m FROM grades WHERE echelle IS NOT NULL GROUP BY echelle")) {
            int e = ((Number) g.get("echelle")).intValue();
            if (new BigDecimal(g.get("t1").toString()).compareTo(new BigDecimal(g.get("t1m").toString())) != 0 || new BigDecimal(g.get("t2").toString()).compareTo(new BigDecimal(g.get("t2m").toString())) != 0)
                anomalie("TAUX_INCOHERENTS_ECHELLE", "grades", e, "Plusieurs taux différents pour l'échelle " + e);
            List<Map<String, Object>> b = dst.queryForList("SELECT taux_repas_1, taux_repas_2 FROM bareme_indemnite WHERE echelle = ? ORDER BY date_effet DESC LIMIT 1", e);
            if (b.isEmpty()) anomalie("BAREME_ABSENT", "grades", e, "Aucun barème dans la cible pour l'échelle " + e);
            else if (((BigDecimal) b.get(0).get("taux_repas_1")).compareTo(new BigDecimal(g.get("t1").toString())) != 0 || ((BigDecimal) b.get(0).get("taux_repas_2")).compareTo(new BigDecimal(g.get("t2").toString())) != 0)
                anomalie("BAREME_DIFFERENT", "grades", e, "Taux de la source différents du barème cible, échelle " + e);
        }
    }

    // ------------------------------------------------------------------ M4 : déplacements, congés, messages, compteurs, réconciliation

    private final Map<Long, String> personnelParLegacy = new HashMap<>();

    private String personnelDe(long legacy) {
        if (personnelParLegacy.isEmpty())
            dst.query("SELECT id_legacy, id FROM personnel WHERE id_legacy IS NOT NULL", rs -> { personnelParLegacy.put(rs.getLong(1), rs.getString(2)); });
        return personnelParLegacy.get(legacy);
    }

    private String codeParLegacy(String table, String colonneCode, Object id) {
        if (id == null) return null;
        List<String> l = src.queryForList("SELECT " + colonneCode + " FROM " + table + " WHERE id = ?", String.class, id);
        return l.isEmpty() ? null : l.get(0);
    }

    private static final java.util.regex.Pattern NUM_ETAT = java.util.regex.Pattern.compile("([A-Za-z]+)\\s*-\\s*(\\d+)\\s*/\\s*(\\d{4})");

    private void deplacements() {
        String t = "deplacement_propres";
        // numéros d'état en double dans la source (lignes supprimées comprises) : signalés, jamais corrigés
        Set<String> etatEnDouble = new HashSet<>(src.queryForList("SELECT numero_etat FROM deplacement_propres WHERE numero_etat IS NOT NULL AND numero_etat <> '' GROUP BY numero_etat HAVING COUNT(*) > 1", String.class));
        Map<Long, String> statut = new HashMap<>();
        for (Map<String, Object> r : src.queryForList("SELECT id, code FROM deplacement_programme_statuses")) statut.put(lng(r.get("id")), (String) r.get("code"));
        RepasEngine.Seuils seuils = RepasEngine.Seuils.parDefaut();
        for (Map<String, Object> r : src.queryForList("SELECT * FROM deplacement_propres ORDER BY id")) {
            inc(t, "lus"); long id = lng(r.get("id"));
            if (dst.queryForObject("SELECT COUNT(*) FROM ordre_mission WHERE source_legacy = 'propre' AND id_legacy = ?", Integer.class, id) > 0) { inc(t, "deja_presents"); continue; }
            String pers = r.get("personne_id") == null ? null : personnelDe(lng(r.get("personne_id")));
            if (pers == null) { anomalie("PERSONNE_INCONNUE", t, id, "Personne absente de la cible : mission non reprise"); inc(t, "ignores"); continue; }
            String code = statut.get(r.get("status_id") == null ? -1L : lng(r.get("status_id")));
            String numOrdm = r.get("numero_ordm") == null ? null : r.get("numero_ordm").toString().trim();
            String numEtat = r.get("numero_etat") == null ? null : r.get("numero_etat").toString().trim();
            boolean aOrdre = numOrdm != null && !numOrdm.isEmpty();
            String nouveau = switch (String.valueOf(code)) {
                case "finalized" -> "finalisee"; case "paid" -> "payee"; case "cancelled" -> "annulee";
                case "generated", "created", "modified" -> aOrdre ? "approuvee" : "soumise";
                default -> "soumise";
            };
            if (code == null) anomalie("STATUT_INCONNU", t, id, "Statut d'origine inconnu : migrée « soumise »");
            Object dep = r.get("date_depart"), ret = r.get("date_retour");
            java.time.LocalDateTime d = dep == null ? null : toLdt(dep), rr = ret == null ? null : toLdt(ret);
            if (d == null) { anomalie("DEPART_ABSENT", t, id, "Date de départ absente : mission non reprise"); inc(t, "ignores"); continue; }
            boolean remboursable = bool(r.get("deplacement_remboursable"));
            int r1 = r.get("nombre_repas_taux_1") == null ? 0 : ((Number) r.get("nombre_repas_taux_1")).intValue(), r2 = r.get("nombre_repas_taux_2") == null ? 0 : ((Number) r.get("nombre_repas_taux_2")).intValue();
            BigDecimal t1 = bd(r.get("indemnite_taux_1")), t2 = bd(r.get("indemnite_taux_2"));
            BigDecimal indemnites = t1.multiply(BigDecimal.valueOf(r1)).add(t2.multiply(BigDecimal.valueOf(r2))).setScale(2, java.math.RoundingMode.HALF_UP);
            String moyenCode = codeParLegacy("moyen_transports", "code", r.get("moyen_transport_id"));
            BigDecimal frais = bd(r.get("frais_transport")).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal fraisRetenus = "transports_en_commun".equals(moyenCode) && frais.signum() > 0 ? frais : BigDecimal.ZERO;
            BigDecimal dist = r.get("distance_trajet") == null ? null : bd(r.get("distance_trajet"));
            Integer trajets = r.get("nombre_trajet") == null ? null : ((Number) r.get("nombre_trajet")).intValue();
            BigDecimal tauxKm = r.get("taux_km") == null ? null : bd(r.get("taux_km"));
            BigDecimal km = dist != null && trajets != null && tauxKm != null ? dist.multiply(BigDecimal.valueOf(trajets)).multiply(tauxKm).setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal total = indemnites.add(fraisRetenus).add(km);

            String departD = r.get("depart_delegation_id") == null ? null : delegation.get(lng(r.get("depart_delegation_id")));
            String arriveeD = r.get("arrival_delegation_id") == null ? null : delegation.get(lng(r.get("arrival_delegation_id")));
            boolean memeLieu = r.get("depart_delegation_id") != null && Objects.equals(r.get("depart_delegation_id"), r.get("arrival_delegation_id"));
            boolean retourAbsent = rr == null;
            if (retourAbsent) anomalie("RETOUR_NON_RENSEIGNE", t, id, remboursable ? "Remboursable sans retour : repas à corriger" : "Non remboursable sans retour");
            if (memeLieu) anomalie("DEPART_EGAL_ARRIVEE", t, id, "Départ et arrivée identiques");
            // écart avec le moteur actuel : attendu pour les missions créées du 03 au 12/06/2024, inattendu ailleurs
            boolean ecart = false;
            if (remboursable && rr != null && rr.isAfter(d)) {
                RepasEngine.Resultat calc = RepasEngine.calculer(d, rr, seuils);
                if (calc.repasTaux1() != r1 || calc.repasTaux2() != r2) {
                    java.time.LocalDate cree = r.get("created_at") == null ? null : toLdt(r.get("created_at")).toLocalDate();
                    boolean premiereSemaine = cree != null && !cree.isBefore(java.time.LocalDate.of(2024, 6, 3)) && !cree.isAfter(java.time.LocalDate.of(2024, 6, 12));
                    ecart = true;
                    if (!premiereSemaine) anomalie("ECART_MOTEUR_INATTENDU", t, id, "Repas stockés " + r1 + "/" + r2 + " différents du moteur " + calc.repasTaux1() + "/" + calc.repasTaux2());
                }
            }
            String anomalieNumero = !aOrdre ? "numero_absent" : (numEtat != null && etatEnDouble.contains(numEtat) ? "etat_en_double" : "aucune");
            if (!aOrdre) anomalie("NUMERO_ABSENT", t, id, "Mission sans numéro d'ordre d'origine");
            if ("etat_en_double".equals(anomalieNumero)) anomalie("ETAT_EN_DOUBLE", t, id, "Numéro d'état d'origine en double : " + numEtat);

            String cible = newId();
            Object cree = r.get("created_at"), maj = r.get("updated_at");
            dst.update("""
                    INSERT INTO ordre_mission (id, personnel_id, depart_delegation_id, arrival_delegation_id, moyen_transport_id, objet, date_depart, date_retour, annee, statut,
                        numero_ordm_legacy, numero_etat_legacy, anomalie_numero, date_ordm, nombre_repas_taux_1, indemnite_taux_1, nombre_repas_taux_2, indemnite_taux_2, montant_indemnites,
                        deplacement_remboursable, frais_transport, vehicule_immatriculation, vehicule_nom, puissance_fiscale_id, distance_km, nombre_trajets, taux_km, montant_km, montant_total,
                        origine, source_legacy, id_legacy, date_validation_approximative, modifie, retour_non_renseigne, anomalie_depart_arrivee, ecart_moteur_historique, supprime_le, cree_le, modifie_le)
                    VALUES (?,?,?,?,(SELECT id FROM moyen_transport WHERE code = ?),?,?,?,?,?, ?,?,?,?, ?,?,?,?,?, ?,?,?,?,(SELECT id FROM puissance_fiscale WHERE code = ?),?,?,?,?,?, 'saisie','propre',?,?,?,?,?,?,?,?,?)""",
                    cible, pers, departD, arriveeD, moyenCode, r.get("objet") == null ? "" : r.get("objet"), java.sql.Timestamp.valueOf(d), rr == null ? null : java.sql.Timestamp.valueOf(rr),
                    r.get("annee_id") == null ? d.getYear() : annee(r.get("annee_id")), nouveau,
                    aOrdre ? numOrdm : null, numEtat == null || numEtat.isEmpty() ? null : numEtat, anomalieNumero, r.get("date_ordm") == null ? null : sqlDate(r.get("date_ordm")),
                    r1, t1, r2, t2, indemnites, remboursable ? 1 : 0, frais, r.get("matricule"), r.get("nom_voiture_fr"),
                    codeParLegacy("puissance_fiscales", "code", r.get("puissance_fiscale_id")), dist, trajets, tauxKm, km, total,
                    id, "finalisee".equals(nouveau) || "payee".equals(nouveau) || "approuvee".equals(nouveau) ? 1 : 0, "modified".equals(code) ? 1 : 0, retourAbsent ? 1 : 0, memeLieu ? 1 : 0, ecart ? 1 : 0,
                    r.get("deleted_at") == null ? null : java.sql.Timestamp.valueOf(toLdt(r.get("deleted_at"))),
                    cree == null ? java.sql.Timestamp.valueOf(d) : java.sql.Timestamp.valueOf(toLdt(cree)), maj == null ? null : java.sql.Timestamp.valueOf(toLdt(maj)));
            // une seule ligne d'historique « migré » : ni auteur ni date de validation ne sont inventés
            dst.update("INSERT INTO ordre_mission_historique (id, ordre_mission_id, acteur_id, ancien_statut, nouveau_statut, commentaire, migre, cree_le) VALUES (UUID(),?,NULL,NULL,?,?,1,?)",
                    cible, nouveau, "Repris de Suivi RH (statut d'origine : " + code + ")", java.sql.Timestamp.valueOf(maj == null ? d : toLdt(maj)));
            inc(t, "crees"); mapper(t, id, "ordre_mission", cible);
        }
        Integer progs = src.queryForObject("SELECT COUNT(*) FROM deplacement_programmes", Integer.class);
        if (progs != null && progs > 0) anomalie("DEPLACEMENTS_PROGRAMME_A_TRAITER", "deplacement_programmes", "*", progs + " missions de programme non reprises : planification à confirmer avant reprise de l'historique");
        else compteurs.computeIfAbsent("deplacement_programmes", k -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("lus", 0); m.put("crees", 0); m.put("deja_presents", 0); m.put("ignores", 0); return m; });
    }

    private static java.time.LocalDateTime toLdt(Object o) {
        if (o instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (o instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
        if (o instanceof java.time.LocalDateTime l) return l;
        if (o instanceof java.time.LocalDate d) return d.atStartOfDay();
        if (o instanceof java.util.Date d) return new java.sql.Timestamp(d.getTime()).toLocalDateTime();
        return java.time.LocalDateTime.parse(o.toString().replace(' ', 'T'));
    }

    /** Les colonnes de type YEAR reviennent parfois en date selon le pilote. */
    private static int annee(Object o) { return o instanceof java.sql.Date d ? d.toLocalDate().getYear() : ((Number) o).intValue(); }

    private static BigDecimal bd(Object o) { return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString()); }
    private static boolean bool(Object o) { return o instanceof Boolean b ? b : o instanceof Number n && n.intValue() != 0; }

    private static final Map<String, String> TYPE_CONGE = Map.of(
            "conge_administratif_annuel", "CONGE_ANNUEL", "conge_administratif_exceptionnel", "CONGE_EXCEPTION", "permission_absence", "CONGE_PERMISSION_ABSENCE",
            "conge_maladie_courte_duree", "CONGE_MALADIE", "conge_maladie_moyenne_duree", "CONGE_MALADIE", "conge_maladie_longue_duree", "CONGE_MALADIE",
            "conge_maladie_ou_blessures_pendant_exercice_des_fonctions", "CONGE_ACCIDENT_TRAVAIL", "conge_de_maternite", "CONGE_MATERNITE", "conge_sans_solde", "CONGE_SANS_SOLDE");

    private void conges() {
        String t = "conges";
        Map<Long, String> types = new HashMap<>(), statuts = new HashMap<>();
        Map<Long, String> libelle = new HashMap<>();
        for (Map<String, Object> r : src.queryForList("SELECT id, code, name_trans FROM conge_types")) { types.put(lng(r.get("id")), (String) r.get("code")); libelle.put(lng(r.get("id")), trad((String) r.get("name_trans"), "fr")); }
        for (Map<String, Object> r : src.queryForList("SELECT id, code FROM deplacement_programme_statuses")) statuts.put(lng(r.get("id")), (String) r.get("code"));
        Map<String, String> typeId = new HashMap<>();
        dst.query("SELECT code, id FROM type_demande", rs -> { typeId.put(rs.getString(1), rs.getString(2)); });
        Map<String, Map<Integer, BigDecimal>> prisParAgent = new TreeMap<>();
        for (Map<String, Object> r : src.queryForList("SELECT * FROM conges ORDER BY id")) {
            inc(t, "lus"); long id = lng(r.get("id"));
            if (dst.queryForObject("SELECT COUNT(*) FROM demande WHERE source_legacy = 'conge' AND id_legacy = ?", Integer.class, id) > 0) { inc(t, "deja_presents"); continue; }
            String pers = r.get("personne_id") == null ? null : personnelDe(lng(r.get("personne_id")));
            if (pers == null) { anomalie("PERSONNE_INCONNUE", t, id, "Personne absente de la cible : congé non repris"); inc(t, "ignores"); continue; }
            String typeCode = TYPE_CONGE.get(types.get(r.get("type_id") == null ? -1L : lng(r.get("type_id"))));
            if (typeCode == null || !typeId.containsKey(typeCode)) { anomalie("TYPE_CONGE_INCONNU", t, id, "Type de congé non reconnu : non repris"); inc(t, "ignores"); continue; }
            String sc = statuts.get(r.get("status_id") == null ? -1L : lng(r.get("status_id")));
            String statut = switch (String.valueOf(sc)) { case "finalized" -> "approuvee"; case "cancelled" -> "annulee"; default -> "soumise"; };
            Date debut = dateValide(r.get("date_debut")), fin = dateValide(r.get("date_fin"));
            if (debut == null || fin == null) anomalie("DATE_CONGE_INVALIDE", t, id, "Date de début ou de fin invalide dans la source : laissée vide");
            BigDecimal jours = r.get("nombre_jours_conge") != null ? bd(r.get("nombre_jours_conge")) : bd(r.get("nombre_jours_annee_courante")).add(bd(r.get("nombre_jours_annee_precedente")));
            Map<String, Object> donnees = new LinkedHashMap<>();
            donnees.put("numeroLegacy", r.get("numero_demande")); donnees.put("dateDemande", r.get("date_demande") == null ? null : r.get("date_demande").toString());
            donnees.put("lieu", r.get("lieu_conge")); donnees.put("articleSpecial", r.get("article_special")); donnees.put("langueDecision", r.get("langue_decision"));
            donnees.put("joursAnneeCourante", r.get("nombre_jours_annee_courante")); donnees.put("joursAnneePrecedente", r.get("nombre_jours_annee_precedente"));
            donnees.put("remplacantId", r.get("remplacant_id") == null ? null : personnelDe(lng(r.get("remplacant_id"))));
            String json; try { json = om.writeValueAsString(donnees); } catch (Exception e) { json = "{}"; }
            String objet = tronque(libelle.getOrDefault(r.get("type_id") == null ? -1L : lng(r.get("type_id")), "Congé"), 250);
            String cible = newId();
            Object cree = r.get("created_at"), maj = r.get("updated_at");
            dst.update("INSERT INTO demande (id, numero, personnel_id, type_demande_id, objet, description, statut, cree_le, modifie_le, date_debut, date_fin, nb_jours, donnees_json, id_legacy, source_legacy) VALUES (?,NULL,?,?,?,?,?,?,?,?,?,?,?,?, 'conge')",
                    cible, pers, typeId.get(typeCode), objet, r.get("objet_conge_exceptionnel"), statut,
                    cree == null ? new java.sql.Timestamp(System.currentTimeMillis()) : java.sql.Timestamp.valueOf(toLdt(cree)), maj == null ? null : java.sql.Timestamp.valueOf(toLdt(maj)), debut, fin, jours, json, id);
            dst.update("INSERT INTO demande_historique (id, demande_id, acteur_id, ancien_statut, nouveau_statut, commentaire, migre) VALUES (UUID(),?,NULL,NULL,?,?,1)", cible, statut, "Repris de Suivi RH (statut d'origine : " + sc + ")");
            inc(t, "crees"); mapper(t, id, "demande", cible);
            if ("approuvee".equals(statut) && "CONGE_ANNUEL".equals(typeCode) && debut != null && r.get("deleted_at") == null)
                prisParAgent.computeIfAbsent(pers, k -> new TreeMap<>()).merge(debut.toLocalDate().getYear(), jours, BigDecimal::add);
        }
        // les droits annuels ne sont pas dans Suivi RH : pas de solde inventé, la RH reçoit les jours déjà pris
        long agents = prisParAgent.size();
        anomalie("SOLDES_A_INITIALISER", "conges", "*", agents + " agents ont des congés annuels approuvés : les droits et reports de chaque année sont à saisir par la RH");
    }

    private Date dateValide(Object o) {
        if (o == null) return null;
        Date d = sqlDate(o);
        int y = d.toLocalDate().getYear();
        return y < 1990 || y > 2100 ? null : d;
    }

    private void messages() {
        String t = "messages";
        for (Map<String, Object> r : src.queryForList("SELECT * FROM messages ORDER BY id")) {
            inc(t, "lus"); long id = lng(r.get("id"));
            if (dst.queryForObject("SELECT COUNT(*) FROM legacy_archive WHERE table_legacy = 'messages' AND id_legacy = ?", Integer.class, String.valueOf(id)) > 0) { inc(t, "deja_presents"); continue; }
            // les messages viennent de comptes d'entité, sans agent : aucune réclamation ne peut leur être rattachée, on les archive
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("message", r.get("message")); d.put("reponse", r.get("response")); d.put("telephone", r.get("phone")); d.put("compteLegacy", r.get("user_id"));
            d.put("creeLe", r.get("created_at") == null ? null : r.get("created_at").toString());
            try { dst.update("INSERT INTO legacy_archive (id, table_legacy, id_legacy, donnees) VALUES (UUID(),'messages',?,?)", String.valueOf(id), om.writeValueAsString(d)); }
            catch (Exception e) { throw new IllegalStateException(e); }
            inc(t, "crees");
        }
        if (compteurs.get(t) != null && (Integer) compteurs.get(t).get("crees") > 0) anomalie("MESSAGES_ARCHIVES", t, "*", "Messages archivés en lecture seule (compte d'entité, sans agent)");
    }

    /** Compteurs = maximum de l'ancien système, par délégation, par année et par type, pour qu'aucun nouveau numéro n'entre en collision. */
    private void initialiserCompteurs() {
        for (Map<String, Object> m : dst.queryForList("SELECT om.numero_ordm_legacy AS o, om.numero_etat_legacy AS e, om.annee, s.delegation_id AS d FROM ordre_mission om JOIN personnel p ON p.id = om.personnel_id JOIN structure s ON s.id = p.structure_id WHERE om.source_legacy = 'propre'")) {
            int annee = annee(m.get("annee")); String d = (String) m.get("d");
            if (m.get("o") != null && m.get("o").toString().matches("\\d{1,9}")) compteur(d, annee, "ordm", Integer.parseInt(m.get("o").toString()));
            if (m.get("e") != null) {
                var mt = NUM_ETAT.matcher(m.get("e").toString());
                if (mt.find()) {
                    List<String> dl = dst.queryForList("SELECT id FROM delegation_province WHERE symbole = ?", String.class, mt.group(1).toUpperCase());
                    // « PR / SB - 1 / 2024 » : le dernier groupe de lettres avant le tiret est le symbole de la délégation
                    compteur(dl.isEmpty() ? d : dl.get(0), Integer.parseInt(mt.group(3)), "etat", Integer.parseInt(mt.group(2)));
                }
            }
        }
    }

    private void compteur(String deleg, int annee, String type, int valeur) {
        dst.update("INSERT INTO delegation_compteur (delegation_id, annee, type_numero, dernier_numero) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE dernier_numero = GREATEST(dernier_numero, VALUES(dernier_numero))", deleg, annee, type, valeur);
    }

    /** Réconciliation : sommes recalculées indépendamment dans la source et dans la cible, écart exigé nul au centime. */
    @SuppressWarnings("unchecked")
    private void reconciliation() {
        Map<String, Object> rec = new LinkedHashMap<>();
        Map<String, Object> s = src.queryForMap("""
                SELECT COUNT(*) AS n, COALESCE(SUM(nombre_repas_taux_1 * indemnite_taux_1 + nombre_repas_taux_2 * indemnite_taux_2),0) AS repas,
                       COALESCE(SUM(CASE WHEN frais_transport > 0 AND moyen_transport_id = (SELECT id FROM moyen_transports WHERE code = 'transports_en_commun') THEN frais_transport ELSE 0 END),0) AS transport,
                       COALESCE(SUM(ROUND(COALESCE(distance_trajet,0) * COALESCE(nombre_trajet,0) * COALESCE(taux_km,0), 2)),0) AS km
                FROM deplacement_propres WHERE deleted_at IS NULL""");
        Map<String, Object> d = dst.queryForMap("""
                SELECT COUNT(*) AS n, COALESCE(SUM(montant_indemnites),0) AS repas,
                       COALESCE(SUM(CASE WHEN frais_transport > 0 AND moyen_transport_id = (SELECT id FROM moyen_transport WHERE code = 'transports_en_commun') THEN frais_transport ELSE 0 END),0) AS transport,
                       COALESCE(SUM(montant_km),0) AS km
                FROM ordre_mission WHERE source_legacy = 'propre' AND supprime_le IS NULL""");
        int ignores = (Integer) Optional.ofNullable(compteurs.get("deplacement_propres")).map(m -> m.get("ignores")).orElse(0);
        for (String k : List.of("repas", "transport", "km")) {
            BigDecimal a = bd(s.get(k)).setScale(2, java.math.RoundingMode.HALF_UP), b = bd(d.get(k)).setScale(2, java.math.RoundingMode.HALF_UP);
            Map<String, Object> ligne = new LinkedHashMap<>(); ligne.put("source", a); ligne.put("cible", b); ligne.put("ecart", a.subtract(b)); rec.put("somme_" + k, ligne);
            if (a.compareTo(b) != 0) anomalie("RECONCILIATION_ECART", "deplacement_propres", k, "Écart de " + a.subtract(b) + " MAD sur " + k);
        }
        rec.put("missions_non_supprimees_source", ((Number) s.get("n")).longValue()); rec.put("missions_non_supprimees_cible", ((Number) d.get("n")).longValue()); rec.put("missions_ignorees", ignores);
        if (((Number) s.get("n")).longValue() != ((Number) d.get("n")).longValue() + (ignores == 0 ? 0 : 0)) anomalie("RECONCILIATION_NOMBRE", "deplacement_propres", "*", "Nombre de missions différent entre source et cible");
        Map<String, Long> parStatut = new TreeMap<>();
        dst.queryForList("SELECT statut, COUNT(*) AS n FROM ordre_mission WHERE source_legacy = 'propre' GROUP BY statut").forEach(m -> parStatut.put((String) m.get("statut"), ((Number) m.get("n")).longValue()));
        rec.put("missions_par_statut_cible", parStatut);
        rec.put("conges_source", src.queryForObject("SELECT COUNT(*) FROM conges", Long.class));
        rec.put("conges_cible", dst.queryForObject("SELECT COUNT(*) FROM demande WHERE source_legacy = 'conge'", Long.class));
        rec.put("personnes_source", src.queryForObject("SELECT COUNT(*) FROM personnes", Long.class));
        rec.put("personnel_cible", dst.queryForObject("SELECT COUNT(*) FROM personnel WHERE id_legacy IS NOT NULL", Long.class));
        compteurs.put("reconciliation", rec);
    }
}
