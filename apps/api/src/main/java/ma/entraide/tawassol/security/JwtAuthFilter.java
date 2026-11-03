package ma.entraide.tawassol.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import ma.entraide.tawassol.common.ApiError;
import ma.entraide.tawassol.user.Utilisateur;
import ma.entraide.tawassol.user.UtilisateurRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentifie chaque requête. Le jeton ne porte que l'identifiant : rôle, état du compte et obligation de changer
 * le mot de passe sont relus en base, donc une désactivation ou un changement de rôle est immédiat.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED_WHEN_PASSWORD_CHANGE_REQUIRED =
            Set.of("/api/v1/auth/change-password", "/api/v1/auth/logout", "/api/v1/auth/logout-all", "/api/v1/me");

    private final JwtService jwt;
    private final UtilisateurRepository users;
    private final ObjectMapper mapper;

    public JwtAuthFilter(JwtService jwt, UtilisateurRepository users, ObjectMapper mapper) {
        this.jwt = jwt;
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Optional<Utilisateur> user = jwt.parseSubject(header.substring(7)).flatMap(users::findById).filter(Utilisateur::isEstActif);
            if (user.isPresent()) {
                Utilisateur u = user.get();
                if (u.isDoitChangerMdp() && !ALLOWED_WHEN_PASSWORD_CHANGE_REQUIRED.contains(req.getRequestURI())) {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    mapper.writeValue(res.getOutputStream(), ApiError.of(403, "PASSWORD_CHANGE_REQUIRED",
                            "Vous devez changer votre mot de passe avant de continuer"));
                    return;
                }
                AuthenticatedUser principal = new AuthenticatedUser(u.getId(), u.getPersonnelId(), u.getRole(), u.isDoitChangerMdp());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()))));
            }
        }
        chain.doFilter(req, res);
    }
}
