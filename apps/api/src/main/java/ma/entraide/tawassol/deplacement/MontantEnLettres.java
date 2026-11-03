package ma.entraide.tawassol.deplacement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Montant en toutes lettres en français et en arabe, avec l'unité (dirhams) et les centimes.
 * Corrige l'ancien modèle de Suivi RH (centimes lus comme un entier, sans unité). La formulation arabe est à valider par l'établissement.
 */
public final class MontantEnLettres {
    private MontantEnLettres() {}

    // ------------------------------------------------------------------ français

    private static final String[] UNITES = {"zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf"};
    private static final String[] DIZAINES = {"", "", "vingt", "trente", "quarante", "cinquante", "soixante", "soixante", "quatre-vingt", "quatre-vingt"};

    public static String fr(BigDecimal montant) {
        BigDecimal m = montant.setScale(2, RoundingMode.HALF_UP).abs();
        long dh = m.toBigInteger().longValueExact();
        int c = m.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String s = nombreFr(dh) + (dh > 1 ? " dirhams" : " dirham");
        if (c > 0) s += " et " + nombreFr(c) + (c > 1 ? " centimes" : " centime");
        return s;
    }

    static String nombreFr(long n) {
        if (n < 0 || n >= 1_000_000_000_000L) throw new IllegalArgumentException("Montant hors limites");
        if (n == 0) return "zéro";
        StringBuilder sb = new StringBuilder();
        long milliards = n / 1_000_000_000L, millions = n % 1_000_000_000L / 1_000_000L, milliers = n % 1_000_000L / 1000, reste = n % 1000;
        if (milliards > 0) sb.append(moinsDeMille(milliards, false)).append(milliards > 1 ? " milliards" : " milliard");
        if (millions > 0) { sep(sb); sb.append(moinsDeMille(millions, false)).append(millions > 1 ? " millions" : " million"); }
        if (milliers > 0) { sep(sb); sb.append(milliers == 1 ? "mille" : moinsDeMille(milliers, false) + " mille"); }
        if (reste > 0) { sep(sb); sb.append(moinsDeMille(reste, true)); }
        return sb.toString();
    }

    private static void sep(StringBuilder sb) { if (sb.length() > 0) sb.append(' '); }

    /** finDeNombre : « vingts » et « cents » prennent leur s seulement quand rien ne suit. */
    private static String moinsDeMille(long n, boolean finDeNombre) {
        StringBuilder sb = new StringBuilder();
        long cent = n / 100, r = n % 100;
        if (cent > 0) {
            if (cent > 1) sb.append(UNITES[(int) cent]).append(' ');
            sb.append("cent");
            if (cent > 1 && r == 0 && finDeNombre) sb.append('s');
            if (r > 0) sb.append(' ');
        }
        if (r > 0) sb.append(deuxChiffres((int) r, finDeNombre));
        return sb.toString();
    }

    private static String deuxChiffres(int n, boolean finDeNombre) {
        if (n < 20) return UNITES[n];
        int d = n / 10, u = n % 10;
        if (d == 7 || d == 9) {                       // soixante-dix, quatre-vingt-dix
            int v = 10 + u;
            String base = DIZAINES[d];
            if (d == 7 && u == 1) return base + " et onze";
            return base + "-" + UNITES[v];
        }
        if (d == 8) return u == 0 ? (finDeNombre ? "quatre-vingts" : "quatre-vingt") : "quatre-vingt-" + UNITES[u];
        if (u == 0) return DIZAINES[d];
        if (u == 1) return DIZAINES[d] + " et un";
        return DIZAINES[d] + "-" + UNITES[u];
    }

    // ------------------------------------------------------------------ arabe

    private static final String[] AR_UNITES = {"صفر", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر"};
    private static final String[] AR_DIZAINES = {"", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون"};
    private static final String[] AR_CENTAINES = {"", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة", "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة"};

    public static String ar(BigDecimal montant) {
        BigDecimal m = montant.setScale(2, RoundingMode.HALF_UP).abs();
        long dh = m.toBigInteger().longValueExact();
        int c = m.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String s = dh == 0 ? "صفر درهم" : arNombre(dh) + (dh >= 3 && dh <= 10 ? " دراهم" : " درهم");
        if (c > 0) s += " و" + arNombre(c) + (c >= 3 && c <= 10 ? " سنتيمات" : " سنتيم");
        return s;
    }

    static String arNombre(long n) {
        if (n == 0) return "صفر";
        java.util.List<String> parts = new java.util.ArrayList<>();
        long millions = n / 1_000_000L, milliers = n % 1_000_000L / 1000, reste = n % 1000;
        if (millions > 0) parts.add(millions == 1 ? "مليون" : millions == 2 ? "مليونان" : millions <= 10 ? arMoinsDeMille(millions) + " ملايين" : arMoinsDeMille(millions) + " مليونا");
        if (milliers > 0) parts.add(milliers == 1 ? "ألف" : milliers == 2 ? "ألفان" : milliers <= 10 ? arMoinsDeMille(milliers) + " آلاف" : arMoinsDeMille(milliers) + " ألف");
        if (reste > 0) parts.add(arMoinsDeMille(reste));
        return String.join(" و", parts);
    }

    private static String arMoinsDeMille(long n) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int c = (int) (n / 100), r = (int) (n % 100);
        if (c > 0) parts.add(AR_CENTAINES[c]);
        if (r > 0) {
            if (r < 20) parts.add(AR_UNITES[r]);
            else {
                int d = r / 10, u = r % 10;
                if (u == 0) parts.add(AR_DIZAINES[d]);
                else parts.add(AR_UNITES[u] + " و" + AR_DIZAINES[d]);
            }
        }
        return String.join(" و", parts);
    }
}
