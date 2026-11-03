package ma.entraide.tawassol.personnel;

import java.util.List;
import ma.entraide.tawassol.access.AccessGuard;
import ma.entraide.tawassol.access.Scope;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PersonnelController {
    private final PersonnelRepository repo;
    private final AccessGuard guard;
    private final AuditService audit;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final java.time.Clock clock;

    public PersonnelController(PersonnelRepository repo, AccessGuard guard, AuditService audit, org.springframework.jdbc.core.JdbcTemplate jdbc, java.time.Clock clock) {
        this.repo = repo;
        this.guard = guard;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @GetMapping("/personnel")
    public PageResult<PersonnelDto> list(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String structureId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        Scope scope = guard.personnelScope(user);
        int s = Math.max(1, Math.min(size, 100));
        if (structureId != null && !scope.all() && !scope.structureIds().contains(structureId)) return new PageResult<>(List.of(), 0, Math.max(page, 0), s);
        PageResult<PersonnelRow> r = repo.search(q, structureId, scope, Math.max(page, 0), s);
        List<PersonnelDto> content = r.content().stream().map(row -> toDto(user, row)).toList();
        return new PageResult<>(content, r.total(), r.page(), r.size());
    }

    /** Hors périmètre ou inexistant : même réponse 404, pour ne pas révéler l'existence d'un identifiant. */
    @GetMapping("/personnel/{id}")
    public PersonnelDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        Scope scope = guard.personnelScope(user);
        PersonnelRow row = repo.findById(id).orElseThrow(ApiException::notFound);
        if (!scope.covers(row.id(), row.structureId())) throw ApiException.notFound();
        if (!guard.isSelf(user, id)) {
            audit.record(user.userId(), "PERSONNEL_READ", "personnel", id, null, null);
        }
        return toDto(user, row);
    }

    @GetMapping("/me")
    public MeDto me(@AuthenticationPrincipal AuthenticatedUser user) {
        PersonnelDto p = user.personnelId() == null ? null
                : repo.findById(user.personnelId()).map(PersonnelDto::detail).orElse(null);
        java.time.LocalDate today = java.time.LocalDate.now(clock);
        String pid = user.personnelId() == null ? "-" : user.personnelId();
        boolean responsable = jdbc.queryForObject("SELECT COUNT(*) FROM responsable_perimetre WHERE personnel_id = ? AND est_actif = 1 AND date_debut <= ? AND (date_fin IS NULL OR date_fin >= ?)", Integer.class, pid, today, today) > 0;
        boolean suppleant = jdbc.queryForObject("SELECT COUNT(*) FROM suppleance WHERE suppleant_id = ? AND est_actif = 1 AND ? BETWEEN date_debut AND date_fin", Integer.class, pid, today) > 0;
        return new MeDto(user.userId(), user.role().db(), user.mustChangePassword(), p, responsable, suppleant);
    }

    private PersonnelDto toDto(AuthenticatedUser user, PersonnelRow row) {
        return guard.levelFor(user, row.id()) == AccessGuard.Level.DETAIL ? PersonnelDto.detail(row) : PersonnelDto.synthese(row);
    }

    public record MeDto(String userId, String role, boolean mustChangePassword, PersonnelDto personnel, boolean responsable, boolean suppleant) {}
}
