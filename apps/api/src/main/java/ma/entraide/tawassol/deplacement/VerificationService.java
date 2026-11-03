package ma.entraide.tawassol.deplacement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ma.entraide.tawassol.config.TawassolProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Jeton public de vérification porté par le QR code d'un document : HMAC du type de document, de l'identifiant et du numéro.
 * Remplace l'ancien QR de Suivi RH (identifiant d'utilisateur, de mission et heure d'impression) qui ne permettait aucune vérification.
 * La réponse publique ne contient aucune donnée personnelle : numéro, type, statut, date d'émission.
 */
@Service
public class VerificationService {
    public static final String ORDRE = "ordre", ETAT = "etat", ETAT_KM = "etat-km", ATTESTATION = "attestation";

    private final byte[] key;
    private final JdbcTemplate jdbc;
    private final String webUrl;

    public VerificationService(TawassolProperties props, JdbcTemplate jdbc) {
        this.key = ("verification|" + props.security().jwtSecret()).getBytes(StandardCharsets.UTF_8);
        this.jdbc = jdbc;
        this.webUrl = props.webUrl() == null ? "" : props.webUrl().replaceAll("/+$", "");
    }

    public String jeton(String missionId, String type, String numero) {
        try {
            Mac m = Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] h = m.doFinal((type + "|" + missionId + "|" + numero).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOf(h, 18));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public String url(String missionId, String type, String numero) { return webUrl + "/verification/" + type + "/" + missionId + "/" + jeton(missionId, type, numero); }

    /** Vide si le jeton est faux ou si le document n'existe pas : même réponse dans les deux cas. */
    public Optional<Map<String, Object>> verifier(String type, String missionId, String jeton) {
        if (ATTESTATION.equals(type)) return verifierAttestation(missionId, jeton);
        List<Map<String, Object>> l = jdbc.queryForList("SELECT numero_ordm, numero_etat, statut, date_emission, date_ordm FROM ordre_mission WHERE id = ? AND supprime_le IS NULL", missionId);
        if (l.isEmpty()) return Optional.empty();
        Map<String, Object> m = l.get(0);
        String numero = ETAT.equals(type) || ETAT_KM.equals(type) ? (String) m.get("numero_etat") : (String) m.get("numero_ordm");
        if (numero == null || !MessageDigest.isEqual(jeton(missionId, type, numero).getBytes(StandardCharsets.UTF_8), jeton.getBytes(StandardCharsets.UTF_8))) return Optional.empty();
        String statut = (String) m.get("statut");
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("valide", !"annulee".equals(statut) && !"rejetee".equals(statut));
        out.put("type", type); out.put("numero", numero); out.put("statut", statut);
        out.put("dateEmission", m.get("date_ordm") == null ? null : m.get("date_ordm").toString());
        return Optional.of(out);
    }

    private Optional<Map<String, Object>> verifierAttestation(String documentId, String jeton) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT numero, annule, emis_le FROM document_officiel WHERE id = ?", documentId);
        if (l.isEmpty()) return Optional.empty();
        Map<String, Object> d = l.get(0);
        String numero = (String) d.get("numero");
        if (!MessageDigest.isEqual(jeton(documentId, ATTESTATION, numero).getBytes(StandardCharsets.UTF_8), jeton.getBytes(StandardCharsets.UTF_8))) return Optional.empty();
        boolean annule = d.get("annule") instanceof Boolean b ? b : d.get("annule") instanceof Number n && n.intValue() != 0;
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("valide", !annule); out.put("type", ATTESTATION); out.put("numero", numero); out.put("statut", annule ? "annulee" : "emise");
        out.put("dateEmission", d.get("emis_le").toString().substring(0, 10));
        return Optional.of(out);
    }
}
