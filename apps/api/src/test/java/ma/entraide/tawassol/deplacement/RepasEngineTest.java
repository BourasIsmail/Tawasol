package ma.entraide.tawassol.deplacement;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RepasEngineTest {
    private static final RepasEngine.Seuils S = RepasEngine.Seuils.parDefaut();
    private static LocalDateTime d(String s) { return LocalDateTime.parse(s); }

    /** 514 cas calculés par le port fidèle de calculateMeals (scripts/legacy), dont les bornes de créneaux. */
    @Test void reproduitLAlgorithmeDeSuiviRhSurLeJeuDeReference() throws Exception {
        int n = 0;
        try (var r = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/deplacement/repas-reference.csv"), StandardCharsets.UTF_8))) {
            r.readLine();
            for (String l; (l = r.readLine()) != null; n++) {
                String[] c = l.split(";");
                var res = RepasEngine.calculer(d(c[0]), d(c[1]), S);
                assertEquals(Integer.parseInt(c[2]), res.repasTaux1(), "taux 1 pour " + l);
                assertEquals(Integer.parseInt(c[3]), res.repasTaux2(), "taux 2 pour " + l);
            }
        }
        assertTrue(n >= 500);
    }

    @Test void casConnus() {
        assertEquals(1, RepasEngine.calculer(d("2024-05-28T07:00"), d("2024-05-28T16:00"), S).repasTaux1());
        var v = RepasEngine.calculer(d("2024-06-13T08:00"), d("2024-07-03T08:00"), S); // 20 jours
        assertEquals(44, v.repasTaux1()); assertEquals(16, v.repasTaux2());
    }

    @Test void retourAvantOuEgalAuDepartDonneZero() {
        assertEquals(0, RepasEngine.calculer(d("2024-06-13T08:00"), d("2024-06-13T08:00"), S).total());
        assertEquals(0, RepasEngine.calculer(d("2024-06-13T08:00"), d("2024-06-12T08:00"), S).total());
        assertEquals(0, RepasEngine.calculer(d("2024-06-13T08:00"), null, S).total());
    }

    @Test void lesSeuilsSontParametrables() {
        var strict = new RepasEngine.Seuils(new int[]{600, 600, 600}, new int[]{360, 240, 180}, S.petitDejeunerApres(), S.dejeunerJusqua(), S.dinerJusqua(), 15);
        assertTrue(RepasEngine.calculer(d("2024-06-13T08:00"), d("2024-06-13T20:00"), strict).total()
                < RepasEngine.calculer(d("2024-06-13T08:00"), d("2024-06-13T20:00"), S).total());
        var court = new RepasEngine.Seuils(S.presence(), S.intervalle(), S.petitDejeunerApres(), S.dejeunerJusqua(), S.dinerJusqua(), 2);
        var r = RepasEngine.calculer(d("2024-06-13T08:00"), d("2024-06-23T08:00"), court);
        assertTrue(r.repasTaux2() > 0, "bascule au second taux après 2 jours");
    }
}
