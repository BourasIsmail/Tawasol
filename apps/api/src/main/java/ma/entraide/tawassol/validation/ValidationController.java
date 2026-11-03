package ma.entraide.tawassol.validation;

import java.util.Map;
import ma.entraide.tawassol.personnel.PageResult;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/validation")
public class ValidationController {
    public record Decision(String commentaire) {}

    private final ValidationService service;

    public ValidationController(ValidationService service) { this.service = service; }

    @GetMapping("/tableau-de-bord")
    public Map<String, Object> dashboard(@AuthenticationPrincipal AuthenticatedUser u) { return service.tableauDeBord(u); }

    @GetMapping("/demandes")
    public PageResult<Map<String, Object>> queue(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) String statut,
                                                 @RequestParam(required = false) String type, @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.queue(u, statut, type, Math.max(page, 0), Math.max(1, Math.min(size, 100)));
    }

    @GetMapping("/demandes/{id}")
    public Map<String, Object> detail(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) { return service.detail(u, id); }

    @PostMapping("/demandes/{id}/valider")
    public Map<String, Object> valider(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody(required = false) Decision d) {
        return service.decider(u, id, true, d == null ? null : d.commentaire());
    }

    @PostMapping("/demandes/{id}/rejeter")
    public Map<String, Object> rejeter(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody(required = false) Decision d) {
        return service.decider(u, id, false, d == null ? null : d.commentaire());
    }
}
