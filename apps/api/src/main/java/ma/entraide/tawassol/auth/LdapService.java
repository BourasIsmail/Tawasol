package ma.entraide.tawassol.auth;

import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustStoreTrustManager;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import ma.entraide.tawassol.config.TawassolProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Authentification contre l'annuaire Active Directory : recherche de l'utilisateur avec le compte de service, puis liaison (bind) avec son DN et le mot de passe saisi.
 * Les mots de passe ne sont ni journalisés ni stockés. Basculement dc1 → dc2 si un contrôleur ne répond pas. LDAPS ; certificat vérifié
 * (autorité interne optionnelle : LDAP_CA_FILE, PEM).
 */
@Service
public class LdapService {
    private static final Logger log = LoggerFactory.getLogger(LdapService.class);

    /** Résultat d'une authentification : identifiant AD, DN et matricule (attribut configuré) s'il existe. */
    public record Identite(String dn, String sam, String matricule, String mail) {}

    public enum Statut { OK, INCONNU, MOT_DE_PASSE, INDISPONIBLE }

    public record Resultat(Statut statut, Identite identite) {}

    private final TawassolProperties.Ldap cfg;
    private volatile SSLSocketFactory socketFactory;

    public LdapService(TawassolProperties props) { this.cfg = props.ldap(); }

    public boolean actif() { return cfg != null && cfg.enabled(); }

    public Resultat authentifier(String identifiant, String motDePasse) {
        if (!actif() || identifiant == null || identifiant.isBlank() || motDePasse == null || motDePasse.isEmpty()) return new Resultat(Statut.MOT_DE_PASSE, null);
        try {
            Identite id = rechercher(identifiant.trim());
            if (id == null) return new Resultat(Statut.INCONNU, null);
            try (LDAPConnection c = connecter()) {
                BindResult r = c.bind(id.dn(), motDePasse);
                return new Resultat(r.getResultCode() == ResultCode.SUCCESS ? Statut.OK : Statut.MOT_DE_PASSE, id);
            } catch (LDAPException e) {
                if (e.getResultCode() == ResultCode.INVALID_CREDENTIALS) return new Resultat(Statut.MOT_DE_PASSE, id);
                throw e;
            }
        } catch (Exception e) {
            log.error("Annuaire indisponible ou mal configuré : {}", e.toString());
            return new Resultat(Statut.INDISPONIBLE, null);
        }
    }

    /** Recherche avec le compte de service (null si inconnu ou ambigu). */
    Identite rechercher(String identifiant) throws Exception {
        try (LDAPConnection c = connecter()) {
            c.bind(cfg.bindDn(), cfg.bindPassword());
            String f = cfg.userFilter().replace("{0}", Filter.encodeValue(identifiant));
            SearchRequest req = new SearchRequest(cfg.baseDn(), SearchScope.SUB, com.unboundid.ldap.sdk.DereferencePolicy.NEVER, 2, 5, false, Filter.create(f),
                    "sAMAccountName", "mail", cfg.matriculeAttribute());
            List<SearchResultEntry> e = c.search(req).getSearchEntries();
            if (e.size() != 1) {
                if (e.size() > 1) log.warn("Filtre LDAP ambigu pour un identifiant : {} entrées", e.size());
                return null;
            }
            SearchResultEntry en = e.get(0);
            String mat = en.getAttributeValue(cfg.matriculeAttribute());
            return new Identite(en.getDN(), en.getAttributeValue("sAMAccountName"), mat == null ? null : mat.trim().toUpperCase(), en.getAttributeValue("mail"));
        }
    }

    /** Diagnostic pour l'administrateur : joint chaque contrôleur et compte les entrées visibles ; ne renvoie aucun secret. */
    public List<String> diagnostic() {
        List<String> out = new ArrayList<>();
        if (!actif()) { out.add("LDAP désactivé (LDAP_ENABLED=false)"); return out; }
        if (cfg.bindPassword() == null || cfg.bindPassword().isBlank()) out.add("ERREUR : LDAP_BIND_PASSWORD absent");
        for (String h : cfg.hosts()) {
            try (LDAPConnection c = open(h)) {
                c.bind(cfg.bindDn(), cfg.bindPassword());
                int n;
                try {
                    n = c.search(new SearchRequest(cfg.baseDn(), SearchScope.SUB, com.unboundid.ldap.sdk.DereferencePolicy.NEVER, 5, 5, false, Filter.create("(objectClass=user)"), "sAMAccountName", cfg.matriculeAttribute())).getEntryCount();
                } catch (com.unboundid.ldap.sdk.LDAPSearchException e) {
                    // limite de 5 entrées atteinte : la recherche fonctionne, il y en a au moins 5
                    if (e.getResultCode() != ResultCode.SIZE_LIMIT_EXCEEDED) throw e;
                    n = e.getEntryCount();
                }
                out.add("OK " + h + ":" + cfg.port() + " — liaison du compte de service réussie, recherche dans l'OU : " + n + " entrée(s) lue(s) (limite 5)");
            } catch (Exception e) {
                out.add("ECHEC " + h + ":" + cfg.port() + " — " + e.getClass().getSimpleName() + " : " + e.getMessage());
            }
        }
        return out;
    }

    public java.util.Map<String, Object> recherche(String identifiant) {
        if (!actif()) return java.util.Map.of("erreur", "LDAP désactivé");
        try {
            Identite i = rechercher(identifiant);
            if (i == null) return java.util.Map.of("trouve", false);
            return java.util.Map.of("trouve", true, "sam", String.valueOf(i.sam()), "matricule", String.valueOf(i.matricule()), "mail", String.valueOf(i.mail()));
        } catch (Exception e) {
            return java.util.Map.of("erreur", e.getClass().getSimpleName() + " : " + e.getMessage());
        }
    }

    /** Exploration pour l'administrateur : au plus 3 fiches dont le nom, l'identifiant ou l'e-mail contient le texte, avec leurs attributs lisibles (hors binaires). */
    public java.util.List<java.util.Map<String, Object>> exploration(String texte) {
        java.util.List<java.util.Map<String, Object>> out = new ArrayList<>();
        if (!actif() || texte == null || texte.trim().length() < 3) return out;
        try (LDAPConnection c = connecter()) {
            c.bind(cfg.bindDn(), cfg.bindPassword());
            String t = Filter.encodeValue(texte.trim());
            Filter f = Filter.create("(&(objectClass=user)(|(sAMAccountName=*" + t + "*)(cn=*" + t + "*)(displayName=*" + t + "*)(mail=*" + t + "*)(employeeID=*" + t + "*)))");
            java.util.List<SearchResultEntry> es;
            try {
                es = c.search(new SearchRequest(cfg.baseDn(), SearchScope.SUB, com.unboundid.ldap.sdk.DereferencePolicy.NEVER, 3, 5, false, f, "*")).getSearchEntries();
            } catch (com.unboundid.ldap.sdk.LDAPSearchException e) {
                if (e.getResultCode() != ResultCode.SIZE_LIMIT_EXCEEDED) throw e;
                es = e.getSearchEntries();
            }
            for (SearchResultEntry e : es) {
                java.util.Map<String, Object> m = new java.util.TreeMap<>();
                m.put("dn", e.getDN());
                for (com.unboundid.ldap.sdk.Attribute a : e.getAttributes()) {
                    String n = a.getName().toLowerCase();
                    if (n.contains("pwd") || n.contains("password") || n.equals("objectsid") || n.equals("objectguid") || n.equals("thumbnailphoto") || n.equals("usercertificate")) continue;
                    String v = String.join(" | ", a.getValues());
                    m.put(a.getName(), v.length() > 120 ? v.substring(0, 120) + "…" : v);
                }
                out.add(m);
            }
        } catch (Exception e) {
            out.add(java.util.Map.of("erreur", e.getClass().getSimpleName() + " : " + e.getMessage()));
        }
        return out;
    }

    public record Compte(String sam, String mail, String nom, String matricule, boolean desactive) {}

    /** Tous les comptes utilisateurs de l'OU (recherche paginée). Pour le rapprochement administrateur. */
    public List<Compte> lister() throws Exception {
        List<Compte> out = new ArrayList<>();
        try (LDAPConnection c = connecter()) {
            c.bind(cfg.bindDn(), cfg.bindPassword());
            com.unboundid.ldap.sdk.controls.SimplePagedResultsControl page = new com.unboundid.ldap.sdk.controls.SimplePagedResultsControl(500);
            do {
                SearchRequest req = new SearchRequest(cfg.baseDn(), SearchScope.SUB, com.unboundid.ldap.sdk.DereferencePolicy.NEVER, 0, 30, false,
                        Filter.create("(&(objectClass=user)(!(objectClass=computer)))"), "sAMAccountName", "mail", "displayName", "userAccountControl", cfg.matriculeAttribute());
                req.setControls(page);
                com.unboundid.ldap.sdk.SearchResult r = c.search(req);
                for (SearchResultEntry e : r.getSearchEntries()) {
                    String sam = e.getAttributeValue("sAMAccountName");
                    if (sam == null) continue;
                    String m = e.getAttributeValue(cfg.matriculeAttribute());
                    Integer uac = e.getAttributeValueAsInteger("userAccountControl");
                    out.add(new Compte(sam, e.getAttributeValue("mail"), e.getAttributeValue("displayName"), m == null ? null : m.trim().toUpperCase(), uac != null && (uac & 2) != 0));
                }
                page = com.unboundid.ldap.sdk.controls.SimplePagedResultsControl.get(r);
            } while (page != null && page.moreResultsToReturn() && (page = new com.unboundid.ldap.sdk.controls.SimplePagedResultsControl(500, page.getCookie())) != null);
        }
        return out;
    }

    private LDAPConnection connecter() throws Exception {
        LDAPException derniere = null;
        for (String h : cfg.hosts()) {
            try { return open(h); } catch (LDAPException e) { derniere = e; log.warn("Contrôleur {} injoignable : {}", h, e.getMessage()); }
        }
        throw derniere != null ? derniere : new LDAPException(ResultCode.CONNECT_ERROR, "aucun contrôleur configuré");
    }

    private LDAPConnection open(String host) throws Exception {
        LDAPConnectionOptions o = new LDAPConnectionOptions();
        int ms = (int) cfg.timeout().toMillis();
        o.setConnectTimeoutMillis(ms);
        o.setResponseTimeoutMillis(ms);
        return cfg.tls() ? new LDAPConnection(sockets(), o, host, cfg.port()) : new LDAPConnection(o, host, cfg.port());
    }

    private SSLSocketFactory sockets() throws Exception {
        SSLSocketFactory f = socketFactory;
        if (f == null) {
            TrustManager[] tm = null;
            String ca = cfg.caFile();
            if (ca != null && !ca.isBlank()) {
                // Les autorités du système restent de confiance ; l'autorité interne est ajoutée.
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null, null);
                TrustManagerFactory def = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                def.init((KeyStore) null);
                int i = 0;
                for (TrustManager t : def.getTrustManagers())
                    if (t instanceof javax.net.ssl.X509TrustManager x) for (X509Certificate cert : x.getAcceptedIssuers()) ks.setCertificateEntry("sys" + i++, cert);
                try (FileInputStream in = new FileInputStream(ca)) {
                    for (var cert : CertificateFactory.getInstance("X.509").generateCertificates(in)) ks.setCertificateEntry("ca" + i++, cert);
                }
                TrustManagerFactory tf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tf.init(ks);
                tm = tf.getTrustManagers();
            }
            f = (tm == null ? new SSLUtil() : new SSLUtil(tm)).createSSLSocketFactory();
            socketFactory = f;
        }
        return f;
    }
}
