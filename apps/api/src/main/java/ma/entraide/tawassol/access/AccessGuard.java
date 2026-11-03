package ma.entraide.tawassol.access;

import java.util.Set;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import ma.entraide.tawassol.security.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Point unique de décision d'accès aux données des agents (rôle ET périmètre).
 * Aucun contrôleur ne doit refaire ce calcul : il demande un Scope ici.
 */
@Service
public class AccessGuard {
    public enum Level { DETAIL, SYNTHESE }

    private final PerimeterService perimeters;

    public AccessGuard(PerimeterService perimeters) {
        this.perimeters = perimeters;
    }

    public Scope personnelScope(AuthenticatedUser u) {
        return switch (u.role()) {
            case RH -> new Scope(true, Set.of(), u.personnelId());
            case RESPONSABLE -> new Scope(false, perimeters.structureIdsInPerimeter(u.personnelId(), true), u.personnelId());
            case AGENT -> new Scope(false, perimeters.structureIdsInPerimeter(u.personnelId(), false), u.personnelId());
            case ADMIN -> throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                    "Les comptes techniques n'ont pas accès aux données des agents");
        };
    }

    /** Niveau de détail : complet pour la RH et pour l'agent lui-même, synthétique (sans données sensibles) pour un responsable. */
    public Level levelFor(AuthenticatedUser u, String targetPersonnelId) {
        if (u.role() == Role.RH || (u.personnelId() != null && u.personnelId().equals(targetPersonnelId))) return Level.DETAIL;
        return Level.SYNTHESE;
    }

    public boolean isSelf(AuthenticatedUser u, String targetPersonnelId) {
        return u.personnelId() != null && u.personnelId().equals(targetPersonnelId);
    }
}
