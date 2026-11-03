package ma.entraide.tawassol.referentiel;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Listes de choix pour les formulaires (délégations, moyens de transport, programmes) : référentiels non sensibles, actifs uniquement. */
@RestController
@RequestMapping("/api/v1/referentiels")
public class ReferentielController {
    private final JdbcTemplate jdbc;

    public ReferentielController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/delegations")
    public List<Map<String, Object>> delegations() {
        return jdbc.queryForList("SELECT id, code_province AS code, nom_deleg_fr AS nomFr, nom_deleg_ar AS nomAr FROM delegation_province WHERE est_active = 1 ORDER BY nom_deleg_fr");
    }

    @GetMapping("/moyens-transport")
    public List<Map<String, Object>> moyensTransport() {
        return jdbc.queryForList("SELECT id, code, libelle_fr AS libelleFr, libelle_ar AS libelleAr FROM moyen_transport WHERE est_actif = 1 ORDER BY libelle_fr");
    }

    @GetMapping("/programmes-mission")
    public List<Map<String, Object>> programmes() {
        return jdbc.queryForList("SELECT id, code, nom_fr AS nomFr, nom_ar AS nomAr FROM programme_mission WHERE est_actif = 1 ORDER BY nom_fr");
    }

    @GetMapping("/puissances-fiscales")
    public List<Map<String, Object>> puissancesFiscales() {
        return jdbc.queryForList("SELECT id, code, libelle_fr AS libelleFr, libelle_ar AS libelleAr, taux_km AS tauxKm FROM puissance_fiscale WHERE date_effet <= CURRENT_DATE AND (date_fin IS NULL OR date_fin >= CURRENT_DATE) ORDER BY taux_km, code");
    }
}
