package ma.entraide.tawassol.migration;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CnieProtectorTest {
    private static final String CLE = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test void chiffreEtDechiffre() {
        var p = new CnieProtector(CLE);
        byte[] c = p.chiffrer("ab 123-456");
        assertEquals("AB123456", p.dechiffrer(c));
        assertFalse(new String(c, java.nio.charset.StandardCharsets.ISO_8859_1).contains("AB123456"));
    }
    @Test void chaqueChiffrementEstDifferentMaisLEmpreinteEstStable() {
        var p = new CnieProtector(CLE);
        assertFalse(java.util.Arrays.equals(p.chiffrer("AB1"), p.chiffrer("AB1")));
        assertEquals(p.empreinte("ab 123"), p.empreinte("AB123"));
        assertEquals(64, p.empreinte("AB123").length());
        assertNotEquals(p.empreinte("AB123"), new CnieProtector(Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffff".getBytes())).empreinte("AB123"));
    }
    @Test void uneCleTropCourteEstRefusee() {
        assertThrows(IllegalArgumentException.class, () -> new CnieProtector(Base64.getEncoder().encodeToString("court".getBytes())));
    }
    @Test void masqueLaCnie() {
        assertEquals("AB****56", CnieProtector.masquer("AB123456"));
        assertEquals("****", CnieProtector.masquer("A1"));
    }
}
