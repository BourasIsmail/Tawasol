package ma.entraide.tawassol.migration;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** Chiffrement (AES-256-GCM) et empreinte (HMAC-SHA-256) de la CNIE. La clé n'est jamais journalisée. */
public final class CnieProtector {
    private final SecretKeySpec aes;
    private final SecretKeySpec hmac;
    private final SecureRandom random = new SecureRandom();

    /** @param base64Key 32 octets en base64 ; null = clé éphémère (essai à blanc uniquement). */
    public CnieProtector(String base64Key) {
        byte[] k;
        if (base64Key == null || base64Key.isBlank()) { k = new byte[32]; random.nextBytes(k); }
        else k = Base64.getDecoder().decode(base64Key.trim());
        if (k.length != 32) throw new IllegalArgumentException("La clé CNIE doit faire 32 octets (base64).");
        this.aes = new SecretKeySpec(k, "AES");
        this.hmac = new SecretKeySpec(derive(k, "empreinte"), "HmacSHA256");
    }

    public static String normaliser(String cnie) { return cnie == null ? null : cnie.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT); }

    public byte[] chiffrer(String cnie) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(normaliser(cnie).getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length); System.arraycopy(ct, 0, out, iv.length, ct.length);
            return out;
        } catch (Exception e) { throw new IllegalStateException("Chiffrement impossible", e); }
    }

    public String dechiffrer(byte[] data) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aes, new GCMParameterSpec(128, data, 0, 12));
            return new String(c.doFinal(data, 12, data.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Déchiffrement impossible", e); }
    }

    public String empreinte(String cnie) {
        try {
            Mac m = Mac.getInstance("HmacSHA256"); m.init(hmac);
            return HexFormat.of().formatHex(m.doFinal(normaliser(cnie).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static byte[] derive(byte[] k, String label) {
        try { Mac m = Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(k, "HmacSHA256")); return m.doFinal(label.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Masque pour tout affichage : les deux premiers et les deux derniers caractères seulement. */
    public static String masquer(String cnie) {
        String n = normaliser(cnie);
        if (n == null || n.length() < 5) return "****";
        return n.substring(0, 2) + "*".repeat(n.length() - 4) + n.substring(n.length() - 2);
    }
}
