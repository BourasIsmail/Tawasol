package ma.entraide.tawassol.imports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.imports.ImportSpec.Champ;
import ma.entraide.tawassol.imports.SheetReader.Feuille;
import ma.entraide.tawassol.imports.SheetReader.Reglages;
import ma.entraide.tawassol.param.NumeroService;
import ma.entraide.tawassol.rh.PersonnelRhService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Import de fichiers Excel ou CSV en trois temps sans état : analyse (colonnes, aperçu, correspondance proposée), contrôle (rapport ligne par ligne,
 * rien n'est écrit) puis import. Les données financières entrent en brouillon dans un lot et ne deviennent visibles qu'à la publication du lot ;
 * une ligne remplacée n'est jamais supprimée (statut « remplace »). Les lignes en anomalie sont exclues et consignées dans le rapport.
 */
@Service
public class ImportService {
    public record Rubrique(String colonne, String code, String libelleFr, String libelleAr, String sens) {}
    public record Mapping(Map<String, String> colonnes, List<Rubrique> rubriques, int ligneEntete) {
        public Mapping(Map<String, String> colonnes, List<Rubrique> rubriques) { this(colonnes, rubriques, 1); }
    }
    public record Demande(String type, byte[] fichier, String fichierNom, Mapping mapping, Reglages reglages, String formatDate, char decimal,
                          Integer annee, Integer mois, boolean remplacerBrouillon, String modeleId) {}
    public record Anomalie(int ligne, String champ, String code, String message, String niveau) {}
    public record Rapport(int total, int valides, int avertissements, int erreurs, List<Anomalie> anomalies) {}
    record Ligne(int numero, Map<String, Object> v) {}
    public record Resultat(Rapport rapport, List<Map<String, Object>> apercu) {}

    private static final Set<String> DECIMAUX = Set.of("salaire_net", "alloc_familiale", "retenue_mutuelle", "rappel", "montant_brut", "ir", "montant_net", "mensualite", "montant_global", "montant_restant");
    private static final Set<String> DATES = Set.of("date_recrutement", "date_naissance", "date_echelon", "date_prime", "date_debut");
    private static final Set<String> ENTIERS = Set.of("nb_mois_restants", "echelle");
    private static final int MAX_ANOMALIES_RETOURNEES = 500;

    private final JdbcTemplate jdbc;
    private final PersonnelRhService personnelRh;
    private final NumeroService numeros;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public ImportService(JdbcTemplate jdbc, PersonnelRhService personnelRh, NumeroService numeros, AuditService audit, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc; this.personnelRh = personnelRh; this.numeros = numeros; this.audit = audit; this.json = json; this.clock = clock;
    }

    // ---------- analyse ----------

    public Map<String, Object> analyser(String type, byte[] fichier, Reglages reglages) {
        verifierType(type);
        Feuille f = SheetReader.lire(fichier, reglages);
        List<List<String>> apercu = new ArrayList<>();
        for (int i = 0; i < Math.min(5, f.lignes().size()); i++) apercu.add(new ArrayList<>(f.lignes().get(i).values()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("colonnes", f.colonnes()); out.put("apercu", apercu); out.put("nbLignes", f.lignes().size());
        out.put("mappingSuggere", ImportSpec.suggerer(type, f.colonnes())); out.put("champs", ImportSpec.CHAMPS.get(type));
        return out;
    }

    // ---------- contrôle ----------

    public Resultat controler(Demande d) { return valider(d).resultat(); }

    private record Valide(Resultat resultat, List<Ligne> lignes, boolean brouillonExistant) {}

    private Valide valider(Demande d) {
        verifierType(d.type());
        Feuille f = SheetReader.lire(d.fichier(), d.reglages());
        List<Champ> champs = ImportSpec.CHAMPS.get(d.type());
        List<Anomalie> an = new ArrayList<>();
        Map<String, String> col = d.mapping() == null ? Map.of() : d.mapping().colonnes();
        for (Champ c : champs) {
            if (c.obligatoire() && (col.get(c.key()) == null || !f.colonnes().contains(col.get(c.key())))) an.add(new Anomalie(0, c.key(), "MAPPING_INCOMPLET", "Colonne non associée au champ obligatoire : " + c.label(), "ERREUR"));
        }
        if (an.stream().anyMatch(a -> a.niveau().equals("ERREUR"))) return new Valide(new Resultat(rapport(f.lignes().size(), 0, an), List.of()), List.of(), false);
        Map<String, String> matricules = new HashMap<>();
        jdbc.query("SELECT matricule, id FROM personnel WHERE est_actif = 1", rs -> { matricules.put(rs.getString(1), rs.getString(2)); });
        Map<String, String> structures = new HashMap<>(), grades = new HashMap<>();
        if (d.type().equals("personnel")) {
            jdbc.query("SELECT code, id FROM structure WHERE est_active = 1", rs -> { structures.put(rs.getString(1), rs.getString(2)); });
            jdbc.query("SELECT code, id FROM grade", rs -> { grades.put(String.valueOf(rs.getInt(1)), rs.getString(2)); });
        }
        Set<String> publies = new HashSet<>();
        boolean brouillon = false;
        if (d.type().equals("salaire")) {
            if (d.annee() == null || d.mois() == null || d.mois() < 1 || d.mois() > 12) an.add(new Anomalie(0, "periode", "PERIOD_REQUIRED", "Année et mois obligatoires pour un import de salaires", "ERREUR"));
            else {
                jdbc.query("SELECT personnel_id, statut FROM salaire WHERE annee = ? AND mois = ?", rs -> { if (!"brouillon".equals(rs.getString(2)) ) publies.add(rs.getString(1)); }, d.annee(), d.mois());
                brouillon = jdbc.queryForObject("SELECT COUNT(*) FROM salaire WHERE annee = ? AND mois = ? AND statut = 'brouillon'", Integer.class, d.annee(), d.mois()) > 0;
                if (brouillon) an.add(new Anomalie(0, "periode", "PERIOD_HAS_DRAFT", "Des salaires en brouillon existent déjà pour cette période : ils seront remplacés si vous le confirmez", "AVERTISSEMENT"));
            }
        }
        List<Ligne> valides = new ArrayList<>();
        Set<String> vus = new HashSet<>();
        for (int i = 0; i < f.lignes().size(); i++) {
            int n = f.numeros().get(i);
            Map<String, String> raw = f.lignes().get(i);
            Map<String, Object> v = new LinkedHashMap<>();
            boolean ok = true;
            for (Champ c : champs) {
                String h = col.get(c.key());
                String s = h == null ? "" : raw.getOrDefault(h, "").trim();
                if (s.isEmpty()) {
                    if (c.obligatoire()) { an.add(new Anomalie(n, c.key(), "REQUIRED", c.label() + " obligatoire", "ERREUR")); ok = false; }
                    continue;
                }
                try {
                    if (DECIMAUX.contains(c.key())) { BigDecimal b = SheetReader.decimal(s, d.decimal()); if (b.signum() < 0) throw new IllegalArgumentException("negatif"); v.put(c.key(), b); }
                    else if (DATES.contains(c.key())) v.put(c.key(), SheetReader.date(s, d.formatDate()));
                    else if (ENTIERS.contains(c.key())) v.put(c.key(), new BigDecimal(s.replace(",", ".")).intValue());
                    else v.put(c.key(), s);
                } catch (IllegalArgumentException | java.time.DateTimeException e) {
                    boolean neg = "negatif".equals(e.getMessage());
                    an.add(new Anomalie(n, c.key(), neg ? "NEGATIVE_AMOUNT" : "INVALID_VALUE", c.label() + (neg ? " négatif" : " invalide : « " + s + " »"), "ERREUR"));
                    ok = false;
                }
            }
            if (!ok) continue;
            if (!d.type().equals("personnel")) {
                String pid = matricules.get(String.valueOf(v.get("matricule")));
                if (pid == null) { an.add(new Anomalie(n, "matricule", "UNKNOWN_MATRICULE", "Matricule inconnu ou agent inactif : " + v.get("matricule"), "ERREUR")); continue; }
                v.put("pid", pid);
            }
            String cle = switch (d.type()) { case "credit" -> v.get("matricule") + "|" + v.getOrDefault("num_dossier", ""); case "prime" -> n + ""; default -> String.valueOf(v.get("matricule")); };
            if (!vus.add(cle)) { an.add(new Anomalie(n, "matricule", "DUPLICATE_IN_FILE", "Ligne en double dans le fichier", "ERREUR")); continue; }
            switch (d.type()) {
                case "personnel" -> {
                    String sid = structures.get(String.valueOf(v.get("structure_code")));
                    if (sid == null) { an.add(new Anomalie(n, "structure_code", "UNKNOWN_STRUCTURE", "Structure introuvable : " + v.get("structure_code"), "ERREUR")); continue; }
                    v.put("structureId", sid);
                    if (v.get("grade_code") != null) {
                        String gid = grades.get(String.valueOf(v.get("grade_code")));
                        if (gid == null) { an.add(new Anomalie(n, "grade_code", "UNKNOWN_GRADE", "Grade introuvable : " + v.get("grade_code"), "ERREUR")); continue; }
                        v.put("gradeId", gid);
                    }
                    if (matricules.containsKey(String.valueOf(v.get("matricule")))) an.add(new Anomalie(n, "matricule", "WILL_UPDATE", "Agent existant : sa fiche sera mise à jour", "AVERTISSEMENT"));
                }
                case "salaire" -> { if (publies.contains(v.get("pid"))) { an.add(new Anomalie(n, "matricule", "ALREADY_PUBLISHED", "Salaire déjà publié pour cette période : toute correction est une nouvelle écriture tracée", "ERREUR")); continue; } }
                case "prime" -> {
                    String tp = String.valueOf(v.getOrDefault("type_prime", "gratification")).toLowerCase();
                    if (!Set.of("gratification", "indemnite", "autre").contains(tp)) { an.add(new Anomalie(n, "type_prime", "INVALID_VALUE", "Type de prime inconnu : " + tp, "ERREUR")); continue; }
                    v.put("type_prime", tp);
                    if (((BigDecimal) v.get("montant_net")).compareTo((BigDecimal) v.get("montant_brut")) > 0) an.add(new Anomalie(n, "montant_net", "NET_ABOVE_GROSS", "Le net dépasse le brut", "AVERTISSEMENT"));
                }
                case "credit" -> {
                    String tc = String.valueOf(v.getOrDefault("type_credit", "bancaire"));
                    if (!Set.of("bancaire", "interne_AOS").contains(tc)) { an.add(new Anomalie(n, "type_credit", "INVALID_VALUE", "Type de crédit inconnu : " + tc, "ERREUR")); continue; }
                    v.put("type_credit", tc);
                }
                default -> { }
            }
            if (d.type().equals("salaire") && d.mapping().rubriques() != null) {
                List<Map<String, Object>> rub = new ArrayList<>();
                int ordre = 0;
                for (Rubrique r : d.mapping().rubriques()) {
                    String s = raw.getOrDefault(r.colonne(), "").trim();
                    if (s.isEmpty()) continue;
                    try { rub.add(Map.of("ordre", ++ordre, "r", r, "montant", SheetReader.decimal(s, d.decimal()))); }
                    catch (RuntimeException e) { an.add(new Anomalie(n, r.colonne(), "INVALID_VALUE", "Rubrique " + r.code() + " invalide : « " + s + " »", "AVERTISSEMENT")); }
                }
                v.put("rubriques", rub);
            }
            valides.add(new Ligne(n, v));
        }
        List<Map<String, Object>> apercu = new ArrayList<>();
        valides.stream().limit(5).forEach(l -> apercu.add(l.v()));
        return new Valide(new Resultat(rapport(f.lignes().size(), valides.size(), an), apercu), valides, brouillon);
    }

    private Rapport rapport(int total, int valides, List<Anomalie> an) {
        int err = (int) an.stream().filter(a -> a.niveau().equals("ERREUR")).count();
        int warn = an.size() - err;
        return new Rapport(total, valides, warn, err, an.size() > MAX_ANOMALIES_RETOURNEES ? an.subList(0, MAX_ANOMALIES_RETOURNEES) : an);
    }

    // ---------- import ----------

    @Transactional
    public Map<String, Object> importer(AuthenticatedUser actor, Demande d) {
        Valide val = valider(d);
        Rapport rap0 = val.resultat().rapport();
        if (rap0.anomalies().stream().anyMatch(a -> a.ligne() == 0 && a.niveau().equals("ERREUR"))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MAPPING_INCOMPLET", "Le fichier ne peut pas être importé : voir le contrôle");
        String sha = sha256(d.fichier());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM import_lot WHERE type_import = ? AND fichier_sha256 = ? AND statut <> 'annule'", Integer.class, d.type(), sha) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FILE", "Ce fichier a déjà été importé");
        }
        if (val.brouillonExistant() && !d.remplacerBrouillon()) throw new ApiException(HttpStatus.CONFLICT, "PERIOD_HAS_DRAFT", "Des salaires en brouillon existent pour cette période : confirmez leur remplacement");
        LocalDateTime now = LocalDateTime.now(clock);
        String lotId = UUID.randomUUID().toString();
        String numero = numeros.suivant("LOT", now.getYear());
        if (d.type().equals("salaire") && val.brouillonExistant()) {
            jdbc.update("DELETE FROM salaire_ligne WHERE salaire_id IN (SELECT id FROM salaire WHERE annee = ? AND mois = ? AND statut = 'brouillon')", d.annee(), d.mois());
            jdbc.update("DELETE FROM salaire WHERE annee = ? AND mois = ? AND statut = 'brouillon'", d.annee(), d.mois());
            jdbc.update("UPDATE import_lot SET statut = 'annule' WHERE type_import = 'salaire' AND periode_annee = ? AND periode_mois = ? AND statut = 'brouillon'", d.annee(), d.mois());
        }
        List<Anomalie> anomalies = new ArrayList<>(rap0.anomalies());
        int ecrites = 0;
        boolean direct = d.type().equals("personnel");
        jdbc.update("INSERT INTO import_lot (id, numero, type_import, modele_id, periode_annee, periode_mois, fichier_nom, fichier_sha256, statut, nb_lignes, nb_erreurs, cree_par, publie_par, publie_le, cree_le) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                lotId, numero, d.type(), d.modeleId(), d.annee(), d.mois(), nomSur(d.fichierNom()), sha, direct ? "publie" : "brouillon", 0, 0, actor.userId(), direct ? actor.userId() : null, direct ? now : null, now);
        Map<String, String> gradeParAgent = new HashMap<>();
        if (d.type().equals("salaire")) jdbc.query("SELECT id, grade_id FROM personnel", rs -> { gradeParAgent.put(rs.getString(1), rs.getString(2)); });
        for (Ligne l : val.lignes()) {
            Map<String, Object> v = l.v();
            try {
                switch (d.type()) {
                    case "personnel" -> ecrirePersonnel(actor, v, now);
                    case "salaire" -> {
                        String sid = UUID.randomUUID().toString();
                        jdbc.update("INSERT INTO salaire (id, personnel_id, grade_id, annee, mois, echelon, salaire_net, alloc_familiale, retenue_mutuelle, rappel, lot_id, statut, cree_le) VALUES (?,?,?,?,?,?,?,?,?,?,?,'brouillon',?)",
                                sid, v.get("pid"), gradeParAgent.get(v.get("pid")), d.annee(), d.mois(), v.get("echelon"), v.get("salaire_net"), v.getOrDefault("alloc_familiale", BigDecimal.ZERO),
                                v.getOrDefault("retenue_mutuelle", BigDecimal.ZERO), v.getOrDefault("rappel", BigDecimal.ZERO), lotId, now);
                        @SuppressWarnings("unchecked") List<Map<String, Object>> rub = (List<Map<String, Object>>) v.getOrDefault("rubriques", List.of());
                        for (Map<String, Object> r : rub) {
                            Rubrique rb = (Rubrique) r.get("r");
                            jdbc.update("INSERT INTO salaire_ligne (id, salaire_id, ordre, code, libelle_fr, libelle_ar, sens, montant) VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID().toString(), sid, r.get("ordre"), rb.code(),
                                    rb.libelleFr() == null ? rb.code() : rb.libelleFr(), rb.libelleAr(), "gain".equals(rb.sens()) ? "gain" : "retenue", r.get("montant"));
                        }
                    }
                    case "prime" -> jdbc.update("INSERT INTO prime (id, personnel_id, type_prime, montant_brut, ir, montant_net, date_prime, lot_id, statut, cree_le) VALUES (?,?,?,?,?,?,?,?,'brouillon',?)",
                            UUID.randomUUID().toString(), v.get("pid"), v.get("type_prime"), v.get("montant_brut"), v.getOrDefault("ir", BigDecimal.ZERO), v.get("montant_net"), v.get("date_prime"), lotId, now);
                    case "credit" -> jdbc.update("INSERT INTO credit (id, personnel_id, banque, num_dossier, type_credit, mensualite, montant_global, montant_restant, nb_mois_restants, date_debut, lot_id, statut, cree_le) VALUES (?,?,?,?,?,?,?,?,?,?,?,'brouillon',?)",
                            UUID.randomUUID().toString(), v.get("pid"), v.get("banque"), v.get("num_dossier"), v.get("type_credit"), v.get("mensualite"), v.getOrDefault("montant_global", BigDecimal.ZERO),
                            v.getOrDefault("montant_restant", BigDecimal.ZERO), v.getOrDefault("nb_mois_restants", 0), v.get("date_debut"), lotId, now);
                    default -> throw new IllegalStateException();
                }
                ecrites++;
            } catch (ApiException e) {
                anomalies.add(new Anomalie(l.numero(), "ligne", e.code(), e.getMessage(), "ERREUR"));
            }
        }
        Rapport rap = rapport(rap0.total(), ecrites, anomalies);
        try {
            jdbc.update("UPDATE import_lot SET nb_lignes = ?, nb_erreurs = ?, rapport_erreurs = ? WHERE id = ?", ecrites, rap.erreurs(), json.writeValueAsString(anomalies), lotId);
        } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        audit.record(actor.userId(), "IMPORT_LOT_CREE", "import_lot", lotId, null, "{\"numero\":\"" + numero + "\",\"type\":\"" + d.type() + "\",\"lignes\":" + ecrites + ",\"erreurs\":" + rap.erreurs() + "}");
        return lot(lotId);
    }

    private void ecrirePersonnel(AuthenticatedUser actor, Map<String, Object> v, LocalDateTime now) {
        String matricule = String.valueOf(v.get("matricule"));
        PersonnelRhService.Input in = new PersonnelRhService.Input(matricule, s(v, "nom_fr"), s(v, "prenom_fr"), s(v, "nom_ar"), s(v, "prenom_ar"), (String) v.get("structureId"), (String) v.get("gradeId"),
                (LocalDate) v.get("date_recrutement"), s(v, "email"), s(v, "telephone"), s(v, "cin"), s(v, "sexe"), s(v, "situation_familiale"), (LocalDate) v.get("date_naissance"), null,
                s(v, "echelon"), (Integer) v.get("echelle"), (LocalDate) v.get("date_echelon"), "Import", null);
        var existing = personnelRh.idByMatricule(matricule);
        if (existing.isPresent()) personnelRh.update(actor, existing.get(), in); else personnelRh.create(actor, in);
    }

    // ---------- lots ----------

    public List<Map<String, Object>> lots(String type) {
        String sql = "SELECT l.id, l.numero, l.type_import AS type, l.statut, l.periode_annee AS periodeAnnee, l.periode_mois AS periodeMois, l.fichier_nom AS fichierNom, l.nb_lignes AS nbLignes, l.nb_erreurs AS nbErreurs, l.cree_le AS creeLe, l.publie_le AS publieLe FROM import_lot l"
                + (type == null || type.isBlank() ? "" : " WHERE l.type_import = ?") + " ORDER BY l.cree_le DESC, l.numero DESC LIMIT 100";
        return type == null || type.isBlank() ? jdbc.queryForList(sql) : jdbc.queryForList(sql, type);
    }

    public Map<String, Object> lot(String id) {
        return jdbc.queryForList("SELECT l.id, l.numero, l.type_import AS type, l.statut, l.periode_annee AS periodeAnnee, l.periode_mois AS periodeMois, l.fichier_nom AS fichierNom, l.nb_lignes AS nbLignes, l.nb_erreurs AS nbErreurs, l.cree_le AS creeLe, l.publie_le AS publieLe FROM import_lot l WHERE l.id = ?", id)
                .stream().findFirst().orElseThrow(ApiException::notFound);
    }

    @Transactional
    public Map<String, Object> publier(AuthenticatedUser actor, String id) {
        Map<String, Object> lot = lot(id);
        if (!"brouillon".equals(lot.get("statut"))) throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Ce lot n'est plus en brouillon");
        LocalDateTime now = LocalDateTime.now(clock);
        String type = (String) lot.get("type");
        if (type.equals("credit")) {
            jdbc.update("UPDATE credit old JOIN credit nw ON nw.personnel_id = old.personnel_id AND nw.num_dossier = old.num_dossier AND nw.lot_id = ? SET old.statut = 'remplace' WHERE old.statut = 'publie' AND old.lot_id <> ?", id, id);
        }
        String table = switch (type) { case "salaire" -> "salaire"; case "prime" -> "prime"; case "credit" -> "credit"; default -> throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Ce type de lot n'a pas de publication"); };
        jdbc.update("UPDATE " + table + " SET statut = 'publie', publie_le = ? WHERE lot_id = ?", now, id);
        jdbc.update("UPDATE import_lot SET statut = 'publie', publie_par = ?, publie_le = ? WHERE id = ?", actor.userId(), now, id);
        int notifies = 0;
        if (type.equals("salaire")) {
            notifies = jdbc.update("INSERT INTO notification (id, utilisateur_id, type, titre, message, lien, cree_le) SELECT UUID(), u.id, 'BULLETIN_DISPONIBLE', 'Votre bulletin est disponible', CONCAT(s.mois, '/', s.annee), '/paie', ? FROM salaire s JOIN utilisateur u ON u.personnel_id = s.personnel_id AND u.est_actif = 1 WHERE s.lot_id = ?", now, id);
        }
        audit.record(actor.userId(), "IMPORT_LOT_PUBLIE", "import_lot", id, null, "{\"numero\":\"" + lot.get("numero") + "\",\"notifies\":" + notifies + "}");
        return lot(id);
    }

    @Transactional
    public Map<String, Object> annuler(AuthenticatedUser actor, String id) {
        Map<String, Object> lot = lot(id);
        if (!"brouillon".equals(lot.get("statut"))) throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Seul un lot en brouillon peut être annulé");
        jdbc.update("DELETE FROM salaire_ligne WHERE salaire_id IN (SELECT id FROM salaire WHERE lot_id = ?)", id);
        for (String t : List.of("salaire", "prime", "credit")) jdbc.update("DELETE FROM " + t + " WHERE lot_id = ? AND statut = 'brouillon'", id);
        jdbc.update("UPDATE import_lot SET statut = 'annule' WHERE id = ?", id);
        audit.record(actor.userId(), "IMPORT_LOT_ANNULE", "import_lot", id, null, null);
        return lot(id);
    }

    public byte[] rapportCsv(String id) {
        String raw = jdbc.queryForList("SELECT rapport_erreurs FROM import_lot WHERE id = ?", String.class, id).stream().findFirst().orElseThrow(ApiException::notFound);
        StringBuilder sb = new StringBuilder("﻿ligne;niveau;champ;code;message\n");
        try {
            for (Map<String, Object> a : json.readValue(raw == null ? "[]" : raw, new TypeReference<List<Map<String, Object>>>() {})) {
                sb.append(a.get("ligne")).append(';').append(a.get("niveau")).append(';').append(csv(a.get("champ"))).append(';').append(a.get("code")).append(';').append(csv(a.get("message"))).append('\n');
            }
        } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---------- utilitaires ----------

    private static String csv(Object o) {
        String s = o == null ? "" : o.toString();
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s; // neutralise l'injection de formule à l'ouverture dans Excel
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String s(Map<String, Object> v, String k) { Object o = v.get(k); return o == null ? null : o.toString(); }

    private static void verifierType(String type) {
        if (!ImportSpec.CHAMPS.containsKey(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMPORT_TYPE", "Type d'import pris en charge : " + String.join(", ", ImportSpec.CHAMPS.keySet()));
    }

    private static String nomSur(String n) { String s = n == null ? "fichier" : n.replace('\\', '/'); s = s.substring(s.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_"); return s.length() > 240 ? s.substring(0, 240) : s; }

    private static String sha256(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
