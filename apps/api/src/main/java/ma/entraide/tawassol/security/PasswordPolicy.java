package ma.entraide.tawassol.security;

import java.util.ArrayList;
import java.util.List;

/** Politique de mot de passe (valeurs par défaut à valider avec la DGSSI / loi 05-20). */
public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static List<String> violations(String password, int minLength) {
        List<String> v = new ArrayList<>();
        if (password == null || password.length() < minLength) v.add("LENGTH_MIN_" + minLength);
        if (password == null) return v;
        if (password.chars().noneMatch(Character::isUpperCase)) v.add("UPPERCASE");
        if (password.chars().noneMatch(Character::isLowerCase)) v.add("LOWERCASE");
        if (password.chars().noneMatch(Character::isDigit)) v.add("DIGIT");
        if (password.chars().allMatch(Character::isLetterOrDigit)) v.add("SYMBOL");
        return v;
    }
}
