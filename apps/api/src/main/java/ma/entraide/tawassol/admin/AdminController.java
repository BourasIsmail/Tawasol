package ma.entraide.tawassol.admin;

import java.util.*;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.deplacement.PermissionService;
import ma.entraide.tawassol.personnel.PageResult;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Espace d'administration technique : comptes, rôles et journal d'audit. Réservé au profil admin ET à la permission role.gerer.
 * L'administrateur n'a aucun accès aux données des agents (dossiers, paie, missions) : il gère les accès, pas le contenu.
 * Il ne peut ni changer son propre rôle ni désactiver son propre compte. Chaque changement est journalisé.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    public record ModificationCompte(String roleId, Boolean estActif) {}

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final PermissionService permissions;
    private final AuditService audit;

    public AdminController(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, PermissionService permissions, AuditService audit) { this.jdbc = jdbc; this.named = named; this.permissions = permissions; this.audit = audit; }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles(@AuthenticationPrincipal AuthenticatedUser u) {
        permissions.exiger(u, "role.gerer");
        List<Map<String, Object>> roles = jdbc.queryForList("SELECT id, code, libelle_fr AS libelleFr, libelle_ar AS libelleAr, profil, est_actif AS estActif FROM role ORDER BY profil, code");
        for (Map<String, Object> r : roles)
            r.put("permissions", jdbc.queryForList("SELECT p.code FROM role_permission rp JOIN permission p ON p.id = rp.permission_id WHERE rp.role_id = ? ORDER BY p.code", String.class, r.get("id")));
        return roles;
    }

    @GetMapping("/utilisateurs")
    public PageResult<Map<String, Object>> utilisateurs(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) String q, @RequestParam(required = false) Boolean legacy,
                                                        @RequestParam(required = false) Boolean actif, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        permissions.exiger(u, "role.gerer");
        size = Math.max(1, Math.min(size, 100)); page = Math.max(page, 0);
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        if (q != null && !q.isBlank()) { w.append(" AND (ut.login LIKE :q OR pe.matricule LIKE :q OR pe.nom_fr LIKE :q)"); p.addValue("q", "%" + q.trim() + "%"); }
        if (legacy != null) { w.append(" AND ut.est_compte_legacy = :legacy"); p.addValue("legacy", legacy ? 1 : 0); }
        if (actif != null) { w.append(" AND ut.est_actif = :actif"); p.addValue("actif", actif ? 1 : 0); }
        String from = " FROM utilisateur ut LEFT JOIN personnel pe ON pe.id = ut.personnel_id LEFT JOIN role r ON r.id = ut.role_id" + w;
        long total = named.queryForObject("SELECT COUNT(*)" + from, p, Long.class);
        p.addValue("lim", size).addValue("off", (long) page * size);
        List<Map<String, Object>> rows = named.queryForList("SELECT ut.id, ut.login, ut.role AS profil, ut.role_id AS roleId, r.code AS roleCode, ut.est_actif AS estActif, ut.est_compte_legacy AS legacy, ut.doit_changer_mdp AS doitChangerMdp, ut.derniere_cnx AS derniereCnx, pe.matricule, pe.nom_fr AS nomFr, pe.prenom_fr AS prenomFr" + from + " ORDER BY ut.login LIMIT :lim OFFSET :off", p);
        return new PageResult<>(rows, total, page, size);
    }

    @PutMapping("/utilisateurs/{id}")
    @Transactional
    public Map<String, Object> modifier(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody ModificationCompte q) {
        permissions.exiger(u, "role.gerer");
        List<Map<String, Object>> l = jdbc.queryForList("SELECT id, role, role_id, est_actif, est_compte_legacy FROM utilisateur WHERE id = ?", id);
        if (l.isEmpty()) throw ApiException.notFound();
        Map<String, Object> ut = l.get(0);
        if (id.equals(u.userId())) throw new ApiException(HttpStatus.FORBIDDEN, "AUTO_MODIFICATION", "Vous ne pouvez pas modifier votre propre compte");
        String avant = "{\"roleId\":\"" + ut.get("role_id") + "\",\"estActif\":" + b(ut.get("est_actif")) + "}";
        if (q.roleId() != null) {
            List<Map<String, Object>> r = jdbc.queryForList("SELECT profil, est_actif FROM role WHERE id = ?", q.roleId());
            if (r.isEmpty() || !b(r.get(0).get("est_actif"))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ROLE_INCONNU", "Rôle inconnu ou inactif");
            jdbc.update("UPDATE utilisateur SET role_id = ?, role = ?, modifie_le = NOW() WHERE id = ?", q.roleId(), r.get(0).get("profil"), id);
        }
        if (q.estActif() != null) {
            // un compte hérité de Suivi RH réactivé doit changer son mot de passe à la première connexion
            jdbc.update("UPDATE utilisateur SET est_actif = ?, doit_changer_mdp = CASE WHEN ? = 1 AND est_compte_legacy = 1 THEN 1 ELSE doit_changer_mdp END, echecs_connexion = 0, verrouille_jusqua = NULL, modifie_le = NOW() WHERE id = ?", q.estActif() ? 1 : 0, q.estActif() ? 1 : 0, id);
            if (!q.estActif()) jdbc.update("UPDATE refresh_token SET revoque = 1 WHERE utilisateur_id = ?", id);   // la désactivation coupe les sessions ouvertes
        }
        audit.record(u.userId(), "ADMIN_COMPTE_MODIFIE", "utilisateur", id, avant, "{\"roleId\":\"" + (q.roleId() == null ? ut.get("role_id") : q.roleId()) + "\",\"estActif\":" + (q.estActif() == null ? b(ut.get("est_actif")) : q.estActif()) + "}");
        return Map.of("id", id);
    }

    @GetMapping("/audit")
    public PageResult<Map<String, Object>> audit(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) String action, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        permissions.exiger(u, "role.gerer");
        size = Math.max(1, Math.min(size, 200)); page = Math.max(page, 0);
        MapSqlParameterSource p = new MapSqlParameterSource();
        String w = "";
        if (action != null && !action.isBlank()) { w = " WHERE j.action LIKE :a"; p.addValue("a", action.trim() + "%"); }
        long total = named.queryForObject("SELECT COUNT(*) FROM journal_audit j" + w, p, Long.class);
        p.addValue("lim", size).addValue("off", (long) page * size);
        // le journal ne montre jamais le contenu avant/après : seulement qui, quoi, sur quel objet, quand
        List<Map<String, Object>> rows = named.queryForList("SELECT j.id, j.action, j.entite, j.entite_id AS entiteId, j.cree_le AS creeLe, ut.login FROM journal_audit j LEFT JOIN utilisateur ut ON ut.id = j.utilisateur_id" + w + " ORDER BY j.cree_le DESC, j.id LIMIT :lim OFFSET :off", p);
        return new PageResult<>(rows, total, page, size);
    }

    private static boolean b(Object o) { return o instanceof Boolean x ? x : o instanceof Number n && n.intValue() != 0; }
}
