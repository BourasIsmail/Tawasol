package ma.entraide.tawassol.demande;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.entraide.tawassol.demande.DemandeModels.*;
import ma.entraide.tawassol.personnel.PageResult;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DemandeRepository {
    private static final String SELECT = """
        SELECT d.id, d.numero, d.personnel_id, d.type_demande_id, t.code AS type_code, t.libelle AS type_libelle, t.validation_responsable,
               d.objet, d.description, d.statut, d.date_debut, d.date_fin, d.nb_jours, CAST(d.donnees_json AS CHAR) AS donnees_json,
               d.commentaire_responsable, d.commentaire_rh, d.cree_le, d.modifie_le, d.responsable_personnel_id
          FROM demande d JOIN type_demande t ON t.id = d.type_demande_id
        """;
    private static final RowMapper<DemandeRow> MAPPER = (rs, i) -> new DemandeRow(rs.getString("id"), rs.getString("numero"),
            rs.getString("personnel_id"), rs.getString("type_demande_id"), rs.getString("type_code"), rs.getString("type_libelle"),
            rs.getBoolean("validation_responsable"), rs.getString("objet"), rs.getString("description"), rs.getString("statut"),
            rs.getObject("date_debut", LocalDate.class), rs.getObject("date_fin", LocalDate.class), rs.getBigDecimal("nb_jours"),
            rs.getString("donnees_json"), rs.getString("commentaire_responsable"), rs.getString("commentaire_rh"),
            rs.getObject("cree_le", LocalDateTime.class), rs.getObject("modifie_le", LocalDateTime.class), rs.getString("responsable_personnel_id"));

    private final NamedParameterJdbcTemplate jdbc;

    public DemandeRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<DemandeRow> findById(String id) {
        return jdbc.query(SELECT + " WHERE d.id = :id", new MapSqlParameterSource("id", id), MAPPER).stream().findFirst();
    }

    public PageResult<DemandeRow> listForPersonnel(String personnelId, String statut, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource("pid", personnelId);
        String where = " WHERE d.personnel_id = :pid";
        if (statut != null && !statut.isBlank()) { where += " AND d.statut = :statut"; p.addValue("statut", statut); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM demande d" + where, p, Long.class);
        p.addValue("limit", size).addValue("offset", (long) page * size);
        List<DemandeRow> rows = jdbc.query(SELECT + where + " ORDER BY d.cree_le DESC, d.id LIMIT :limit OFFSET :offset", p, MAPPER);
        return new PageResult<>(rows, total == null ? 0 : total, page, size);
    }

    public String insert(String personnelId, String typeId, String objet, String description, LocalDate debut, LocalDate fin,
                         String donneesJson, LocalDateTime now) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO demande (id, personnel_id, type_demande_id, objet, description, statut, date_debut, date_fin, donnees_json, cree_le)
            VALUES (:id, :pid, :type, :objet, :desc, 'brouillon', :debut, :fin, :json, :now)
            """, new MapSqlParameterSource().addValue("id", id).addValue("pid", personnelId).addValue("type", typeId)
                .addValue("objet", objet).addValue("desc", description).addValue("debut", debut).addValue("fin", fin)
                .addValue("json", donneesJson).addValue("now", now));
        return id;
    }

    public void updateContent(String id, String objet, String description, LocalDate debut, LocalDate fin, String donneesJson) {
        jdbc.update("UPDATE demande SET objet = :objet, description = :desc, date_debut = :debut, date_fin = :fin, donnees_json = :json WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("objet", objet).addValue("desc", description)
                        .addValue("debut", debut).addValue("fin", fin).addValue("json", donneesJson));
    }

    public void setStatut(String id, String statut, String numero, BigDecimal nbJours) {
        jdbc.update("UPDATE demande SET statut = :s, numero = COALESCE(:n, numero), nb_jours = COALESCE(:j, nb_jours) WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("s", statut).addValue("n", numero).addValue("j", nbJours));
    }

    public void setResponsable(String id, String responsablePersonnelId) {
        jdbc.update("UPDATE demande SET responsable_personnel_id = :r WHERE id = :id", new MapSqlParameterSource("id", id).addValue("r", responsablePersonnelId));
    }

    public void delete(String id) {
        jdbc.update("DELETE FROM demande WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    public void addHistorique(String demandeId, String acteurId, String ancien, String nouveau, String commentaire, LocalDateTime now) {
        jdbc.update("INSERT INTO demande_historique (id, demande_id, acteur_id, ancien_statut, nouveau_statut, commentaire, cree_le) VALUES (:id,:d,:a,:o,:n,:c,:t)",
                new MapSqlParameterSource().addValue("id", UUID.randomUUID().toString()).addValue("d", demandeId).addValue("a", acteurId)
                        .addValue("o", ancien).addValue("n", nouveau).addValue("c", commentaire).addValue("t", now));
    }

    public List<HistoriqueDto> historique(String demandeId) {
        return jdbc.query("""
            SELECT h.ancien_statut, h.nouveau_statut, h.commentaire, h.cree_le,
                   TRIM(CONCAT(COALESCE(p.prenom_fr,''), ' ', COALESCE(p.nom_fr,''))) AS acteur
              FROM demande_historique h LEFT JOIN utilisateur u ON u.id = h.acteur_id LEFT JOIN personnel p ON p.id = u.personnel_id
             WHERE h.demande_id = :id ORDER BY h.seq
            """, new MapSqlParameterSource("id", demandeId), (rs, i) -> new HistoriqueDto(rs.getString("ancien_statut"),
                rs.getString("nouveau_statut"), rs.getString("commentaire"), rs.getString("acteur"), rs.getObject("cree_le", LocalDateTime.class)));
    }

    public List<PieceDto> pieces(String demandeId) {
        return jdbc.query("SELECT id, nom_fichier, type_mime, taille_kb, cree_le FROM piece_jointe WHERE demande_id = :id ORDER BY cree_le, id",
                new MapSqlParameterSource("id", demandeId), (rs, i) -> new PieceDto(rs.getString("id"), rs.getString("nom_fichier"),
                        rs.getString("type_mime"), (Integer) rs.getObject("taille_kb"), rs.getObject("cree_le", LocalDateTime.class)));
    }

    public int countPieces(String demandeId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM piece_jointe WHERE demande_id = :id", new MapSqlParameterSource("id", demandeId), Integer.class);
    }

    public Optional<String> typeIdByCode(String code) {
        return jdbc.queryForList("SELECT id FROM type_demande WHERE code = :c AND est_actif = 1", new MapSqlParameterSource("c", code), String.class).stream().findFirst();
    }

    public List<java.util.Map<String, Object>> typesActifs() {
        return jdbc.queryForList("SELECT code, libelle, description, validation_responsable AS validationResponsable FROM type_demande WHERE est_actif = 1 ORDER BY libelle", new MapSqlParameterSource());
    }

    public boolean chevauche(String personnelId, String excludeId, LocalDate debut, LocalDate fin) {
        Integer n = jdbc.queryForObject("""
            SELECT COUNT(*) FROM demande d JOIN type_demande t ON t.id = d.type_demande_id
             WHERE d.personnel_id = :p AND d.id <> :x AND t.code IN ('CONGE_ANNUEL','CONGE_MALADIE','CONGE_EXCEPTION')
               AND d.statut IN ('soumise','validee_responsable','approuvee') AND d.date_debut <= :fin AND d.date_fin >= :debut
            """, new MapSqlParameterSource().addValue("p", personnelId).addValue("x", excludeId).addValue("debut", debut).addValue("fin", fin), Integer.class);
        return n != null && n > 0;
    }

    public BigDecimal joursReserves(String personnelId, String excludeId, int annee) {
        BigDecimal v = jdbc.queryForObject("""
            SELECT COALESCE(SUM(d.nb_jours), 0) FROM demande d JOIN type_demande t ON t.id = d.type_demande_id
             WHERE d.personnel_id = :p AND d.id <> :x AND t.code = 'CONGE_ANNUEL' AND d.statut IN ('soumise','validee_responsable')
               AND YEAR(d.date_debut) = :a
            """, new MapSqlParameterSource().addValue("p", personnelId).addValue("x", excludeId).addValue("a", annee), BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }
}
