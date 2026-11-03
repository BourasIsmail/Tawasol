package ma.entraide.tawassol.deplacement;

import java.util.Map;
import ma.entraide.tawassol.common.ApiException;
import org.springframework.web.bind.annotation.*;

/** Vérification publique d'un document par son QR code : aucune donnée personnelle, même réponse 404 pour un jeton faux ou un document inconnu. */
@RestController
@RequestMapping("/api/v1/verification")
public class VerificationController {
    private final VerificationService service;

    public VerificationController(VerificationService service) { this.service = service; }

    private static final int PAR_MINUTE = 120;
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.ArrayDeque<Long>> hits = new java.util.concurrent.ConcurrentHashMap<>();

    /** Limite par adresse (120 par minute) : l'endpoint est public. Le jeton fait 144 bits, il ne se devine pas, mais on évite le balayage. */
    private void limiter(jakarta.servlet.http.HttpServletRequest http) {
        long now = System.currentTimeMillis();
        var q = hits.computeIfAbsent(http.getRemoteAddr(), k -> new java.util.ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
            if (q.size() >= PAR_MINUTE) throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Trop de requêtes");
            q.addLast(now);
        }
        if (hits.size() > 10_000) hits.clear();
    }

    @GetMapping("/{type}/{id}/{jeton}")
    public Map<String, Object> verifier(@PathVariable String type, @PathVariable String id, @PathVariable String jeton, jakarta.servlet.http.HttpServletRequest http) {
        limiter(http);
        if (!Set.of(VerificationService.ORDRE, VerificationService.ETAT, VerificationService.ETAT_KM, VerificationService.ATTESTATION).contains(type)) throw ApiException.notFound();
        return service.verifier(type, id, jeton).orElseThrow(ApiException::notFound);
    }

    private static final class Set { static java.util.Set<String> of(String... v) { return java.util.Set.of(v); } }
}
