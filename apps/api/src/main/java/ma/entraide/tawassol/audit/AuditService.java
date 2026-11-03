package ma.entraide.tawassol.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Écrit le journal d'audit dans sa propre transaction : une trace survit même si l'opération métier échoue.
 * Ne jamais y mettre de mot de passe, de jeton ou de montant nominatif.
 */
@Service
public class AuditService {
    private final JournalAuditRepository repo;
    private final Clock clock;

    public AuditService(JournalAuditRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String userId, String action, String entite, String entiteId, String avantJson, String apresJson) {
        String ip = null, ua = null;
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest r = sra.getRequest();
            ip = r.getRemoteAddr();
            ua = r.getHeader("User-Agent");
            if (ua != null && ua.length() > 250) ua = ua.substring(0, 250);
        }
        repo.save(new JournalAudit(UUID.randomUUID().toString(), userId, action, entite, entiteId, avantJson, apresJson,
                ip, ua, LocalDateTime.now(clock)));
    }

    public void record(String userId, String action) { record(userId, action, null, null, null, null); }
}
