package ma.entraide.tawassol.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** argon2id pour les nouveaux mots de passe ; bcrypt accepté en lecture puis migré à la première connexion. */
@Service
public class PasswordService {
    private final Argon2PasswordEncoder argon = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final String dummyHash = argon.encode("dummy-password-for-timing");

    public String hash(String raw) { return argon.encode(raw); }

    public boolean matches(String raw, String hash) {
        if (hash == null) return false;
        try {
            if (hash.startsWith("$argon2")) return argon.matches(raw, hash);
            if (hash.startsWith("$2")) return bcrypt.matches(raw, hash);
        } catch (RuntimeException ignored) {
            // hash invalide (ex. valeur factice du dump) : jamais valide
        }
        return false;
    }

    /** Dépense le même temps qu'une vérification réelle, pour ne pas révéler si un login existe. */
    public void burn(String raw) { argon.matches(raw, dummyHash); }

    public boolean needsUpgrade(String hash) { return hash != null && hash.startsWith("$2"); }
}
