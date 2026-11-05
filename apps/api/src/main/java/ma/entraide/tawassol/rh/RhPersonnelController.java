package ma.entraide.tawassol.rh;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Dossiers agents et référentiels organisationnels (grades, délégations, structures) : écriture réservée à la RH. */
@RestController
@RequestMapping("/api/v1/rh")
@PreAuthorize("hasRole('RH')")
public class RhPersonnelController {
    public record PersonnelRequest(String matricule, String nomFr, String prenomFr, String nomAr, String prenomAr, String structureId, String gradeId, LocalDate dateRecrutement, String email,
                                   String telephone, String cin, String sexe, String situationFamiliale, LocalDate dateNaissance, String positionAdministrative, String echelon, Integer echelle,
                                   LocalDate dateEchelon, LocalDate dateEchelle, String decisionRef, LocalDate dateEffet) {
        public PersonnelRhService.Input toInput() {
            return new PersonnelRhService.Input(matricule, nomFr, prenomFr, nomAr, prenomAr, structureId, gradeId, dateRecrutement, email, telephone, cin, sexe, situationFamiliale, dateNaissance, positionAdministrative, echelon, echelle, dateEchelon, dateEchelle, decisionRef, dateEffet);
        }
    }
    public record GradeRequest(Integer code, String libelleFr, String libelleAr, Integer echelle, Integer nbEchelons) {}
    public record StructureRequest(String delegationId, String parentId, String type, String code, String nomFr, String nomAr, String adresse) {}
    public record DelegationRequest(String coordinationId, String codeProvince, String nomProvinceFr, String nomProvinceAr, String nomDelegFr, String nomDelegAr, String telephone) {}

    private final PersonnelRhService service;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ma.entraide.tawassol.admin.ComptesService comptes;

    public RhPersonnelController(PersonnelRhService service, JdbcTemplate jdbc, AuditService audit, ma.entraide.tawassol.admin.ComptesService comptes) {
        this.service = service; this.jdbc = jdbc; this.audit = audit; this.comptes = comptes;
    }

    /** Rôles que la RH peut attribuer : agent et responsable uniquement. */
    @GetMapping("/comptes/roles")
    public List<Map<String, Object>> rolesComptes() {
        return jdbc.queryForList("SELECT id, code, libelle_fr AS libelleFr, libelle_ar AS libelleAr, profil FROM role WHERE est_actif = 1 AND profil IN ('agent','responsable') ORDER BY profil, code");
    }

    @GetMapping("/comptes/candidats")
    public List<Map<String, Object>> candidatsComptes(@RequestParam(required = false) String q) { return comptes.candidats(q); }

    @PostMapping("/comptes")
    @ResponseStatus(HttpStatus.CREATED)
    public ma.entraide.tawassol.admin.ComptesService.Resultat creerCompte(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody ma.entraide.tawassol.admin.ComptesService.Creation c) {
        return comptes.creer(u, c, false);
    }

    @GetMapping("/personnel/{id}")
    public Map<String, Object> fiche(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) { return service.fiche(u, id); }

    @PostMapping("/personnel")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> creer(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody PersonnelRequest r) { return Map.of("id", service.create(u, r.toInput())); }

    @PutMapping("/personnel/{id}")
    public Map<String, Object> modifier(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody PersonnelRequest r) {
        service.update(u, id, r.toInput());
        return Map.of("id", id);
    }

    @DeleteMapping("/personnel/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactiver(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) { service.deactivate(u, id); }

    @GetMapping("/personnel/{id}/carriere")
    public List<Map<String, Object>> carriere(@PathVariable String id) { return service.carriere(id); }

    // ---- référentiels ----

    @GetMapping("/grades")
    public List<Map<String, Object>> grades() { return jdbc.queryForList("SELECT id, code, libelle_fr AS libelleFr, libelle_ar AS libelleAr, echelle, nb_echelons AS nbEchelons FROM grade ORDER BY code"); }

    @PostMapping("/grades")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> creerGrade(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody GradeRequest r) {
        if (r.code() == null || blank(r.libelleFr()) || blank(r.libelleAr())) throw invalid();
        String id = UUID.randomUUID().toString();
        try { jdbc.update("INSERT INTO grade (id, code, libelle_fr, libelle_ar, echelle, nb_echelons) VALUES (?,?,?,?,?,?)", id, r.code(), r.libelleFr(), r.libelleAr(), r.echelle(), r.nbEchelons() == null ? 10 : r.nbEchelons()); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", "Ce code de grade existe déjà"); }
        audit.record(u.userId(), "GRADE_CREE", "grade", id, null, null);
        return Map.of("id", id);
    }

    @PutMapping("/grades/{id}")
    public Map<String, Object> majGrade(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody GradeRequest r) {
        if (jdbc.update("UPDATE grade SET libelle_fr = COALESCE(?, libelle_fr), libelle_ar = COALESCE(?, libelle_ar), echelle = COALESCE(?, echelle), nb_echelons = COALESCE(?, nb_echelons) WHERE id = ?", r.libelleFr(), r.libelleAr(), r.echelle(), r.nbEchelons(), id) == 0) throw ApiException.notFound();
        audit.record(u.userId(), "GRADE_MODIFIE", "grade", id, null, null);
        return Map.of("id", id);
    }

    @GetMapping("/structures")
    public List<Map<String, Object>> structures() {
        return jdbc.queryForList("SELECT id, code, type, nom_fr AS nomFr, nom_ar AS nomAr, delegation_id AS delegationId, parent_id AS parentId, est_active AS actif FROM structure ORDER BY code");
    }

    @PostMapping("/structures")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> creerStructure(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody StructureRequest r) {
        if (blank(r.delegationId()) || blank(r.code()) || blank(r.nomFr()) || blank(r.nomAr()) || !List.of("association", "etablissement", "centre", "complexe").contains(r.type())) throw invalid();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM delegation_province WHERE id = ?", Integer.class, r.delegationId()) == 0) throw invalid();
        if (r.parentId() != null && jdbc.queryForObject("SELECT COUNT(*) FROM structure WHERE id = ?", Integer.class, r.parentId()) == 0) throw invalid();
        String id = UUID.randomUUID().toString();
        try { jdbc.update("INSERT INTO structure (id, delegation_id, parent_id, type, code, nom_ar, nom_fr, adresse) VALUES (?,?,?,?,?,?,?,?)", id, r.delegationId(), r.parentId(), r.type(), r.code().trim(), r.nomAr(), r.nomFr(), r.adresse()); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", "Ce code de structure existe déjà"); }
        audit.record(u.userId(), "STRUCTURE_CREE", "structure", id, null, null);
        return Map.of("id", id);
    }

    @PutMapping("/structures/{id}")
    public Map<String, Object> majStructure(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id, @RequestBody StructureRequest r) {
        if (id.equals(r.parentId())) throw invalid();
        if (jdbc.update("UPDATE structure SET nom_fr = COALESCE(?, nom_fr), nom_ar = COALESCE(?, nom_ar), adresse = COALESCE(?, adresse), parent_id = COALESCE(?, parent_id) WHERE id = ?", r.nomFr(), r.nomAr(), r.adresse(), r.parentId(), id) == 0) throw ApiException.notFound();
        audit.record(u.userId(), "STRUCTURE_MODIFIEE", "structure", id, null, null);
        return Map.of("id", id);
    }

    @DeleteMapping("/structures/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactiverStructure(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE structure_id = ? AND est_actif = 1", Integer.class, id) > 0) throw new ApiException(HttpStatus.CONFLICT, "STRUCTURE_NOT_EMPTY", "Cette structure a encore des agents actifs");
        if (jdbc.update("UPDATE structure SET est_active = 0 WHERE id = ?", id) == 0) throw ApiException.notFound();
        audit.record(u.userId(), "STRUCTURE_DESACTIVEE", "structure", id, null, null);
    }

    @GetMapping("/delegations")
    public List<Map<String, Object>> delegations() {
        return jdbc.queryForList("SELECT id, coordination_id AS coordinationId, code_province AS codeProvince, nom_deleg_fr AS nomDelegFr, nom_deleg_ar AS nomDelegAr, est_active AS actif FROM delegation_province ORDER BY code_province");
    }

    @PostMapping("/delegations")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> creerDelegation(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody DelegationRequest r) {
        if (blank(r.coordinationId()) || blank(r.codeProvince()) || blank(r.nomProvinceFr()) || blank(r.nomProvinceAr()) || blank(r.nomDelegFr()) || blank(r.nomDelegAr())) throw invalid();
        String id = UUID.randomUUID().toString();
        try { jdbc.update("INSERT INTO delegation_province (id, coordination_id, code_province, nom_province_ar, nom_province_fr, nom_deleg_ar, nom_deleg_fr, telephone) VALUES (?,?,?,?,?,?,?,?)", id, r.coordinationId(), r.codeProvince().trim(), r.nomProvinceAr(), r.nomProvinceFr(), r.nomDelegAr(), r.nomDelegFr(), r.telephone()); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", "Ce code de province existe déjà"); }
        catch (org.springframework.dao.DataIntegrityViolationException e) { throw invalid(); }
        audit.record(u.userId(), "DELEGATION_CREE", "delegation_province", id, null, null);
        return Map.of("id", id);
    }

    @GetMapping("/coordinations")
    public List<Map<String, Object>> coordinations() {
        return jdbc.queryForList("SELECT id, code_region AS codeRegion, nom_region_fr AS nomRegionFr, nom_region_ar AS nomRegionAr, nom_coord_fr AS nomCoordFr FROM coordination_region WHERE est_active = 1 ORDER BY code_region");
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static ApiException invalid() { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Champs obligatoires manquants ou invalides"); }
}
