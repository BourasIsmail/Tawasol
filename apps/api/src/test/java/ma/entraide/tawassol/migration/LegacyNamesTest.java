package ma.entraide.tawassol.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyNamesTest {
    @Test void francaisNomPuisPrenom() {
        var s = LegacyNames.fr("AKRACHE ABDELLAH");
        assertEquals("AKRACHE", s.nom()); assertEquals("ABDELLAH", s.prenom()); assertFalse(s.ambigu());
        var p = LegacyNames.fr("EL KARF FATIMA");
        assertEquals("EL KARF", p.nom()); assertEquals("FATIMA", p.prenom()); assertFalse(p.ambigu());
    }
    @Test void francaisCasAmbigusSontSignales() {
        assertTrue(LegacyNames.fr("ZAAFANE ES SAADIA").ambigu());
        assertTrue(LegacyNames.fr("BEN-TAHIR LALLA MINA BELAID").ambigu());
        assertTrue(LegacyNames.fr("MONONYME").ambigu());
        assertTrue(LegacyNames.fr("").ambigu());
    }
    @Test void arabePrenomPuisNom() {
        var s = LegacyNames.ar("عبد الله عكراش");
        assertEquals("عكراش", s.nom()); assertEquals("عبد الله", s.prenom());
        var p = LegacyNames.ar("فوزية بن رشيد");
        assertEquals("بن رشيد", p.nom()); assertEquals("فوزية", p.prenom()); assertTrue(p.ambigu());
    }
    @Test void leTexteVideOuNulNePlanteJamais() {
        assertTrue(LegacyNames.fr(null).ambigu());
        assertTrue(LegacyNames.ar("   ").ambigu());
    }
}
