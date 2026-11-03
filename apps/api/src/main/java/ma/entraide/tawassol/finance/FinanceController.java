package ma.entraide.tawassol.finance;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Lecture des données financières de l'agent connecté, publiées uniquement. Toutes les requêtes sont bornées par
 * l'identifiant de l'agent du jeton : aucun paramètre client ne peut désigner un autre agent.
 * Chaque consultation d'un bulletin est journalisée (loi 09-08).
 */
@RestController
@RequestMapping("/api/v1/me")
public class FinanceController {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final BulletinPdfService pdf;
    private final Clock clock;

    public FinanceController(JdbcTemplate jdbc, AuditService audit, BulletinPdfService pdf, Clock clock) {
        this.jdbc = jdbc; this.audit = audit; this.pdf = pdf; this.clock = clock;
    }

    @GetMapping("/bulletins")
    public List<Map<String, Object>> bulletins(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("SELECT id, annee, mois, salaire_net AS salaireNet, alloc_familiale AS allocFamiliale, retenue_mutuelle AS retenueMutuelle, rappel FROM salaire WHERE personnel_id = ? AND statut = 'publie' ORDER BY annee DESC, mois DESC", pid(u));
    }

    @GetMapping("/bulletins/{id}")
    public Map<String, Object> bulletin(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        Map<String, Object> b = jdbc.queryForList("SELECT id, annee, mois, echelon, salaire_net AS salaireNet, alloc_familiale AS allocFamiliale, retenue_mutuelle AS retenueMutuelle, rappel FROM salaire WHERE id = ? AND personnel_id = ? AND statut = 'publie'", id, pid(u))
                .stream().findFirst().orElseThrow(ApiException::notFound);
        audit.record(u.userId(), "SALAIRE_READ", "salaire", id, null, null);
        Map<String, Object> out = new java.util.LinkedHashMap<>(b);
        out.put("lignes", jdbc.queryForList("SELECT code, libelle_fr AS libelleFr, libelle_ar AS libelleAr, sens, montant FROM salaire_ligne WHERE salaire_id = ? ORDER BY ordre, id", id));
        return out;
    }

    @GetMapping("/bulletins/{id}/pdf")
    public ResponseEntity<byte[]> bulletinPdf(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        var rows = jdbc.queryForList("""
            SELECT s.annee, s.mois, s.salaire_net, s.alloc_familiale, s.retenue_mutuelle, s.rappel, p.matricule, p.nom_fr, p.prenom_fr, p.nom_ar, p.prenom_ar,
                   g.libelle_fr AS grade_fr, g.libelle_ar AS grade_ar
              FROM salaire s JOIN personnel p ON p.id = s.personnel_id LEFT JOIN grade g ON g.id = p.grade_id
             WHERE s.id = ? AND s.personnel_id = ? AND s.statut = 'publie'
            """, id, pid(u));
        if (rows.isEmpty()) throw ApiException.notFound();
        Map<String, Object> r = rows.get(0);
        List<BulletinPdfService.Ligne> lignes = jdbc.query("SELECT libelle_fr, libelle_ar, sens, montant FROM salaire_ligne WHERE salaire_id = ? ORDER BY ordre, id",
                (rs, i) -> new BulletinPdfService.Ligne(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)), id);
        byte[] bytes = pdf.generer(new BulletinPdfService.Bulletin(String.valueOf(r.get("matricule")), r.get("prenom_fr") + " " + r.get("nom_fr"),
                r.get("prenom_ar") + " " + r.get("nom_ar"), (String) r.get("grade_fr"), (String) r.get("grade_ar"), ((Number) r.get("annee")).intValue(),
                ((Number) r.get("mois")).intValue(), (BigDecimal) r.get("salaire_net"), (BigDecimal) r.get("alloc_familiale"),
                (BigDecimal) r.get("retenue_mutuelle"), (BigDecimal) r.get("rappel"), lignes), LocalDate.now(clock));
        audit.record(u.userId(), "SALAIRE_PDF", "salaire", id, null, null);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("releve-paie-" + r.get("annee") + "-" + String.format("%02d", ((Number) r.get("mois")).intValue()) + ".pdf").build().toString())
                .header("X-Content-Type-Options", "nosniff").body(bytes);
    }

    @GetMapping("/primes")
    public List<Map<String, Object>> primes(@AuthenticationPrincipal AuthenticatedUser u) {
        audit.record(u.userId(), "PRIME_READ", "prime", null, null, null);
        return jdbc.queryForList("SELECT id, type_prime AS typePrime, montant_brut AS montantBrut, ir, montant_net AS montantNet, date_prime AS datePrime FROM prime WHERE personnel_id = ? AND statut = 'publie' ORDER BY date_prime DESC", pid(u));
    }

    @GetMapping("/credits")
    public List<Map<String, Object>> credits(@AuthenticationPrincipal AuthenticatedUser u) {
        audit.record(u.userId(), "CREDIT_READ", "credit", null, null, null);
        return jdbc.queryForList("SELECT id, banque, num_dossier AS numDossier, type_credit AS typeCredit, mensualite, montant_global AS montantGlobal, montant_restant AS montantRestant, nb_mois_restants AS nbMoisRestants, date_debut AS dateDebut FROM credit WHERE personnel_id = ? AND statut = 'publie' ORDER BY date_debut DESC", pid(u));
    }

    @GetMapping("/ordres-mission")
    public List<Map<String, Object>> missions(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("SELECT id, numero_ordm AS numeroOrdm, objet, date_depart AS dateDepart, date_retour AS dateRetour, statut, montant_total AS montantTotal FROM ordre_mission WHERE personnel_id = ? ORDER BY date_depart DESC", pid(u));
    }

    private String pid(AuthenticatedUser u) {
        if (u.personnelId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "NO_PERSONNEL", "Ce compte n'est lié à aucun agent");
        return u.personnelId();
    }
}
