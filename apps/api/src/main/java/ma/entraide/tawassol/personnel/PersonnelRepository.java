package ma.entraide.tawassol.personnel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import ma.entraide.tawassol.access.Scope;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PersonnelRepository {
    private static final String SELECT = """
        SELECT p.id, p.matricule, p.nom_fr, p.prenom_fr, p.nom_ar, p.prenom_ar, g.libelle_fr AS grade_fr, g.libelle_ar AS grade_ar,
               p.structure_id, st.nom_fr AS structure_fr, st.nom_ar AS structure_ar, p.date_recrutement, p.email, p.photo,
               p.echelon, p.echelle, p.date_echelon, p.est_actif, p.position_administrative, p.telephone
          FROM personnel p JOIN structure st ON st.id = p.structure_id LEFT JOIN grade g ON g.id = p.grade_id
        """;

    private static final RowMapper<PersonnelRow> MAPPER = (rs, i) -> map(rs);

    private final NamedParameterJdbcTemplate jdbc;

    public PersonnelRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PersonnelRow> findById(String id) {
        return jdbc.query(SELECT + " WHERE p.id = :id", new MapSqlParameterSource("id", id), MAPPER).stream().findFirst();
    }

    /** Liste filtrée par le périmètre : la restriction est appliquée dans la requête, pas après coup. */
    public PageResult<PersonnelRow> search(String q, String structureId, Scope scope, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE p.est_actif = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (!scope.all()) {
            if (scope.structureIds().isEmpty()) {
                where.append(" AND p.id = :self");
            } else {
                where.append(" AND (p.id = :self OR p.structure_id IN (:sids))");
                params.addValue("sids", scope.structureIds());
            }
            params.addValue("self", scope.selfPersonnelId() == null ? "-" : scope.selfPersonnelId());
        }
        if (structureId != null && !structureId.isBlank()) {
            where.append(" AND p.structure_id = :sid1");
            params.addValue("sid1", structureId);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (p.matricule LIKE :q OR p.nom_fr LIKE :q OR p.prenom_fr LIKE :q OR p.nom_ar LIKE :q OR p.prenom_ar LIKE :q)");
            params.addValue("q", "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM personnel p" + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<PersonnelRow> rows = jdbc.query(SELECT + where + " ORDER BY p.nom_fr, p.prenom_fr, p.id LIMIT :limit OFFSET :offset", params, MAPPER);
        return new PageResult<>(rows, total == null ? 0 : total, page, size);
    }

    private static PersonnelRow map(ResultSet rs) throws SQLException {
        return new PersonnelRow(rs.getString("id"), rs.getString("matricule"), rs.getString("nom_fr"), rs.getString("prenom_fr"),
                rs.getString("nom_ar"), rs.getString("prenom_ar"), rs.getString("grade_fr"), rs.getString("grade_ar"),
                rs.getString("structure_id"), rs.getString("structure_fr"), rs.getString("structure_ar"),
                rs.getObject("date_recrutement", java.time.LocalDate.class), rs.getString("email"), rs.getString("photo"),
                rs.getString("echelon"), (Integer) rs.getObject("echelle"), rs.getObject("date_echelon", java.time.LocalDate.class),
                rs.getBoolean("est_actif"), rs.getString("position_administrative"), rs.getString("telephone"));
    }
}
