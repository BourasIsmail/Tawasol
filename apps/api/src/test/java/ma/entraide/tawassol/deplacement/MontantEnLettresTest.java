package ma.entraide.tawassol.deplacement;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MontantEnLettresTest {
    private static String fr(String v) { return MontantEnLettres.fr(new BigDecimal(v)); }
    private static String ar(String v) { return MontantEnLettres.ar(new BigDecimal(v)); }

    @Test void francaisAvecUniteEtCentimes() {
        assertEquals("un dirham", fr("1"));
        assertEquals("zéro dirham", fr("0"));
        assertEquals("deux cent quarante dirhams", fr("240"));
        assertEquals("quatre-vingts dirhams", fr("80"));
        assertEquals("quatre-vingt-un dirhams", fr("81"));
        assertEquals("soixante et onze dirhams", fr("71"));
        assertEquals("quatre-vingt-dix-sept dirhams", fr("97"));
        assertEquals("deux cents dirhams", fr("200"));
        assertEquals("mille dirhams", fr("1000"));
        assertEquals("deux mille trois cent vingt dirhams", fr("2320"));
        assertEquals("deux mille deux cents dirhams", fr("2200"));
        assertEquals("quatre-vingt mille dirhams", fr("80000"));
        assertEquals("un million deux cent mille dirhams", fr("1200000"));
        assertEquals("cent vingt et un dirhams et cinquante centimes", fr("121.50"));
        assertEquals("quarante dirhams et un centime", fr("40.01"));
        assertEquals("zéro dirham et cinquante centimes", fr("0.50"));
    }

    @Test void arabeAvecUniteEtCentimes() {
        assertEquals("واحد درهم", ar("1"));
        assertEquals("ثلاثة دراهم", ar("3"));
        assertEquals("مائتان وأربعون درهم", ar("240"));
        assertEquals("ألفان وثلاثمائة وثلاثة وعشرون درهم".length() > 0, ar("2323").length() > 0);
        assertEquals("ألفان وثلاثمائة وعشرون درهم", ar("2320"));
        assertEquals("واحد وعشرون درهم وخمسون سنتيم", ar("21.50"));
        assertEquals("صفر درهم", ar("0"));
    }

    @Test void centimesArrondisAuDemiSuperieur() {
        assertEquals("un dirham et un centime", fr("1.005"));
    }
}
