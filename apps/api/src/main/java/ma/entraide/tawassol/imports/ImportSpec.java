package ma.entraide.tawassol.imports;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Champs attendus par type d'import. La correspondance colonnes → champs est un réglage (modèle d'import), pas du code. */
public final class ImportSpec {
    public record Champ(String key, String label, boolean obligatoire, List<String> synonymes) {}

    private static Champ c(String key, String label, boolean req, String... syn) { return new Champ(key, label, req, List.of(syn)); }

    public static final Map<String, List<Champ>> CHAMPS = new LinkedHashMap<>();
    static {
        CHAMPS.put("personnel", List.of(c("matricule", "Matricule", true, "mat", "numero", "n"), c("nom_fr", "Nom (FR)", true, "nom", "nomfr"), c("prenom_fr", "Prénom (FR)", true, "prenom", "prenomfr"),
                c("nom_ar", "Nom (AR)", true, "nomar"), c("prenom_ar", "Prénom (AR)", true, "prenomar"), c("structure_code", "Code structure", true, "structure", "codestructure"),
                c("date_recrutement", "Date de recrutement", true, "recrutement", "daterec"), c("grade_code", "Code grade", false, "grade", "codegrade"), c("email", "E-mail", false, "mail", "courriel"),
                c("telephone", "Téléphone", false, "tel"), c("cin", "CIN", false), c("sexe", "Sexe", false), c("date_naissance", "Date de naissance", false, "naissance"),
                c("situation_familiale", "Situation familiale", false, "situation"), c("echelon", "Échelon", false), c("echelle", "Échelle", false), c("date_echelon", "Date d'échelon", false), c("date_echelle", "Date d'échelle", false)));
        CHAMPS.put("salaire", List.of(c("matricule", "Matricule", true, "mat", "numero"), c("salaire_net", "Salaire net", true, "net", "netapayer", "salaire"),
                c("alloc_familiale", "Allocations familiales", false, "allocfamiliale", "af", "allocations"), c("retenue_mutuelle", "Retenue mutuelle", false, "mutuelle"),
                c("rappel", "Rappel", false), c("echelon", "Échelon", false)));
        CHAMPS.put("prime", List.of(c("matricule", "Matricule", true, "mat"), c("type_prime", "Type de prime", false, "type"), c("montant_brut", "Montant brut", true, "brut"),
                c("ir", "IR", false, "impot"), c("montant_net", "Montant net", true, "net"), c("date_prime", "Date de la prime", true, "date")));
        CHAMPS.put("credit", List.of(c("matricule", "Matricule", true, "mat"), c("banque", "Banque / organisme", true, "organisme"), c("num_dossier", "N° de dossier", false, "dossier", "numdossier"),
                c("type_credit", "Type de crédit", false, "type"), c("mensualite", "Mensualité", true, "retenue"), c("montant_global", "Montant global", false, "global"),
                c("montant_restant", "Montant restant dû", false, "restant", "restantdu"), c("nb_mois_restants", "Mois restants", false, "moisrestants"), c("date_debut", "Date de début", false, "debut")));
    }

    private ImportSpec() {}

    public static String normalise(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** Proposition automatique de correspondance : nom du champ, libellé ou synonyme, comparés sans accents ni ponctuation. */
    public static Map<String, String> suggerer(String type, List<String> colonnes) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Champ ch : CHAMPS.get(type)) {
            for (String col : colonnes) {
                String n = normalise(col);
                if (n.equals(normalise(ch.key())) || n.equals(normalise(ch.label())) || ch.synonymes().stream().anyMatch(s -> n.equals(normalise(s)))) { out.put(ch.key(), col); break; }
            }
        }
        return out;
    }
}
