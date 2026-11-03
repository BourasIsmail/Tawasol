package ma.entraide.tawassol.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import ma.entraide.tawassol.security.Role;

@Entity
@Table(name = "utilisateur")
public class Utilisateur {
    @Id @Column(length = 36) private String id;
    @Column(name = "personnel_id") private String personnelId;
    @Column(name = "login") private String login;
    @Column(name = "mot_de_passe") private String motDePasse;
    @Column(name = "role") private String role;
    @Column(name = "est_actif") private boolean estActif;
    @Column(name = "derniere_cnx") private LocalDateTime derniereCnx;
    @Column(name = "doit_changer_mdp") private boolean doitChangerMdp;
    @Column(name = "echecs_connexion") private int echecsConnexion;
    @Column(name = "verrouille_jusqua") private LocalDateTime verrouilleJusqua;

    @PrePersist void ensureId() { if (id == null) id = UUID.randomUUID().toString(); }

    public String getId() { return id; }
    public String getPersonnelId() { return personnelId; }
    public String getLogin() { return login; }
    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String v) { this.motDePasse = v; }
    public Role getRole() { return Role.fromDb(role); }
    public boolean isEstActif() { return estActif; }
    public LocalDateTime getDerniereCnx() { return derniereCnx; }
    public void setDerniereCnx(LocalDateTime v) { this.derniereCnx = v; }
    public boolean isDoitChangerMdp() { return doitChangerMdp; }
    public void setDoitChangerMdp(boolean v) { this.doitChangerMdp = v; }
    public int getEchecsConnexion() { return echecsConnexion; }
    public void setEchecsConnexion(int v) { this.echecsConnexion = v; }
    public LocalDateTime getVerrouilleJusqua() { return verrouilleJusqua; }
    public void setVerrouilleJusqua(LocalDateTime v) { this.verrouilleJusqua = v; }
}
