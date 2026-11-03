package ma.entraide.tawassol.notification;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public NotificationService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void notifier(String utilisateurId, String type, String titre, String message, String lien) {
        if (utilisateurId == null) return;
        jdbc.update("INSERT INTO notification (id, utilisateur_id, type, titre, message, lien, cree_le) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), utilisateurId, type, titre, message, lien, LocalDateTime.now(clock));
    }

    /** Notifie le compte lié à un agent, s'il en a un. */
    public void notifierAgent(String personnelId, String type, String titre, String message, String lien) {
        jdbc.queryForList("SELECT id FROM utilisateur WHERE personnel_id = ? AND est_actif = 1", String.class, personnelId)
                .forEach(u -> notifier(u, type, titre, message, lien));
    }

    /** Notifie le responsable et, pendant leur suppléance, ceux qui le remplacent. */
    public void notifierResponsable(String responsablePersonnelId, String type, String titre, String message, String lien) {
        if (responsablePersonnelId == null) return;
        notifierAgent(responsablePersonnelId, type, titre, message, lien);
        java.time.LocalDate today = java.time.LocalDate.now(clock);
        jdbc.queryForList("SELECT suppleant_id FROM suppleance WHERE responsable_id = ? AND est_actif = 1 AND ? BETWEEN date_debut AND date_fin",
                String.class, responsablePersonnelId, today).forEach(s -> notifierAgent(s, type, titre, message, lien));
    }

    public List<Map<String, Object>> lister(String utilisateurId, boolean nonLuesSeulement, int limit) {
        return jdbc.queryForList("SELECT id, type, titre, message, lien, lu, cree_le AS creeLe FROM notification WHERE utilisateur_id = ? "
                + (nonLuesSeulement ? "AND lu = 0 " : "") + "ORDER BY cree_le DESC, id LIMIT ?", utilisateurId, limit);
    }

    public int nonLues(String utilisateurId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE utilisateur_id = ? AND lu = 0", Integer.class, utilisateurId);
    }

    public boolean marquerLue(String utilisateurId, String id) {
        return jdbc.update("UPDATE notification SET lu = 1 WHERE id = ? AND utilisateur_id = ?", id, utilisateurId) > 0;
    }

    public int toutMarquerLu(String utilisateurId) {
        return jdbc.update("UPDATE notification SET lu = 1 WHERE utilisateur_id = ? AND lu = 0", utilisateurId);
    }
}
