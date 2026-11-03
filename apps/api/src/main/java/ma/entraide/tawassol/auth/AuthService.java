package ma.entraide.tawassol.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.auth.AuthDtos.TokenResponse;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.config.TawassolProperties;
import ma.entraide.tawassol.security.JwtService;
import ma.entraide.tawassol.security.PasswordPolicy;
import ma.entraide.tawassol.security.PasswordService;
import ma.entraide.tawassol.user.RefreshToken;
import ma.entraide.tawassol.user.RefreshTokenRepository;
import ma.entraide.tawassol.user.Utilisateur;
import ma.entraide.tawassol.user.UtilisateurRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UtilisateurRepository users;
    private final RefreshTokenRepository tokens;
    private final PasswordService passwords;
    private final JwtService jwt;
    private final AuditService audit;
    private final TawassolProperties props;
    private final Clock clock;

    public AuthService(UtilisateurRepository users, RefreshTokenRepository tokens, PasswordService passwords, JwtService jwt,
                       AuditService audit, TawassolProperties props, Clock clock) {
        this.users = users; this.tokens = tokens; this.passwords = passwords; this.jwt = jwt;
        this.audit = audit; this.props = props; this.clock = clock;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(String login, String password, String appareil, String ip) {
        LocalDateTime now = LocalDateTime.now(clock);
        Utilisateur u = users.findByLogin(login).orElse(null);
        if (u == null || !u.isEstActif()) {
            passwords.burn(password);
            audit.record(null, "LOGIN_FAIL", "utilisateur", null, null, null);
            throw badCredentials();
        }
        if (u.getVerrouilleJusqua() != null && u.getVerrouilleJusqua().isAfter(now)) {
            audit.record(u.getId(), "LOGIN_LOCKED", "utilisateur", u.getId(), null, null);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_TEMPORARILY_LOCKED",
                    "Compte temporairement verrouillé. Réessayez plus tard.");
        }
        if (!passwords.matches(password, u.getMotDePasse())) {
            int echecs = u.getEchecsConnexion() + 1;
            if (echecs >= props.security().maxFailedAttempts()) {
                u.setVerrouilleJusqua(now.plus(props.security().lockDuration()));
                u.setEchecsConnexion(0);
                audit.record(u.getId(), "ACCOUNT_LOCKED", "utilisateur", u.getId(), null, null);
            } else {
                u.setEchecsConnexion(echecs);
            }
            audit.record(u.getId(), "LOGIN_FAIL", "utilisateur", u.getId(), null, null);
            throw badCredentials();
        }
        u.setEchecsConnexion(0);
        u.setVerrouilleJusqua(null);
        u.setDerniereCnx(now);
        if (passwords.needsUpgrade(u.getMotDePasse())) u.setMotDePasse(passwords.hash(password));
        audit.record(u.getId(), "LOGIN_OK", "utilisateur", u.getId(), null, null);
        return issue(u, UUID.randomUUID().toString(), appareil, ip);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String rawToken, String appareil, String ip) {
        RefreshToken t = tokens.findByTokenHash(hash(rawToken)).orElseThrow(AuthService::invalidRefresh);
        if (t.isRevoque()) {
            // Réutilisation d'un jeton déjà consommé : vol probable, on révoque toute la famille.
            tokens.revokeFamily(t.getFamille());
            audit.record(t.getUtilisateurId(), "REFRESH_REUSE_DETECTED", "utilisateur", t.getUtilisateurId(), null, null);
            throw invalidRefresh();
        }
        if (t.getExpireLe().isBefore(LocalDateTime.now(clock))) throw invalidRefresh();
        Utilisateur u = users.findById(t.getUtilisateurId()).filter(Utilisateur::isEstActif).orElseThrow(AuthService::invalidRefresh);
        t.revoke();
        tokens.save(t);
        return issue(u, t.getFamille(), appareil != null ? appareil : t.getAppareil(), ip);
    }

    @Transactional
    public void logout(String rawToken) {
        tokens.findByTokenHash(hash(rawToken)).ifPresent(t -> {
            t.revoke();
            tokens.save(t);
            audit.record(t.getUtilisateurId(), "LOGOUT", "utilisateur", t.getUtilisateurId(), null, null);
        });
    }

    @Transactional
    public void logoutAll(String userId) {
        tokens.revokeAllForUser(userId);
        audit.record(userId, "LOGOUT_ALL", "utilisateur", userId, null, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse changePassword(String userId, String current, String next, String ip) {
        Utilisateur u = users.findById(userId).orElseThrow(ApiException::notFound);
        if (!passwords.matches(current, u.getMotDePasse())) {
            audit.record(userId, "PASSWORD_CHANGE_FAIL", "utilisateur", userId, null, null);
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "Mot de passe actuel incorrect");
        }
        List<String> v = PasswordPolicy.violations(next, props.security().passwordMinLength());
        if (!v.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY", "Mot de passe non conforme : " + String.join(", ", v));
        }
        if (next.equals(current)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY", "Le nouveau mot de passe doit être différent de l'ancien");
        }
        u.setMotDePasse(passwords.hash(next));
        u.setDoitChangerMdp(false);
        tokens.revokeAllForUser(userId);
        audit.record(userId, "PASSWORD_CHANGED", "utilisateur", userId, null, null);
        return issue(u, UUID.randomUUID().toString(), null, ip);
    }

    private TokenResponse issue(Utilisateur u, String famille, String appareil, String ip) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        LocalDateTime now = LocalDateTime.now(clock);
        tokens.save(new RefreshToken(UUID.randomUUID().toString(), u.getId(), hash(refresh), famille, appareil, ip,
                now.plus(props.security().refreshTtl()), now));
        return new TokenResponse(jwt.issueAccessToken(u.getId()), refresh, props.security().accessTtl().toSeconds(),
                u.isDoitChangerMdp(), u.getRole().db());
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException badCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Identifiant ou mot de passe incorrect");
    }

    private static ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Session expirée, reconnectez-vous");
    }
}
