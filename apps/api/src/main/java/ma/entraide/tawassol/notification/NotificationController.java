package ma.entraide.tawassol.notification;

import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(defaultValue = "false") boolean nonLues,
                                    @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> items = service.lister(u.userId(), nonLues, Math.max(1, Math.min(limit, 100)));
        return Map.of("nonLues", service.nonLues(u.userId()), "items", items);
    }

    @PostMapping("/{id}/lu")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lu(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        if (!service.marquerLue(u.userId(), id)) throw ApiException.notFound();
    }

    @PostMapping("/lu-tout")
    public Map<String, Object> luTout(@AuthenticationPrincipal AuthenticatedUser u) {
        return Map.of("marquees", service.toutMarquerLu(u.userId()));
    }
}
