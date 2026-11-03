package ma.entraide.tawassol.demande;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.demande.DemandeModels.DemandeDto;
import ma.entraide.tawassol.demande.DemandeModels.DemandeRequest;
import ma.entraide.tawassol.personnel.PageResult;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DemandeController {
    public record AnnulationRequest(String commentaire) {}

    private final DemandeService service;
    private final SoldeCongeService soldes;

    public DemandeController(DemandeService service, SoldeCongeService soldes) {
        this.service = service;
        this.soldes = soldes;
    }

    @GetMapping("/types-demande")
    public List<Map<String, Object>> types() { return service.types(); }

    @GetMapping("/demandes")
    public PageResult<DemandeDto> list(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) String statut,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(u, statut, Math.max(page, 0), Math.max(1, Math.min(size, 100)));
    }

    @PostMapping("/demandes")
    @ResponseStatus(HttpStatus.CREATED)
    public DemandeDto create(@AuthenticationPrincipal AuthenticatedUser u, @Valid @RequestBody DemandeRequest req) { return service.create(u, req); }

    @GetMapping("/demandes/{id}")
    public DemandeDto get(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) { return service.get(u, id); }

    @PutMapping("/demandes/{id}")
    public DemandeDto update(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @Valid @RequestBody DemandeRequest req) { return service.update(u, id, req); }

    @DeleteMapping("/demandes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) { service.delete(u, id); }

    @PostMapping("/demandes/{id}/soumettre")
    public DemandeDto submit(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) { return service.submit(u, id); }

    @PostMapping("/demandes/{id}/annuler")
    public DemandeDto cancel(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody(required = false) AnnulationRequest req) {
        return service.cancel(u, id, req == null ? null : req.commentaire());
    }

    @GetMapping("/me/solde-conge")
    public SoldeCongeService.Solde solde(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) Integer annee) {
        if (u.personnelId() == null) throw new ma.entraide.tawassol.common.ApiException(HttpStatus.FORBIDDEN, "NO_PERSONNEL", "Ce compte n'est lié à aucun agent");
        return soldes.get(u.personnelId(), annee == null ? LocalDate.now().getYear() : annee, null);
    }
}
