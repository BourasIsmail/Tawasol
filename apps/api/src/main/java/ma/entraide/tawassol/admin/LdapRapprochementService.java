package ma.entraide.tawassol.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import ma.entraide.tawassol.auth.LdapService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Rapprochement des comptes de l'annuaire et des comptes TAWASSOL. Aucun mot de passe : ni l'AD ni TAWASSOL n'en exposent. */
@Service
public class LdapRapprochementService {
    public record Ligne(String statut, String identifiantWindows, String email, String nomAd, String matricule, String compteTawassol, String agent, String roleTawassol) {}

    private final LdapService ldap;
    private final JdbcTemplate jdbc;

    public LdapRapprochementService(LdapService ldap, JdbcTemplate jdbc) { this.ldap = ldap; this.jdbc = jdbc; }

    public List<Ligne> calculer() throws Exception {
        List<LdapService.Compte> ad = ldap.lister();
        Map<String, Map<String, Object>> comptes = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT u.login, u.role, u.est_actif, CONCAT(COALESCE(p.nom_fr,''),' ',COALESCE(p.prenom_fr,'')) AS agent FROM utilisateur u LEFT JOIN personnel p ON p.id = u.personnel_id"))
            comptes.put(String.valueOf(r.get("login")).toUpperCase(), r);
        Map<String, Integer> parMatricule = new HashMap<>();
        for (LdapService.Compte c : ad) if (c.matricule() != null && comptes.containsKey(c.matricule())) parMatricule.merge(c.matricule(), 1, Integer::sum);
        Set<String> utilises = new TreeSet<>();
        List<Ligne> out = new ArrayList<>();
        for (LdapService.Compte c : ad) {
            String cle = c.matricule() != null && comptes.containsKey(c.matricule()) ? c.matricule() : (comptes.containsKey(c.sam().toUpperCase()) ? c.sam().toUpperCase() : null);
            Map<String, Object> t = cle == null ? null : comptes.get(cle);
            String statut;
            if (t == null) statut = "AD_SANS_COMPTE_TAWASSOL";
            else if (parMatricule.getOrDefault(cle, 0) > 1) statut = "MATRICULE_EN_DOUBLE_DANS_AD";
            else if (c.desactive()) statut = "RAPPROCHE_COMPTE_AD_DESACTIVE";
            else statut = "RAPPROCHE";
            if (cle != null) utilises.add(cle);
            out.add(new Ligne(statut, c.sam(), c.mail(), c.nom(), c.matricule(), t == null ? null : String.valueOf(t.get("login")), t == null ? null : String.valueOf(t.get("agent")).trim(), t == null ? null : String.valueOf(t.get("role"))));
        }
        for (var e : comptes.entrySet())
            if (!utilises.contains(e.getKey()))
                out.add(new Ligne("TAWASSOL_SANS_COMPTE_AD", null, null, null, null, String.valueOf(e.getValue().get("login")), String.valueOf(e.getValue().get("agent")).trim(), String.valueOf(e.getValue().get("role"))));
        return out;
    }

    public static String csv(List<Ligne> l) {
        StringBuilder b = new StringBuilder("\uFEFFstatut;identifiant_windows;email;nom_ad;matricule_ad;compte_tawassol;agent;role\n");
        for (Ligne x : l) b.append(String.join(";", c(x.statut()), c(x.identifiantWindows()), c(x.email()), c(x.nomAd()), c(x.matricule()), c(x.compteTawassol()), c(x.agent()), c(x.roleTawassol()))).append('\n');
        return b.toString();
    }

    private static String c(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        // neutralise l'injection de formules dans Excel
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s + "\"";
    }
}
