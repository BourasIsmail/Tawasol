package ma.entraide.tawassol.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {
    @Id @Column(length = 36) private String id;
    @Column(name = "utilisateur_id") private String utilisateurId;
    @Column(name = "token_hash") private String tokenHash;
    @Column(name = "famille") private String famille;
    @Column(name = "appareil") private String appareil;
    @Column(name = "ip") private String ip;
    @Column(name = "expire_le") private LocalDateTime expireLe;
    @Column(name = "revoque") private boolean revoque;
    @Column(name = "cree_le") private LocalDateTime creeLe;

    protected RefreshToken() {}

    public RefreshToken(String id, String utilisateurId, String tokenHash, String famille, String appareil, String ip,
                        LocalDateTime expireLe, LocalDateTime creeLe) {
        this.id = id; this.utilisateurId = utilisateurId; this.tokenHash = tokenHash; this.famille = famille;
        this.appareil = appareil; this.ip = ip; this.expireLe = expireLe; this.creeLe = creeLe;
    }

    public String getId() { return id; }
    public String getUtilisateurId() { return utilisateurId; }
    public String getFamille() { return famille; }
    public String getAppareil() { return appareil; }
    public LocalDateTime getExpireLe() { return expireLe; }
    public boolean isRevoque() { return revoque; }
    public void revoke() { this.revoque = true; }
}
