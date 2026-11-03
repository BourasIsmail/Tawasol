package ma.entraide.tawassol.validation;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ma.entraide.tawassol.hierarchy.HierarchyIndex;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Chaîne de validation par les responsables, commune aux demandes (congés, attestations…) et aux missions.
 * Tous les responsables de la hiérarchie valident, dans l'ordre, du plus proche au plus haut ; un rejet à un niveau arrête la chaîne.
 * Un niveau sans responsable n'existe pas (il est simplement absent). Le suppléant d'un responsable décide à sa place et c'est tracé.
 */
@Service
public class ChaineValidationService {
    public record Etape(String id, int ordre, String niveau, String responsablePersonnelId) {}
    /** complete : plus aucun niveau en attente ; suivant : responsable du prochain niveau ; rejetee : la chaîne est arrêtée. */
    public record Resultat(boolean complete, boolean rejetee, String suivantPersonnelId, boolean parSuppleance, int niveauDecide, int total) {}

    private final JdbcTemplate jdbc;
    private final HierarchyIndex hierarchy;
    private final Clock clock;

    public ChaineValidationService(JdbcTemplate jdbc, HierarchyIndex hierarchy, Clock clock) { this.jdbc = jdbc; this.hierarchy = hierarchy; this.clock = clock; }

    /** Fige la chaîne de l'objet. Retourne les étapes créées (vide : aucun responsable dans la hiérarchie). */
    public List<Etape> construire(String type, String objetId, String requesterPersonnelId, String structureId) {
        jdbc.update("DELETE FROM etape_validation WHERE objet_type = ? AND objet_id = ?", type, objetId);
        int ordre = 1;
        for (HierarchyIndex.Niveau n : hierarchy.chaine(requesterPersonnelId, structureId))
            jdbc.update("INSERT INTO etape_validation (id, objet_type, objet_id, ordre, niveau, responsable_personnel_id) VALUES (UUID(),?,?,?,?,?)", type, objetId, ordre++, n.niveau(), n.personnelId());
        return etapes(type, objetId);
    }

    public List<Etape> etapes(String type, String objetId) {
        return jdbc.query("SELECT id, ordre, niveau, responsable_personnel_id FROM etape_validation WHERE objet_type = ? AND objet_id = ? ORDER BY ordre",
                (rs, i) -> new Etape(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4)), type, objetId);
    }

    /** Étape en attente la plus proche : c'est elle, et elle seule, qui peut décider. */
    public Optional<Etape> active(String type, String objetId) {
        return jdbc.query("SELECT id, ordre, niveau, responsable_personnel_id FROM etape_validation WHERE objet_type = ? AND objet_id = ? AND statut = 'en_attente' ORDER BY ordre LIMIT 1",
                (rs, i) -> new Etape(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4)), type, objetId).stream().findFirst();
    }

    /** Le responsable (ou l'un de ceux qu'il supplée) est-il impliqué dans la chaîne, à n'importe quel niveau ? */
    public boolean implique(String type, String objetId, Set<String> personnelIds) {
        if (personnelIds.isEmpty()) return false;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM etape_validation WHERE objet_type = ? AND objet_id = ? AND responsable_personnel_id IN (" + String.join(",", java.util.Collections.nCopies(personnelIds.size(), "?")) + ")",
                Integer.class, concat(type, objetId, personnelIds));
        return n != null && n > 0;
    }

    private static Object[] concat(String type, String id, Set<String> ids) {
        Object[] a = new Object[2 + ids.size()]; a[0] = type; a[1] = id; int i = 2; for (String s : ids) a[i++] = s; return a;
    }

    /**
     * Enregistre la décision du niveau actif. L'appelant a déjà vérifié que l'étape active revient à l'acteur (ou à son suppléant).
     * Valider fait avancer au niveau suivant ; rejeter arrête la chaîne (niveaux restants « ignorés »).
     */
    public Resultat decider(String type, String objetId, Etape etape, String userId, String actingPersonnelId, boolean valider, String commentaire) {
        boolean parSuppleance = !etape.responsablePersonnelId().equals(actingPersonnelId);
        int n = jdbc.update("UPDATE etape_validation SET statut = ?, decide_par = ?, par_suppleance = ?, date_decision = ?, commentaire = ? WHERE id = ? AND statut = 'en_attente'",
                valider ? "validee" : "rejetee", userId, parSuppleance ? 1 : 0, Timestamp.valueOf(LocalDateTime.now(clock)), commentaire, etape.id());
        if (n != 1) throw new ma.entraide.tawassol.common.ApiException(org.springframework.http.HttpStatus.CONFLICT, "CONFLIT_DE_STATUT", "Ce niveau a déjà été décidé");
        int total = etapes(type, objetId).size();
        if (!valider) {
            jdbc.update("UPDATE etape_validation SET statut = 'ignoree' WHERE objet_type = ? AND objet_id = ? AND statut = 'en_attente'", type, objetId);
            return new Resultat(false, true, null, parSuppleance, etape.ordre(), total);
        }
        Optional<Etape> suivante = active(type, objetId);
        return new Resultat(suivante.isEmpty(), false, suivante.map(Etape::responsablePersonnelId).orElse(null), parSuppleance, etape.ordre(), total);
    }

    /** Lecture pour l'affichage : niveau, responsable, état, qui a décidé et quand. */
    public List<Map<String, Object>> liste(String type, String objetId) {
        return jdbc.queryForList("""
                SELECT e.ordre, e.niveau, e.statut, e.par_suppleance AS parSuppleance, e.date_decision AS dateDecision, e.commentaire,
                       p.id AS responsableId, p.nom_fr AS nomFr, p.prenom_fr AS prenomFr, p.nom_ar AS nomAr, p.prenom_ar AS prenomAr
                FROM etape_validation e JOIN personnel p ON p.id = e.responsable_personnel_id
                WHERE e.objet_type = ? AND e.objet_id = ? ORDER BY e.ordre""", type, objetId);
    }
}
