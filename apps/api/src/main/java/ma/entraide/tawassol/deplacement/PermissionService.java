package ma.entraide.tawassol.deplacement;

import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Permissions fines (mission.*) portées par le rôle de l'utilisateur ; à défaut de rôle fin, le rôle standard de son profil. Lues en base à chaque appel : une révocation est immédiate. */
@Service
public class PermissionService {
    private final JdbcTemplate jdbc;

    public PermissionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean a(AuthenticatedUser u, String permission) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM utilisateur ut
                JOIN role_permission rp ON rp.role_id = COALESCE(ut.role_id, (SELECT r.id FROM role r WHERE r.code = CASE ut.role WHEN 'agent' THEN 'agent_standard' WHEN 'responsable' THEN 'responsable_standard' WHEN 'rh' THEN 'rh_generaliste' ELSE 'admin_technique' END))
                JOIN permission p ON p.id = rp.permission_id
                WHERE ut.id = ? AND ut.est_actif = 1 AND p.code = ?""", Integer.class, u.userId(), permission);
        return n != null && n > 0;
    }

    public void exiger(AuthenticatedUser u, String permission) {
        if (!a(u, permission)) throw new ApiException(HttpStatus.FORBIDDEN, "PERMISSION_REFUSEE", "Permission requise : " + permission);
    }
}
