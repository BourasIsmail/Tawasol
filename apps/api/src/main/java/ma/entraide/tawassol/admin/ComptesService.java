package ma.entraide.tawassol.admin;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.auth.PasswordResetService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import ma.entraide.tawassol.security.PasswordService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Création de comptes par l'administrateur (tous profils) ou la RH (profils agent et responsable seulement).
 * Le mot de passe est généré (jamais choisi par la personne qui crée) et doit être changé à la première connexion.
 * Si la fiche a une adresse e-mail, le lien de choix du mot de passe part par e-mail ; sinon le mot de passe initial
 * est retourné une seule fois à la personne qui crée le compte.
 */
@Service
public class ComptesService {
    public record Creation(String personnelId, String login, String roleId) {}
    public record Resultat(String id, String login, boolean lienEnvoye, String motDePasseInitial) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String MAJ = "ABCDEFGHJKLMNPQRSTUVWXYZ", MIN = "abcdefghijkmnpqrstuvwxyz", NUM = "23456789", SYM = "!@#$%*+-=?";

    private final JdbcTemplate jdbc;
    private final PasswordService passwords;
    private final PasswordResetService reset;
    private final AuditService audit;

    public ComptesService(JdbcTemplate jdbc, PasswordService passwords, PasswordResetService reset, AuditService audit) {
        this.jdbc = jdbc; this.passwords = passwords; this.reset = reset; this.audit = audit;
    }

    /** Agents actifs qui n'ont pas encore de compte : identité minimale seulement (aucune donnée de carrière ni de paie). */
    public List<Map<String, Object>> candidats(String q) {
        String like = "%" + (q == null ? "" : q.trim()) + "%";
        return jdbc.queryForList("""
                SELECT p.id, p.matricule, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr,
                       s.nom_fr AS structureFr, s.nom_ar AS structureAr, (p.email IS NOT NULL AND p.email <> '') AS aEmail
                FROM personnel p LEFT JOIN structure s ON s.id = p.structure_id
                WHERE p.est_actif = 1 AND NOT EXISTS (SELECT 1 FROM utilisateur u WHERE u.personnel_id = p.id)
                  AND (p.matricule LIKE ? OR p.nom_fr LIKE ? OR p.prenom_fr LIKE ?)
                ORDER BY p.nom_fr, p.prenom_fr LIMIT 20""", like, like, like);
    }

    @Transactional
    public Resultat creer(AuthenticatedUser acteur, Creation c, boolean parAdmin) {
        if (c.roleId() == null || c.roleId().isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ROLE_INCONNU", "Rôle obligatoire");
        List<Map<String, Object>> r = jdbc.queryForList("SELECT profil, est_actif FROM role WHERE id = ?", c.roleId());
        if (r.isEmpty() || !Boolean.TRUE.equals(bool(r.get(0).get("est_actif")))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ROLE_INCONNU", "Rôle inconnu ou inactif");
        String profil = String.valueOf(r.get(0).get("profil"));
        if (!parAdmin && !List.of("agent", "responsable").contains(profil))
            throw new ApiException(HttpStatus.FORBIDDEN, "PROFIL_INTERDIT", "La RH ne peut créer que des comptes agent ou responsable");
        String personnelId = c.personnelId() == null || c.personnelId().isBlank() ? null : c.personnelId();
        if (personnelId == null && !"admin".equals(profil)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PERSONNEL_OBLIGATOIRE", "Un agent doit être rattaché au compte");
        String matricule = null;
        if (personnelId != null) {
            List<Map<String, Object>> p = jdbc.queryForList("SELECT matricule, est_actif FROM personnel WHERE id = ?", personnelId);
            if (p.isEmpty() || !Boolean.TRUE.equals(bool(p.get(0).get("est_actif")))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PERSONNEL_INTROUVABLE", "Agent introuvable ou inactif");
            matricule = String.valueOf(p.get(0).get("matricule"));
            Integer deja = jdbc.queryForObject("SELECT COUNT(*) FROM utilisateur WHERE personnel_id = ?", Integer.class, personnelId);
            if (deja != null && deja > 0) throw new ApiException(HttpStatus.CONFLICT, "COMPTE_EXISTANT", "Cet agent a déjà un compte");
        }
        String login = c.login() == null || c.login().isBlank() ? matricule : c.login().trim();
        if (login == null || !login.matches("[A-Za-z0-9._-]{3,50}")) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LOGIN_INVALIDE", "Identifiant invalide (3 à 50 caractères : lettres, chiffres, . _ -)");
        Integer pris = jdbc.queryForObject("SELECT COUNT(*) FROM utilisateur WHERE login = ?", Integer.class, login);
        if (pris != null && pris > 0) throw new ApiException(HttpStatus.CONFLICT, "LOGIN_PRIS", "Cet identifiant existe déjà");
        String mdp = generer();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO utilisateur (id, personnel_id, login, mot_de_passe, role, role_id, est_actif, doit_changer_mdp) VALUES (?,?,?,?,?,?,1,1)", id, personnelId, login, passwords.hash(mdp), profil, c.roleId());
        audit.record(acteur.userId(), "COMPTE_CREE", "utilisateur", id, null, "{\"profil\":\"" + profil + "\",\"parAdmin\":" + parAdmin + "}");
        boolean aEmail = personnelId != null && !jdbc.queryForList("SELECT 1 FROM personnel WHERE id = ? AND email IS NOT NULL AND email <> ''", personnelId).isEmpty();
        if (aEmail) {
            reset.request(login);
            return new Resultat(id, login, true, null);
        }
        return new Resultat(id, login, false, mdp);
    }

    private static Boolean bool(Object o) { return o instanceof Boolean b ? b : o instanceof Number n ? n.intValue() != 0 : null; }

    static String generer() {
        StringBuilder sb = new StringBuilder();
        String tout = MAJ + MIN + NUM + SYM;
        sb.append(MAJ.charAt(RANDOM.nextInt(MAJ.length()))).append(MIN.charAt(RANDOM.nextInt(MIN.length()))).append(NUM.charAt(RANDOM.nextInt(NUM.length()))).append(SYM.charAt(RANDOM.nextInt(SYM.length())));
        while (sb.length() < 16) sb.append(tout.charAt(RANDOM.nextInt(tout.length())));
        char[] a = sb.toString().toCharArray();
        for (int i = a.length - 1; i > 0; i--) { int j = RANDOM.nextInt(i + 1); char t = a[i]; a[i] = a[j]; a[j] = t; }
        return new String(a);
    }
}
