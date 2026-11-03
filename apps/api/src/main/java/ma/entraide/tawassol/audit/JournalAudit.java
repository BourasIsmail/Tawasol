package ma.entraide.tawassol.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "journal_audit")
public class JournalAudit {
    @Id @Column(length = 36) private String id;
    @Column(name = "utilisateur_id") private String utilisateurId;
    @Column(name = "action") private String action;
    @Column(name = "entite") private String entite;
    @Column(name = "entite_id") private String entiteId;
    @Column(name = "avant", columnDefinition = "json") private String avant;
    @Column(name = "apres", columnDefinition = "json") private String apres;
    @Column(name = "ip") private String ip;
    @Column(name = "user_agent") private String userAgent;
    @Column(name = "cree_le") private LocalDateTime creeLe;

    protected JournalAudit() {}

    public JournalAudit(String id, String utilisateurId, String action, String entite, String entiteId, String avant,
                        String apres, String ip, String userAgent, LocalDateTime creeLe) {
        this.id = id; this.utilisateurId = utilisateurId; this.action = action; this.entite = entite;
        this.entiteId = entiteId; this.avant = avant; this.apres = apres; this.ip = ip;
        this.userAgent = userAgent; this.creeLe = creeLe;
    }

    public String getAction() { return action; }
    public String getUtilisateurId() { return utilisateurId; }
}
