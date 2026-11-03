package ma.entraide.tawassol.migration;

import java.util.Set;

/** Découpage heuristique de « nom_prenom » en nom et prénom. Le texte d'origine est toujours conservé. */
public final class LegacyNames {
    public record Split(String nom, String prenom, boolean ambigu) {}

    private static final Set<String> PARTICULES_FR = Set.of("EL", "AL", "AIT", "AÏT", "BEN", "BENT", "OULD", "ABOU", "ABU", "BOU", "DE", "DU", "DES", "LA", "LE", "ES", "ECH", "EN", "ER", "ET", "ID", "IDER", "AZ", "ZAIT", "OU");
    private static final Set<String> PARTICULES_AR = Set.of("ال", "أيت", "آيت", "بن", "ابن", "أبو", "بو", "ولد", "بنت", "ايت");

    private LegacyNames() {}

    private static String[] mots(String s) { return s == null ? new String[0] : s.trim().replaceAll("\\s+", " ").split(" "); }

    /** Français : « NOM PRENOM ». Le dernier mot est le prénom, sauf si le reste n'est qu'une particule. */
    public static Split fr(String nomPrenom) {
        String[] m = mots(nomPrenom);
        if (m.length == 0 || m[0].isEmpty()) return new Split("", "", true);
        if (m.length == 1) return new Split(m[0], "", true);
        String prenom = m[m.length - 1];
        String nom = String.join(" ", java.util.Arrays.copyOf(m, m.length - 1));
        boolean ambigu = m.length > 3 || PARTICULES_FR.contains(m[m.length - 2].toUpperCase());
        return new Split(nom, prenom, ambigu);
    }

    /** Arabe : « PRENOM NOM » ; le nom est le dernier mot, précédé de sa particule éventuelle (ال, بن…). */
    public static Split ar(String nomPrenom) {
        String[] m = mots(nomPrenom);
        if (m.length == 0 || m[0].isEmpty()) return new Split("", "", true);
        if (m.length == 1) return new Split(m[0], "", true);
        int debutNom = m.length - 1;
        if (m.length >= 3 && PARTICULES_AR.contains(m[m.length - 2])) debutNom = m.length - 2;
        String prenom = String.join(" ", java.util.Arrays.copyOfRange(m, 0, debutNom));
        String nom = String.join(" ", java.util.Arrays.copyOfRange(m, debutNom, m.length));
        boolean ambigu = m.length > 3 || debutNom != m.length - 1;
        return new Split(nom, prenom, ambigu);
    }
}
