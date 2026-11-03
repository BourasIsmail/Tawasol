package ma.entraide.tawassol.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank @Size(max = 100) String login, @NotBlank @Size(max = 200) String password,
                               @Size(max = 150) String appareil) {}
    public record RefreshRequest(@NotBlank String refreshToken, @Size(max = 150) String appareil) {}
    public record LogoutRequest(@NotBlank String refreshToken) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(max = 200) String newPassword) {}
    public record ForgotRequest(@NotBlank @Size(max = 100) String login) {}
    public record ResetRequest(@NotBlank String token, @NotBlank @Size(max = 200) String newPassword) {}
    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds,
                                boolean mustChangePassword, String role) {}
}
