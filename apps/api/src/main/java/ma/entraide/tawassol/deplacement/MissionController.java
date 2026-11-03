package ma.entraide.tawassol.deplacement;

import jakarta.validation.Valid;
import ma.entraide.tawassol.deplacement.MissionModels.*;
import ma.entraide.tawassol.personnel.PageResult;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/missions")
public class MissionController {
    private final MissionService service;
    private final MissionPdfService pdf;
    private final PermissionService permissions;
    private final ma.entraide.tawassol.audit.AuditService audit;

    public MissionController(MissionService service, MissionPdfService pdf, ma.entraide.tawassol.audit.AuditService audit, PermissionService permissions) { this.service = service; this.pdf = pdf; this.audit = audit; this.permissions = permissions; }

    /** Droits du module Déplacements de l'utilisateur connecté (pilote l'affichage ; chaque action est de toute façon revérifiée côté serveur). */
    @GetMapping("/droits")
    public java.util.Map<String, Boolean> droits(@AuthenticationPrincipal AuthenticatedUser u) {
        java.util.Map<String, Boolean> d = new java.util.LinkedHashMap<>();
        for (String p : new String[]{"demander", "saisir_perimetre", "valider_niveau1", "valider", "corriger", "valider_paiement", "annuler_paiement", "etats", "analytics", "planifier", "bareme"})
            d.put(java.util.stream.Stream.of(p.split("_")).reduce((a, b) -> a + Character.toUpperCase(b.charAt(0)) + b.substring(1)).get(), permissions.a(u, "mission." + p));
        return d;
    }

    /** Documents officiels : même contrôle d'accès que la mission (404 hors périmètre), puis droit de téléchargement. L'empreinte du PDF émis est journalisée (archivage). */
    @GetMapping("/{id}/documents/{type}")
    public org.springframework.http.ResponseEntity<byte[]> document(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @PathVariable String type) {
        MissionDto m = service.get(u, id);
        if (!m.actionsPossibles().contains("telecharger")) throw new ma.entraide.tawassol.common.ApiException(HttpStatus.FORBIDDEN, "TELECHARGEMENT_REFUSE", "Document non disponible pour votre profil ou ce statut");
        MissionPdfService.Pdf d = switch (type) {
            case "ordre" -> pdf.ordreDeMission(id);
            case "etat" -> pdf.etat(id);
            case "etat-km" -> pdf.etatKilometrique(id);
            default -> throw ma.entraide.tawassol.common.ApiException.notFound();
        };
        audit.record(u.userId(), "MISSION_DOCUMENT_" + type.toUpperCase().replace('-', '_'), "ordre_mission", id, null, "{\"sha256\":\"" + d.sha256() + "\"}");
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + d.nomFichier() + "\"").header("X-Content-Type-Options", "nosniff").body(d.contenu());
    }

    @GetMapping
    public PageResult<MissionDto> list(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) String statut, @RequestParam(required = false) String personnelId,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(u, statut, personnelId, Math.max(page, 0), Math.max(1, Math.min(size, 100)));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MissionDto create(@AuthenticationPrincipal AuthenticatedUser u, @Valid @RequestBody MissionRequest q) { return service.creer(u, q); }

    @PostMapping("/simulation")
    public IndemniteService.Calcul simulation(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody MissionRequest q) { return service.simuler(u, q); }

    @GetMapping("/{id}")
    public MissionDto get(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) { return service.get(u, id); }

    @PutMapping("/{id}")
    public MissionDto update(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @Valid @RequestBody MissionRequest q) { return service.modifier(u, id, q); }

    @PostMapping("/{id}/soumettre")
    public MissionDto soumettre(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) { return service.soumettre(u, id); }

    @PostMapping("/{id}/valider-niveau1")
    public MissionDto niveau1(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody(required = false) MotifRequest r) { return service.validerNiveau1(u, id, r == null ? null : r.commentaire()); }

    @PostMapping("/{id}/approuver")
    public MissionDto approuver(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody(required = false) MotifRequest r) { return service.approuver(u, id, r == null ? null : r.commentaire()); }

    @PostMapping("/{id}/finaliser")
    public MissionDto finaliser(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody(required = false) FinalisationRequest q) { return service.finaliser(u, id, q); }

    @PostMapping("/{id}/payer")
    public MissionDto payer(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody(required = false) MotifRequest r) { return service.payer(u, id, r == null ? null : r.commentaire()); }

    @PostMapping("/{id}/annuler-paiement")
    public MissionDto annulerPaiement(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody MotifRequest r) { return service.annulerPaiement(u, id, r.commentaire()); }

    @PostMapping("/{id}/rejeter")
    public MissionDto rejeter(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody MotifRequest r) { return service.rejeter(u, id, r.commentaire()); }

    @PostMapping("/{id}/annuler")
    public MissionDto annuler(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody(required = false) MotifRequest r) { return service.annuler(u, id, r == null ? null : r.commentaire()); }
}
