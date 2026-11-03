package ma.entraide.tawassol.imports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.imports.ImportService.Demande;
import ma.entraide.tawassol.imports.ImportService.Mapping;
import ma.entraide.tawassol.imports.SheetReader.Reglages;
import ma.entraide.tawassol.param.ParametreService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Imports Excel/CSV : réservé à la RH. */
@RestController
@RequestMapping("/api/v1/rh/imports")
@PreAuthorize("hasRole('RH')")
public class ImportController {
    private final ImportService service;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ParametreService params;

    public ImportController(ImportService service, JdbcTemplate jdbc, ObjectMapper json, ParametreService params) {
        this.service = service; this.jdbc = jdbc; this.json = json; this.params = params;
    }

    @GetMapping("/champs")
    public Map<String, List<ImportSpec.Champ>> champs() { return ImportSpec.CHAMPS; }

    @PostMapping(value = "/analyse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> analyse(@RequestParam String type, @RequestParam("fichier") MultipartFile fichier, @RequestParam(required = false) String modeleId,
                                       @RequestParam(required = false) String encodage, @RequestParam(required = false) String separateur, @RequestParam(required = false) Integer ligneEntete) throws IOException {
        Map<String, Object> modele = modele(modeleId);
        Reglages r = reglages(modele, encodage, separateur, ligneEntete);
        Map<String, Object> out = new LinkedHashMap<>(service.analyser(type, contenu(fichier), r));
        if (modele != null) out.put("mappingModele", parseMapping((String) modele.get("mapping")));
        return out;
    }

    @PostMapping(value = "/controle", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportService.Resultat controle(@RequestParam String type, @RequestParam("fichier") MultipartFile fichier, @RequestParam(required = false) String mapping,
                                           @RequestParam(required = false) String modeleId, @RequestParam(required = false) String encodage, @RequestParam(required = false) String separateur,
                                           @RequestParam(required = false) String separateurDecimal, @RequestParam(required = false) String formatDate, @RequestParam(required = false) Integer ligneEntete,
                                           @RequestParam(required = false) Integer annee, @RequestParam(required = false) Integer mois) throws IOException {
        return service.controler(demande(type, fichier, mapping, modeleId, encodage, separateur, separateurDecimal, formatDate, ligneEntete, annee, mois, false));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> importer(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam String type, @RequestParam("fichier") MultipartFile fichier, @RequestParam(required = false) String mapping,
                                        @RequestParam(required = false) String modeleId, @RequestParam(required = false) String encodage, @RequestParam(required = false) String separateur,
                                        @RequestParam(required = false) String separateurDecimal, @RequestParam(required = false) String formatDate, @RequestParam(required = false) Integer ligneEntete,
                                        @RequestParam(required = false) Integer annee, @RequestParam(required = false) Integer mois, @RequestParam(defaultValue = "false") boolean remplacerBrouillon) throws IOException {
        return service.importer(u, demande(type, fichier, mapping, modeleId, encodage, separateur, separateurDecimal, formatDate, ligneEntete, annee, mois, remplacerBrouillon));
    }

    @GetMapping("/lots")
    public List<Map<String, Object>> lots(@RequestParam(required = false) String type) { return service.lots(type); }

    @GetMapping("/lots/{id}")
    public Map<String, Object> lot(@PathVariable String id) { return service.lot(id); }

    @GetMapping("/lots/{id}/rapport")
    public ResponseEntity<byte[]> rapport(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("rapport-" + id + ".csv").build().toString())
                .header("X-Content-Type-Options", "nosniff").body(service.rapportCsv(id));
    }

    @PostMapping("/lots/{id}/publier")
    public Map<String, Object> publier(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) { return service.publier(u, id); }

    @PostMapping("/lots/{id}/annuler")
    public Map<String, Object> annuler(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String id) { return service.annuler(u, id); }

    // ---- modèles d'import ----

    public record ModeleRequest(String type, String nom, String encodage, String separateur, String separateurDecimal, String formatDate, Object mapping) {}

    @GetMapping("/modeles")
    public List<Map<String, Object>> modeles(@RequestParam(required = false) String type) {
        String sql = "SELECT id, type_import AS type, nom, encodage, separateur, separateur_decimal AS separateurDecimal, format_date AS formatDate, CAST(mapping AS CHAR) AS mapping FROM import_modele WHERE est_actif = 1";
        return type == null ? jdbc.queryForList(sql + " ORDER BY nom") : jdbc.queryForList(sql + " AND type_import = ? ORDER BY nom", type);
    }

    @PostMapping("/modeles")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> creerModele(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody ModeleRequest r) throws JsonProcessingException {
        if (r.type() == null || !ImportSpec.CHAMPS.containsKey(r.type()) || r.nom() == null || r.nom().isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "type et nom obligatoires");
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO import_modele (id, type_import, nom, encodage, separateur, separateur_decimal, format_date, mapping, cree_par) VALUES (?,?,?,?,?,?,?,?,?)", id, r.type(), r.nom().trim(),
                    r.encodage() == null ? "UTF-8" : r.encodage(), String.valueOf(car(r.separateur(), ';')), String.valueOf(car(r.separateurDecimal(), ',')), r.formatDate() == null ? "dd/MM/yyyy" : r.formatDate(), json.writeValueAsString(r.mapping() == null ? Map.of() : r.mapping()), u.userId());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_MODEL", "Un modèle porte déjà ce nom");
        }
        return Map.of("id", id);
    }

    @PutMapping("/modeles/{id}")
    public Map<String, Object> majModele(@PathVariable String id, @RequestBody ModeleRequest r) throws JsonProcessingException {
        int n = jdbc.update("UPDATE import_modele SET nom = COALESCE(?, nom), encodage = COALESCE(?, encodage), separateur = COALESCE(?, separateur), separateur_decimal = COALESCE(?, separateur_decimal), format_date = COALESCE(?, format_date), mapping = COALESCE(?, mapping) WHERE id = ? AND est_actif = 1",
                r.nom(), r.encodage(), r.separateur() == null ? null : String.valueOf(car(r.separateur(), ';')), r.separateurDecimal() == null ? null : String.valueOf(car(r.separateurDecimal(), ',')), r.formatDate(), r.mapping() == null ? null : json.writeValueAsString(r.mapping()), id);
        if (n == 0) throw ApiException.notFound();
        return Map.of("id", id);
    }

    @DeleteMapping("/modeles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerModele(@PathVariable String id) {
        if (jdbc.update("UPDATE import_modele SET est_actif = 0 WHERE id = ?", id) == 0) throw ApiException.notFound();
    }

    // ---- outils ----

    private Demande demande(String type, MultipartFile f, String mappingJson, String modeleId, String enc, String sep, String sepDec, String fmt, Integer ligne, Integer annee, Integer mois, boolean remplacer) throws IOException {
        Map<String, Object> modele = modele(modeleId);
        Reglages r = reglages(modele, enc, sep, ligne);
        byte[] contenu = contenu(f);
        Mapping m;
        if (mappingJson != null && !mappingJson.isBlank()) m = parseMapping(mappingJson);
        else if (modele != null) m = parseMapping((String) modele.get("mapping"));
        else m = new Mapping(ImportSpec.suggerer(type, SheetReader.lire(contenu, r).colonnes()), List.of());
        char dec = sepDec != null && !sepDec.isEmpty() ? sepDec.charAt(0) : modele != null ? ((String) modele.get("separateur_decimal")).charAt(0) : ',';
        String format = fmt != null ? fmt : modele != null ? (String) modele.get("format_date") : "dd/MM/yyyy";
        return new Demande(type, contenu, f.getOriginalFilename(), m, r, format, dec, annee, mois, remplacer, modeleId);
    }

    private byte[] contenu(MultipartFile f) throws IOException {
        long max = params.integer("import.taille_max_mo", LocalDate.now(), 10) * 1024L * 1024L;
        if (f.isEmpty() || f.getSize() > max) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Fichier vide ou supérieur à la taille maximale");
        return f.getBytes();
    }

    private Map<String, Object> modele(String id) {
        if (id == null || id.isBlank()) return null;
        return jdbc.queryForList("SELECT encodage, separateur, separateur_decimal, format_date, CAST(mapping AS CHAR) AS mapping FROM import_modele WHERE id = ? AND est_actif = 1", id).stream().findFirst().orElseThrow(ApiException::notFound);
    }

    private Reglages reglages(Map<String, Object> modele, String enc, String sep, Integer ligne) {
        Reglages d = Reglages.defaut();
        String e = enc != null ? enc : modele != null ? (String) modele.get("encodage") : d.encodage();
        char s = sep != null && !sep.isEmpty() ? ("\\t".equals(sep) ? '\t' : sep.charAt(0)) : modele != null ? ((String) modele.get("separateur")).charAt(0) : d.separateur();
        int l = ligne != null ? ligne : modele != null ? parseMapping((String) modele.get("mapping")).ligneEntete() : 1;
        return new Reglages(e, s, Math.max(1, l));
    }

    private Mapping parseMapping(String raw) {
        try {
            Map<String, Object> m = json.readValue(raw == null ? "{}" : raw, new TypeReference<Map<String, Object>>() {});
            Map<String, String> cols = json.convertValue(m.getOrDefault("colonnes", Map.of()), new TypeReference<Map<String, String>>() {});
            List<ImportService.Rubrique> rub = json.convertValue(m.getOrDefault("rubriques", List.of()), new TypeReference<List<ImportService.Rubrique>>() {});
            Object lh = m.get("ligneEntete");
            return new Mapping(cols, rub, lh == null ? 1 : ((Number) lh).intValue());
        } catch (RuntimeException | JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MAPPING", "Correspondance invalide");
        }
    }

    private static char car(String s, char defaut) { return s == null || s.isEmpty() ? defaut : "\\t".equals(s) ? '\t' : s.charAt(0); }
}
