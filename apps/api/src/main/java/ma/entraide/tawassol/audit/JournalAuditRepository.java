package ma.entraide.tawassol.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalAuditRepository extends JpaRepository<JournalAudit, String> {
    List<JournalAudit> findByAction(String action);
}
