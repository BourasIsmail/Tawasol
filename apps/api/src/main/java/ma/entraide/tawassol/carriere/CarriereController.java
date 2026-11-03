package ma.entraide.tawassol.carriere;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CarriereController {
    public record Avancement(LocalDate dateEffet, String decisionRef) {}

    private final CarriereService service;
    private final ma.entraide.tawassol.document.AttestationService attestations;

    public CarriereController(CarriereService service, ma.entraide.tawassol.document.AttestationService attestations) { this.service = service; this.attestations = attestations; }

    /** Attestation officielle d'une demande approuvée : l'agent concerné ou la RH, numéro unique, fichier archivé tel qu'émis. */
    @GetMapping("/demandes/{id}/attestation")
    public org.springframework.http.ResponseEntity<byte[]> attestation(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        var d = attestations.attestation(u, id);
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + d.nomFichier() + "\"").header("X-Content-Type-Options", "nosniff").body(d.contenu());
    }

    /** Ma carrière : chacun ne voit que la sienne. */
    @GetMapping("/me/carriere")
    public List<Map<String, Object>> maCarriere(@AuthenticationPrincipal AuthenticatedUser u) {
        if (u.personnelId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "NO_PERSONNEL", "Ce compte n'est lié à aucun agent");
        return service.historique(u.personnelId());
    }

    @GetMapping("/examens")
    public List<Map<String, Object>> examens(@AuthenticationPrincipal AuthenticatedUser u) { return service.examens(); }

    @GetMapping("/rh/avancement/propositions") @PreAuthorize("hasRole('RH')")
    public Map<String, Object> propositions() { return service.propositions(); }

    @PostMapping("/rh/avancement/{personnelId}/echelon") @PreAuthorize("hasRole('RH')")
    public Map<String, Object> avancer(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String personnelId, @RequestBody Avancement q) { return service.avancer(u, personnelId, q.dateEffet(), q.decisionRef()); }

    @PostMapping("/rh/examens") @PreAuthorize("hasRole('RH')") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> creerExamen(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String, Object> q) { return Map.of("id", service.creerExamen(u, q)); }

    @PutMapping("/rh/examens/{id}") @PreAuthorize("hasRole('RH')")
    public void majExamen(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody Map<String, Object> q) { service.majExamen(u, id, q); }
}
