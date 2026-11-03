package ma.entraide.tawassol.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ma.entraide.tawassol.auth.AuthDtos.*;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import ma.entraide.tawassol.security.LoginRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final LoginRateLimiter limiter;
    private final PasswordResetService reset;

    public AuthController(AuthService auth, LoginRateLimiter limiter, PasswordResetService reset) {
        this.auth = auth;
        this.limiter = limiter;
        this.reset = reset;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        if (!limiter.allow(http.getRemoteAddr())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Trop de tentatives, réessayez dans une minute");
        }
        return auth.login(req.login().trim(), req.password(), req.appareil(), http.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return auth.refresh(req.refreshToken(), req.appareil(), http.getRemoteAddr());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest req) {
        auth.logout(req.refreshToken());
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal AuthenticatedUser user) {
        auth.logoutAll(user.userId());
    }

    @PostMapping("/change-password")
    public TokenResponse changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody ChangePasswordRequest req, HttpServletRequest http) {
        return auth.changePassword(user.userId(), req.currentPassword(), req.newPassword(), http.getRemoteAddr());
    }

    /** Réponse identique que le compte existe ou non. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgot(@Valid @RequestBody ForgotRequest req, HttpServletRequest http) {
        if (!limiter.allow("forgot|" + http.getRemoteAddr())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Trop de tentatives, réessayez dans une minute");
        }
        reset.request(req.login().trim());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetRequest req, HttpServletRequest http) {
        if (!limiter.allow("reset|" + http.getRemoteAddr())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Trop de tentatives, réessayez dans une minute");
        }
        reset.reset(req.token(), req.newPassword());
    }
}
