package ma.entraide.tawassol.deplacement;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Décompte des repas d'une mission (algorithme de Suivi RH, calculateMeals, avec seuils paramétrables et datés).
 * Fonction pure : aucune lecture de base, entièrement testable. Les seuils viennent de bareme_repas et des paramètres
 * mission.repas.* à la date de départ ; ils sont « à valider » par la RH et le contrôle financier.
 */
public final class RepasEngine {
    /** Ordre du cycle : petit-déjeuner (ftour), déjeuner (ghda), dîner (isha). */
    public static final int PETIT_DEJEUNER = 0, DEJEUNER = 1, DINER = 2;

    /** presence[i] : minutes de présence nécessaires ; intervalle[i] : minutes avant le repas suivant. */
    public record Seuils(int[] presence, int[] intervalle, LocalTime petitDejeunerApres, LocalTime dejeunerJusqua, LocalTime dinerJusqua, int seuilBasculeJours) {
        public static Seuils parDefaut() {
            return new Seuils(new int[]{300, 180, 180}, new int[]{360, 240, 180}, LocalTime.of(18, 0), LocalTime.of(11, 0), LocalTime.of(18, 0), 15);
        }
    }

    /** repasTaux1 : repas payés au premier taux ; repasTaux2 : au second taux (après le seuil de bascule). */
    public record Resultat(int repasTaux1, int repasTaux2, int premiereJournee, int total) {}

    private RepasEngine() {}

    public static Resultat calculer(LocalDateTime depart, LocalDateTime retour, Seuils s) {
        if (depart == null || retour == null || !retour.isAfter(depart)) return new Resultat(0, 0, 0, 0);
        long total = Duration.between(depart, retour).toMinutes();
        int t = depart.getHour() * 60 + depart.getMinute();
        int petitApres = s.petitDejeunerApres().toSecondOfDay() / 60, dej = s.dejeunerJusqua().toSecondOfDay() / 60, din = s.dinerJusqua().toSecondOfDay() / 60;

        int premier;
        if (t == 0 || t > petitApres) {
            premier = PETIT_DEJEUNER;
            // jusqu'à minuit
            LocalDateTime minuit = depart.toLocalDate().atStartOfDay().plusDays(t > 0 ? 1 : 0);
            total -= Math.abs(Duration.between(depart, minuit).toMinutes());
        } else if (t <= dej) {
            premier = DEJEUNER;
            total -= Math.abs(Duration.between(depart, depart.toLocalDate().atTime(s.dejeunerJusqua())).toMinutes());
        } else {
            premier = DINER;
            total -= Math.abs(Duration.between(depart, depart.toLocalDate().atTime(s.dinerJusqua())).toMinutes());
        }

        // repas possibles le premier jour : du premier repas à la fin du cycle de trois
        int premiereJournee = 0, cumul = 0;
        for (int k = premier, i = 0; k <= DINER; k++, i++) {
            cumul += s.presence()[k];
            if (total - cumul >= 0) premiereJournee++;
            cumul += s.intervalle()[k];
        }

        // repas réellement décomptés sur la durée
        int cur = premier, repas = 0;
        long restant = total;
        while (restant > 0) {
            if (restant - s.presence()[cur] >= 0) {
                repas++;
                restant -= s.presence()[cur];
                restant -= s.intervalle()[cur];
                cur = (cur + 1) % 3;
            } else restant = 0;
        }

        int t1 = 0, t2 = 0;
        if (repas > 0) {
            int jour = 1, rest = repas;
            while (rest > 0) {
                if (jour == 1) { int k = Math.min(premiereJournee, rest); t1 += k; rest -= premiereJournee; }
                else if (jour <= s.seuilBasculeJours()) { int k = Math.min(rest, 3); t1 += k; rest -= k; }
                else { int k = Math.min(rest, 3); t2 += k; rest -= k; }
                jour++;
                if (jour > 10000) throw new IllegalStateException("Décompte des repas : boucle non terminée");
            }
        }
        return new Resultat(t1, t2, premiereJournee, repas);
    }
}
