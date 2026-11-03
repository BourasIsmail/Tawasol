package ma.entraide.tawassol.piecejointe;

import java.util.Map;
import ma.entraide.tawassol.demande.DemandeModels.PieceDto;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class PieceJointeController {
    private final PieceJointeService service;

    public PieceJointeController(PieceJointeService service) { this.service = service; }

    @PostMapping(value = "/demandes/{id}/pieces-jointes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PieceDto upload(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestParam("fichier") MultipartFile fichier) {
        return service.upload(u, id, fichier);
    }

    @DeleteMapping("/demandes/{id}/pieces-jointes/{pid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @PathVariable String pid) { service.delete(u, id, pid); }

    @GetMapping("/demandes/{id}/pieces-jointes/{pid}/lien")
    public Map<String, Object> lien(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @PathVariable String pid) { return service.lien(u, id, pid); }

    /** Téléchargement par lien signé, sans jeton Bearer : la signature et l'expiration font foi. */
    @GetMapping("/fichiers/{token}")
    public ResponseEntity<org.springframework.core.io.Resource> fichier(@PathVariable String token) {
        PieceJointeService.Fichier f = service.ouvrir(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.nom()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(f.typeMime()))
                .body(f.resource());
    }
}
