package ma.entraide.tawassol.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.config.TawassolProperties;
import ma.entraide.tawassol.mail.MailService;
import ma.entraide.tawassol.param.ParametreService;
import ma.entraide.tawassol.security.PasswordPolicy;
import ma.entraide.tawassol.security.PasswordService;
import ma.entraide.tawassol.user.RefreshTokenRepository;
import ma.entraide.tawassol.user.Utilisateur;
import ma.entraide.tawassol.user.UtilisateurRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mot de passe oublié : lien à usage unique et courte durée envoyé à l'e-mail de la fiche. La réponse ne révèle jamais si le compte existe. */
@Service
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UtilisateurRepository users;
    private final RefreshTokenRepository tokens;
    private final JdbcTemplate jdbc;
    private final PasswordService passwords;
    private final MailService mail;
    private final ParametreService params;
    private final AuditService audit;
    private final TawassolProperties props;
    private final Clock clock;

    public PasswordResetService(UtilisateurRepository users, RefreshTokenRepository tokens, JdbcTemplate jdbc, PasswordService passwords, MailService mail,
                                ParametreService params, AuditService audit, TawassolProperties props, Clock clock) {
        this.users = users; this.tokens = tokens; this.jdbc = jdbc; this.passwords = passwords; this.mail = mail;
        this.params = params; this.audit = audit; this.props = props; this.clock = clock;
    }

    @Transactional
    public void request(String login) {
        Utilisateur u = users.findByLogin(login).filter(Utilisateur::isEstActif).orElse(null);
        if (u == null || u.getPersonnelId() == null) return;
        List<String> emails = jdbc.queryForList("SELECT email FROM personnel WHERE id = ? AND email IS NOT NULL AND email <> ''", String.class, u.getPersonnelId());
        if (emails.isEmpty()) return;
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        LocalDateTime now = LocalDateTime.now(clock);
        int minutes = params.integer("securite.reset_mdp.duree_minutes", LocalDate.now(clock), 30);
        jdbc.update("INSERT INTO reset_mdp_token (id, utilisateur_id, token_hash, expire_le, cree_le) VALUES (?,?,?,?,?)",
                UUID.randomUUID().toString(), u.getId(), AuthService.hash(token), now.plusMinutes(minutes), now);
        String base = props.webUrl() == null ? "http://localhost:3000" : props.webUrl();
        mail.send(emails.get(0), "TAWASSOL : réinitialisation du mot de passe / إعادة تعيين كلمة المرور",
                "Pour choisir un nouveau mot de passe (valable " + minutes + " minutes) :\n" + base + "/fr/reset?token=" + token + "\n\n"
                        + "لاختيار كلمة مرور جديدة (صالح لمدة " + minutes + " دقيقة):\n" + base + "/ar/reset?token=" + token + "\n\n"
                        + "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message. / إذا لم تطلبوا ذلك فتجاهلوا هذه الرسالة.");
        audit.record(u.getId(), "PASSWORD_RESET_REQUESTED", "utilisateur", u.getId(), null, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void reset(String token, String newPassword) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<String[]> rows = jdbc.query("SELECT id, utilisateur_id FROM reset_mdp_token WHERE token_hash = ? AND utilise_le IS NULL AND expire_le > ?",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, AuthService.hash(token), now);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "Lien invalide ou expiré");
        List<String> v = PasswordPolicy.violations(newPassword, props.security().passwordMinLength());
        if (!v.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY", "Mot de passe non conforme : " + String.join(", ", v));
        Utilisateur u = users.findById(rows.get(0)[1]).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "Lien invalide ou expiré"));
        u.setMotDePasse(passwords.hash(newPassword));
        u.setDoitChangerMdp(false);
        u.setEchecsConnexion(0);
        u.setVerrouilleJusqua(null);
        jdbc.update("UPDATE reset_mdp_token SET utilise_le = ? WHERE utilisateur_id = ? AND utilise_le IS NULL", now, u.getId());
        tokens.revokeAllForUser(u.getId());
        audit.record(u.getId(), "PASSWORD_RESET_DONE", "utilisateur", u.getId(), null, null);
    }
}
