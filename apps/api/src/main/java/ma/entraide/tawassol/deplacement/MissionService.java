package ma.entraide.tawassol.deplacement;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import ma.entraide.tawassol.access.AccessGuard;
import ma.entraide.tawassol.access.Scope;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.deplacement.MissionModels.*;
import ma.entraide.tawassol.notification.NotificationService;
import ma.entraide.tawassol.personnel.PageResult;
import ma.entraide.tawassol.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Circuit de validation d'une mission : brouillon, soumise, validée par le responsable, approuvée (numéro d'ordre),
 * finalisée (numéro d'état, montants figés), payée. Rejet et annulation possibles avant le paiement.
 * Séparation des tâches : l'agent ne valide jamais sa propre mission, et le paiement est validé par une autre personne
 * que celles qui ont approuvé et finalisé. Chaque transition est tracée (historique + journal d'audit).
 */
@Service
public class MissionService {
    private static final String SELECT = """
            SELECT om.*, p.nom_fr, p.prenom_fr, p.matricule, p.structure_id, s.delegation_id AS agent_delegation_id, p.echelle AS agent_echelle,
                   dd.nom_deleg_fr AS depart_nom, da.nom_deleg_fr AS arrivee_nom, mt.code AS moyen_code, mt.libelle_fr AS moyen_nom, pm.nom_fr AS programme_nom
            FROM ordre_mission om
            JOIN personnel p ON p.id = om.personnel_id
            JOIN structure s ON s.id = p.structure_id
            LEFT JOIN delegation_province dd ON dd.id = om.depart_delegation_id
            LEFT JOIN delegation_province da ON da.id = om.arrival_delegation_id
            LEFT JOIN moyen_transport mt ON mt.id = om.moyen_transport_id
            LEFT JOIN programme_mission pm ON pm.id = om.programme_id
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final AccessGuard guard;
    private final PermissionService permissions;
    private final IndemniteService indemnites;
    private final NumerotationMissionService numeros;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;
    private final ma.entraide.tawassol.validation.ChaineValidationService chaine;
    private final ma.entraide.tawassol.access.PerimeterService perimeters;

    public MissionService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, AccessGuard guard, PermissionService permissions, IndemniteService indemnites,
                          NumerotationMissionService numeros, NotificationService notifications, AuditService audit, Clock clock,
                          ma.entraide.tawassol.validation.ChaineValidationService chaine, ma.entraide.tawassol.access.PerimeterService perimeters) {
        this.chaine = chaine; this.perimeters = perimeters;
        this.jdbc = jdbc; this.named = named; this.guard = guard; this.permissions = permissions; this.indemnites = indemnites;
        this.numeros = numeros; this.notifications = notifications; this.audit = audit; this.clock = clock;
    }

    private static ApiException refus(String code, String msg) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, msg); }
    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static boolean bool(Object o) { return o instanceof Boolean b ? b : o instanceof Number n && n.intValue() != 0; }
    private static BigDecimal dec(Object o) { return o == null ? null : new BigDecimal(o.toString()); }
    private static LocalDateTime dt(Object o) { return o == null ? null : o instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) o; }
    private static Timestamp ts(LocalDateTime d) { return d == null ? null : Timestamp.valueOf(d); }

    // ------------------------------------------------------------------ lecture

    private Map<String, Object> ligne(String id) {
        List<Map<String, Object>> l = jdbc.queryForList(SELECT + " WHERE om.id = ? AND om.supprime_le IS NULL", id);
        if (l.isEmpty()) throw ApiException.notFound();
        return l.get(0);
    }

    /** Une mission hors périmètre est invisible (404), jamais « interdite » : on ne révèle pas son existence. */
    private Map<String, Object> visible(AuthenticatedUser u, String id) {
        Map<String, Object> m = ligne(id);
        if (!guard.personnelScope(u).covers(str(m.get("personnel_id")), str(m.get("structure_id")))) throw ApiException.notFound();
        return m;
    }

    @Transactional(readOnly = true)
    public MissionDto get(AuthenticatedUser u, String id) { return dto(u, visible(u, id), true); }

    @Transactional(readOnly = true)
    public PageResult<MissionDto> list(AuthenticatedUser u, String statut, String personnelId, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(clauseDePerimetre(u, p));
        if (statut != null && !statut.isBlank()) { w.append(" AND om.statut = :statut"); p.addValue("statut", statut); }
        if (personnelId != null && !personnelId.isBlank()) { w.append(" AND om.personnel_id = :pid"); p.addValue("pid", personnelId); }
        long total = named.queryForObject("SELECT COUNT(*) FROM ordre_mission om JOIN personnel p ON p.id = om.personnel_id" + w, p, Long.class);
        p.addValue("lim", size).addValue("off", (long) page * size);
        List<Map<String, Object>> rows = named.queryForList(SELECT + w + " ORDER BY om.date_depart DESC, om.id LIMIT :lim OFFSET :off", p);
        List<MissionDto> items = new ArrayList<>();
        for (Map<String, Object> r : rows) items.add(dto(u, r, false));
        return new PageResult<>(items, total, page, size);
    }

    /** Clause WHERE commune (liste, exports, analytique) : le périmètre est appliqué à un seul endroit. Alias requis : om (ordre_mission), p (personnel). */
    public String clauseDePerimetre(AuthenticatedUser u, MapSqlParameterSource p) {
        Scope sc = guard.personnelScope(u);
        StringBuilder w = new StringBuilder(" WHERE om.supprime_le IS NULL");
        if (!sc.all()) {
            w.append(" AND (om.personnel_id = :self");
            p.addValue("self", sc.selfPersonnelId() == null ? "" : sc.selfPersonnelId());
            if (!sc.structureIds().isEmpty()) { w.append(" OR p.structure_id IN (:structs)"); p.addValue("structs", sc.structureIds()); }
            w.append(")");
        }
        return w.toString();
    }

    // ------------------------------------------------------------------ saisie

    @Transactional
    public MissionDto creer(AuthenticatedUser u, MissionRequest q) {
        String cible = q.personnelId() == null || q.personnelId().equals(u.personnelId()) ? requirePersonnel(u) : q.personnelId();
        boolean pourSoi = cible.equals(u.personnelId());
        if (pourSoi) permissions.exiger(u, "mission.demander");
        else {
            permissions.exiger(u, "mission.saisir_perimetre");
            List<String> st = jdbc.queryForList("SELECT structure_id FROM personnel WHERE id = ? AND est_actif = 1", String.class, cible);
            if (st.isEmpty() || !guard.personnelScope(u).covers(cible, st.get(0))) throw ApiException.notFound();
        }
        if (q.dateDepart() == null) throw refus("DEPART_REQUIS", "La date de départ est obligatoire");
        verifierDates(q.dateDepart(), q.dateRetour());
        verifierReferences(q);
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO ordre_mission (id, personnel_id, programme_id, depart_delegation_id, arrival_delegation_id, moyen_transport_id, objet, date_depart, date_retour, annee,
                    statut, deplacement_remboursable, frais_transport, vehicule_immatriculation, vehicule_nom, puissance_fiscale_id, distance_km, nombre_trajets, cree_le)
                VALUES (?,?,?,?,?,?,?,?,?,?, 'brouillon', ?,?,?,?,?,?,?, ?)""",
                id, cible, q.programmeId(), q.departDelegationId(), q.arrivalDelegationId(), q.moyenTransportId(), q.objet().trim(), ts(q.dateDepart()), ts(q.dateRetour()), q.dateDepart().getYear(),
                q.remboursable() == null || q.remboursable() ? 1 : 0, q.fraisTransport() == null ? BigDecimal.ZERO : q.fraisTransport(), q.vehiculeImmatriculation(), q.vehiculeNom(),
                q.puissanceFiscaleId(), q.distanceKm(), q.nombreTrajets(), Timestamp.valueOf(LocalDateTime.now(clock)));
        historique(id, u, null, "brouillon", pourSoi ? null : "Saisie pour le compte de l'agent");
        audit.record(u.userId(), "MISSION_CREER", "ordre_mission", id, null, null);
        return get(u, id);
    }

    @Transactional
    public MissionDto modifier(AuthenticatedUser u, String id, MissionRequest q) {
        Map<String, Object> m = visible(u, id);
        exigerDroitDeSaisie(u, m);
        if (!"brouillon".equals(m.get("statut"))) throw refus("MISSION_NON_MODIFIABLE", "Seul un brouillon est modifiable");
        if (q.dateDepart() == null) throw refus("DEPART_REQUIS", "La date de départ est obligatoire");
        verifierDates(q.dateDepart(), q.dateRetour());
        verifierReferences(q);
        jdbc.update("""
                UPDATE ordre_mission SET objet = ?, programme_id = ?, depart_delegation_id = ?, arrival_delegation_id = ?, moyen_transport_id = ?, date_depart = ?, date_retour = ?, annee = ?,
                    deplacement_remboursable = ?, frais_transport = ?, vehicule_immatriculation = ?, vehicule_nom = ?, puissance_fiscale_id = ?, distance_km = ?, nombre_trajets = ?, modifie_le = ? WHERE id = ?""",
                q.objet().trim(), q.programmeId(), q.departDelegationId(), q.arrivalDelegationId(), q.moyenTransportId(), ts(q.dateDepart()), ts(q.dateRetour()), q.dateDepart().getYear(),
                q.remboursable() == null || q.remboursable() ? 1 : 0, q.fraisTransport() == null ? BigDecimal.ZERO : q.fraisTransport(), q.vehiculeImmatriculation(), q.vehiculeNom(),
                q.puissanceFiscaleId(), q.distanceKm(), q.nombreTrajets(), Timestamp.valueOf(LocalDateTime.now(clock)), id);
        audit.record(u.userId(), "MISSION_MODIFIER", "ordre_mission", id, null, null);
        return get(u, id);
    }

    private void exigerDroitDeSaisie(AuthenticatedUser u, Map<String, Object> m) {
        boolean pourSoi = str(m.get("personnel_id")).equals(u.personnelId());
        permissions.exiger(u, pourSoi ? "mission.demander" : "mission.saisir_perimetre");
    }

    private void verifierDates(LocalDateTime depart, LocalDateTime retour) {
        if (retour != null && !retour.isAfter(depart)) throw refus("DATES_INCOHERENTES", "Le retour doit être postérieur au départ");
    }

    private void verifierReferences(MissionRequest q) {
        if (count("SELECT COUNT(*) FROM delegation_province WHERE id = ?", q.arrivalDelegationId()) == 0) throw refus("DESTINATION_INCONNUE", "Destination inconnue");
        if (q.departDelegationId() != null && count("SELECT COUNT(*) FROM delegation_province WHERE id = ?", q.departDelegationId()) == 0) throw refus("DEPART_INCONNU", "Lieu de départ inconnu");
        if (q.moyenTransportId() != null && count("SELECT COUNT(*) FROM moyen_transport WHERE id = ?", q.moyenTransportId()) == 0) throw refus("TRANSPORT_INCONNU", "Moyen de transport inconnu");
        if (q.programmeId() != null && count("SELECT COUNT(*) FROM programme_mission WHERE id = ?", q.programmeId()) == 0) throw refus("PROGRAMME_INCONNU", "Programme inconnu");
    }

    private int count(String sql, Object... args) { Integer n = jdbc.queryForObject(sql, Integer.class, args); return n == null ? 0 : n; }

    // ------------------------------------------------------------------ circuit

    @Transactional
    public MissionDto soumettre(AuthenticatedUser u, String id) {
        Map<String, Object> m = visible(u, id);
        exigerDroitDeSaisie(u, m);
        exiger(m, "brouillon");
        verifierChevauchement(m);
        // estimation (sans figer) : une alerte de barème absent n'empêche pas de soumettre
        passer(u, id, "brouillon", "soumise", null);
        // chaîne complète des responsables, figée à la soumission ; sans aucun responsable dans la hiérarchie, la mission passe directement à l'approbation
        var etapes = chaine.construire("mission", id, str(m.get("personnel_id")), str(m.get("structure_id")));
        if (etapes.isEmpty()) passer(u, id, "soumise", "validee_responsable", "Aucun responsable dans la hiérarchie : validation hiérarchique sans objet");
        else notifierResponsable(etapes.get(0).responsablePersonnelId(), m);
        return get(u, id);
    }

    /** Valide le niveau hiérarchique actif : seul le responsable de ce niveau (ou son suppléant) peut le faire, après les niveaux inférieurs. */
    @Transactional
    public MissionDto validerNiveau1(AuthenticatedUser u, String id, String commentaire) {
        Map<String, Object> m = visible(u, id);
        permissions.exiger(u, "mission.valider_niveau1");
        exiger(m, "soumise");
        if (str(m.get("personnel_id")).equals(u.personnelId())) throw refus("AUTO_VALIDATION", "Vous ne pouvez pas valider votre propre mission");
        var etape = etapeActiveDe(u, id);
        var r = chaine.decider("mission", id, etape, u.userId(), u.personnelId(), true, commentaire);
        String niveau = "Niveau " + r.niveauDecide() + "/" + r.total() + " (" + etape.niveau() + ")" + (r.parSuppleance() ? " [par suppléance]" : "");
        String txt = niveau + (commentaire == null || commentaire.isBlank() ? "" : " : " + commentaire);
        if (r.complete()) {
            passer(u, id, "soumise", "validee_responsable", txt);
            notifierAgent(m, "Mission validée par tous vos responsables", "Votre mission « " + m.get("objet") + " » est en attente d'approbation.");
        } else {
            historique(id, u, "soumise", "soumise", txt);
            audit.record(u.userId(), "MISSION_NIVEAU_VALIDE", "ordre_mission", id, null, "{\"niveau\":" + r.niveauDecide() + "}");
            notifierResponsable(r.suivantPersonnelId(), m);
            notifierAgent(m, "Étape de validation franchie", "Votre mission « " + m.get("objet") + " » : niveau " + r.niveauDecide() + " sur " + r.total() + " validé.");
        }
        return get(u, id);
    }

    private ma.entraide.tawassol.validation.ChaineValidationService.Etape etapeActiveDe(AuthenticatedUser u, String id) {
        var etape = chaine.active("mission", id).orElseThrow(() -> refus("TRANSITION_INTERDITE", "Aucun niveau de validation en attente"));
        if (!perimeters.effectivePersonnelIds(u.personnelId(), true).contains(etape.responsablePersonnelId()))
            throw new ApiException(HttpStatus.CONFLICT, "NOT_YOUR_TURN", "Ce n'est pas encore (ou plus) le tour de votre niveau de validation");
        return etape;
    }

    private void notifierResponsable(String responsablePersonnelId, Map<String, Object> m) {
        notifications.notifierResponsable(responsablePersonnelId, "VALIDATION_A_FAIRE", "Mission à valider", str(m.get("nom_fr")) + " " + str(m.get("prenom_fr")) + " · " + m.get("objet"), "/deplacements/" + m.get("id"));
    }

    @Transactional
    public MissionDto approuver(AuthenticatedUser u, String id, String commentaire) {
        Map<String, Object> m = visible(u, id);
        permissions.exiger(u, "mission.valider");
        exiger(m, "validee_responsable");
        exigerAutrePersonne(u, m, "soumise", "validee_responsable");
        verifierChevauchement(m);
        LocalDateTime now = LocalDateTime.now(clock);
        int annee = dt(m.get("date_depart")).getYear();
        // le numéro d'ordre est attribué à l'approbation (émission du document), dans la même transaction
        String numero = numeros.prochainNumero(str(m.get("agent_delegation_id")), annee, NumerotationMissionService.Type.ORDM);
        List<String> avert = new ArrayList<>();
        BigDecimal estime = estimer(m, avert);
        jdbc.update("UPDATE ordre_mission SET numero_ordm = ?, date_ordm = ?, date_emission = ?, valide_par = ?, date_validation = ?, montant_total = ? WHERE id = ?",
                numero, java.sql.Date.valueOf(now.toLocalDate()), Timestamp.valueOf(now), u.userId(), Timestamp.valueOf(now), estime, id);
        passer(u, id, "validee_responsable", "approuvee", commentaire);
        notifierAgent(m, "Ordre de mission approuvé", "Votre ordre de mission " + numero + " est disponible.");
        return get(u, id);
    }

    /** Après le retour : dates réelles, repas décomptés, montants figés, numéro d'état attribué. */
    @Transactional
    public MissionDto finaliser(AuthenticatedUser u, String id, FinalisationRequest q) {
        Map<String, Object> m = visible(u, id);
        permissions.exiger(u, "mission.valider");
        exiger(m, "approuvee");
        if (str(m.get("personnel_id")).equals(u.personnelId())) throw refus("AUTO_VALIDATION", "Vous ne pouvez pas finaliser votre propre mission");
        LocalDateTime depart = q != null && q.dateDepart() != null ? q.dateDepart() : dt(m.get("date_depart"));
        LocalDateTime retour = q != null && q.dateRetour() != null ? q.dateRetour() : dt(m.get("date_retour"));
        if (retour == null) throw refus("RETOUR_REQUIS", "La date et l'heure de retour réelles sont obligatoires pour finaliser");
        if (!retour.isAfter(depart)) throw refus("DATES_INCOHERENTES", "Le retour doit être postérieur au départ");
        if (retour.isAfter(LocalDateTime.now(clock))) throw refus("RETOUR_FUTUR", "Le retour ne peut pas être dans le futur");
        BigDecimal frais = q != null && q.fraisTransport() != null ? q.fraisTransport() : dec(m.get("frais_transport"));
        BigDecimal km = q != null && q.distanceKm() != null ? q.distanceKm() : dec(m.get("distance_km"));
        Integer trajets = q != null && q.nombreTrajets() != null ? q.nombreTrajets() : (m.get("nombre_trajets") == null ? null : ((Number) m.get("nombre_trajets")).intValue());
        var c = indemnites.calculer(new IndemniteService.Demande(echelle(m), depart, retour, bool(m.get("deplacement_remboursable")), str(m.get("moyen_code")), frais, km, trajets, str(m.get("puissance_fiscale_id"))));
        if (c.avertissements().contains("DONNEES_KILOMETRIQUES_INCOMPLETES")) throw refus("DONNEES_KILOMETRIQUES_INCOMPLETES", "Distance, trajets et puissance fiscale sont requis pour une voiture privée");
        int annee = dt(m.get("date_depart")).getYear();
        String etat = numeros.prochainNumero(str(m.get("agent_delegation_id")), annee, NumerotationMissionService.Type.ETAT);
        jdbc.update("""
                UPDATE ordre_mission SET date_depart = ?, date_retour = ?, nombre_repas_taux_1 = ?, indemnite_taux_1 = ?, nombre_repas_taux_2 = ?, indemnite_taux_2 = ?,
                    montant_indemnites = ?, frais_transport = ?, distance_km = ?, nombre_trajets = ?, taux_km = ?, montant_km = ?, montant_total = ?, numero_etat = ?, retour_non_renseigne = 0 WHERE id = ?""",
                ts(depart), ts(retour), c.repasTaux1(), c.tauxRepas1(), c.repasTaux2(), c.tauxRepas2(), c.montantIndemnites(), c.fraisTransport(), km, trajets, c.tauxKm(), c.montantKm(), c.total(), etat, id);
        passer(u, id, "approuvee", "finalisee", q == null ? null : q.commentaire());
        return get(u, id);
    }

    @Transactional
    public MissionDto payer(AuthenticatedUser u, String id, String commentaire) {
        Map<String, Object> m = visible(u, id);
        permissions.exiger(u, "mission.valider_paiement");
        exiger(m, "finalisee");
        if (str(m.get("personnel_id")).equals(u.personnelId())) throw refus("AUTO_VALIDATION", "Vous ne pouvez pas valider le paiement de votre propre mission");
        exigerAutrePersonne(u, m, "approuvee", "finalisee");
        jdbc.update("UPDATE ordre_mission SET paye_par = ?, date_paiement = ? WHERE id = ?", u.userId(), Timestamp.valueOf(LocalDateTime.now(clock)), id);
        passer(u, id, "finalisee", "payee", commentaire);
        notifierAgent(m, "Indemnités payées", "Les indemnités de votre mission « " + m.get("objet") + " » sont payées.");
        return get(u, id);
    }

    @Transactional
    public MissionDto annulerPaiement(AuthenticatedUser u, String id, String commentaire) {
        Map<String, Object> m = visible(u, id);
        permissions.exiger(u, "mission.annuler_paiement");
        exiger(m, "payee");
        if (commentaire == null || commentaire.isBlank()) throw refus("MOTIF_REQUIS", "Un motif est obligatoire pour annuler un paiement");
        jdbc.update("UPDATE ordre_mission SET paye_par = NULL, date_paiement = NULL WHERE id = ?", id);
        passer(u, id, "payee", "finalisee", commentaire);
        return get(u, id);
    }

    @Transactional
    public MissionDto rejeter(AuthenticatedUser u, String id, String motif) {
        Map<String, Object> m = visible(u, id);
        String statut = str(m.get("statut"));
        if ("soumise".equals(statut)) {
            permissions.exiger(u, "mission.valider_niveau1");
            if (!str(m.get("personnel_id")).equals(u.personnelId())) {
                var etape = etapeActiveDe(u, id);
                if (motif != null && !motif.isBlank()) chaine.decider("mission", id, etape, u.userId(), u.personnelId(), false, motif.trim());
            }
        }
        else if ("validee_responsable".equals(statut)) permissions.exiger(u, "mission.valider");
        else throw refus("MISSION_NON_REJETABLE", "Seule une mission en attente de validation peut être rejetée");
        if (str(m.get("personnel_id")).equals(u.personnelId())) throw refus("AUTO_VALIDATION", "Vous ne pouvez pas rejeter votre propre mission");
        if (motif == null || motif.isBlank()) throw refus("MOTIF_REQUIS", "Le motif du rejet est obligatoire");
        jdbc.update("UPDATE ordre_mission SET motif_rejet = ? WHERE id = ?", motif.trim(), id);
        passer(u, id, statut, "rejetee", motif.trim());
        notifierAgent(m, "Mission rejetée", "Votre mission « " + m.get("objet") + " » a été rejetée : " + motif.trim());
        return get(u, id);
    }

    /** Annulation avant paiement. Le numéro d'ordre déjà émis est conservé (document signé) ; aucun numéro d'état n'a été consommé avant la finalisation. */
    @Transactional
    public MissionDto annuler(AuthenticatedUser u, String id, String commentaire) {
        Map<String, Object> m = visible(u, id);
        String statut = str(m.get("statut"));
        if (Set.of("payee", "rejetee", "annulee").contains(statut)) throw refus("MISSION_NON_ANNULABLE", "Cette mission ne peut plus être annulée");
        boolean createur = str(m.get("personnel_id")).equals(u.personnelId()) && Set.of("brouillon", "soumise").contains(statut);
        if (!createur) permissions.exiger(u, "mission.corriger");
        jdbc.update("UPDATE etape_validation SET statut = 'ignoree' WHERE objet_type = 'mission' AND objet_id = ? AND statut = 'en_attente'", id);
        passer(u, id, statut, "annulee", commentaire);
        return get(u, id);
    }

    @Transactional(readOnly = true)
    public IndemniteService.Calcul simuler(AuthenticatedUser u, MissionRequest q) {
        String pid = q.personnelId() == null ? requirePersonnel(u) : q.personnelId();
        Integer echelle = jdbc.queryForList("SELECT echelle FROM personnel WHERE id = ?", Integer.class, pid).stream().findFirst().orElse(null);
        String code = q.moyenTransportId() == null ? null : jdbc.queryForList("SELECT code FROM moyen_transport WHERE id = ?", String.class, q.moyenTransportId()).stream().findFirst().orElse(null);
        return indemnites.calculer(new IndemniteService.Demande(echelle, q.dateDepart(), q.dateRetour(), q.remboursable() == null || q.remboursable(), code, q.fraisTransport(), q.distanceKm(), q.nombreTrajets(), q.puissanceFiscaleId()));
    }

    // ------------------------------------------------------------------ internes

    private void exiger(Map<String, Object> m, String statut) {
        if (!statut.equals(m.get("statut"))) throw refus("TRANSITION_INTERDITE", "Cette action n'est pas possible au statut « " + m.get("statut") + " »");
    }

    private String requirePersonnel(AuthenticatedUser u) {
        if (u.personnelId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "NO_PERSONNEL", "Ce compte n'est lié à aucun agent");
        return u.personnelId();
    }

    private Integer echelle(Map<String, Object> m) { return m.get("agent_echelle") == null ? null : ((Number) m.get("agent_echelle")).intValue(); }

    private BigDecimal estimer(Map<String, Object> m, List<String> avert) {
        try {
            var c = indemnites.calculer(new IndemniteService.Demande(echelle(m), dt(m.get("date_depart")), dt(m.get("date_retour")), bool(m.get("deplacement_remboursable")), str(m.get("moyen_code")),
                    dec(m.get("frais_transport")), dec(m.get("distance_km")), m.get("nombre_trajets") == null ? null : ((Number) m.get("nombre_trajets")).intValue(), str(m.get("puissance_fiscale_id"))));
            avert.addAll(c.avertissements());
            return c.total();
        } catch (ApiException e) { avert.add(e.code()); return BigDecimal.ZERO; }
    }

    private void verifierChevauchement(Map<String, Object> m) {
        LocalDateTime d = dt(m.get("date_depart")), r = dt(m.get("date_retour"));
        LocalDateTime fin = r != null ? r : d.plusDays(1);
        int autres = count("SELECT COUNT(*) FROM ordre_mission WHERE personnel_id = ? AND id <> ? AND supprime_le IS NULL AND statut IN ('soumise','validee_responsable','approuvee','finalisee','payee') AND date_depart < ? AND COALESCE(date_retour, DATE_ADD(date_depart, INTERVAL 1 DAY)) > ?",
                m.get("personnel_id"), m.get("id"), Timestamp.valueOf(fin), Timestamp.valueOf(d));
        if (autres > 0) throw refus("MISSION_CHEVAUCHEMENT", "Cet agent a déjà une mission sur cette période");
        int conges = count("SELECT COUNT(*) FROM demande dm JOIN type_demande t ON t.id = dm.type_demande_id WHERE dm.personnel_id = ? AND t.code LIKE 'CONGE%' AND dm.statut = 'approuvee' AND dm.date_debut <= ? AND dm.date_fin >= ?",
                m.get("personnel_id"), java.sql.Date.valueOf(fin.toLocalDate()), java.sql.Date.valueOf(d.toLocalDate()));
        if (conges > 0) throw refus("MISSION_PENDANT_CONGE", "Cet agent est en congé approuvé sur cette période");
    }

    /** L'acteur ne doit être ni l'agent, ni l'auteur d'une étape précédente citée. */
    private void exigerAutrePersonne(AuthenticatedUser u, Map<String, Object> m, String... statutsPrecedents) {
        List<String> agentUser = jdbc.queryForList("SELECT id FROM utilisateur WHERE personnel_id = ?", String.class, m.get("personnel_id"));
        if (agentUser.contains(u.userId())) throw refus("SEPARATION_DES_TACHES", "Vous ne pouvez pas traiter votre propre mission");
        for (String s : statutsPrecedents) {
            List<String> acteurs = jdbc.queryForList("SELECT acteur_id FROM ordre_mission_historique WHERE ordre_mission_id = ? AND nouveau_statut = ? AND acteur_id IS NOT NULL", String.class, m.get("id"), s);
            if (acteurs.contains(u.userId())) throw refus("SEPARATION_DES_TACHES", "Cette étape doit être validée par une autre personne que celle qui a traité l'étape « " + s + " »");
        }
    }

    private void passer(AuthenticatedUser u, String id, String de, String vers, String commentaire) {
        int n = jdbc.update("UPDATE ordre_mission SET statut = ?, modifie_le = ? WHERE id = ? AND statut = ?", vers, Timestamp.valueOf(LocalDateTime.now(clock)), id, de);
        if (n != 1) throw new ApiException(HttpStatus.CONFLICT, "CONFLIT_DE_STATUT", "La mission a été modifiée entre-temps, rechargez-la");
        historique(id, u, de, vers, commentaire);
        audit.record(u.userId(), "MISSION_" + vers.toUpperCase(), "ordre_mission", id, "{\"statut\":\"" + de + "\"}", "{\"statut\":\"" + vers + "\"}");
    }

    private void historique(String id, AuthenticatedUser u, String de, String vers, String commentaire) {
        jdbc.update("INSERT INTO ordre_mission_historique (id, ordre_mission_id, acteur_id, ancien_statut, nouveau_statut, commentaire) VALUES (UUID(),?,?,?,?,?)",
                id, u.userId(), de, vers, commentaire);
    }

    private void notifierAgent(Map<String, Object> m, String titre, String message) {
        jdbc.queryForList("SELECT id FROM utilisateur WHERE personnel_id = ?", String.class, m.get("personnel_id")).forEach(uid -> notifications.notifier(uid, "mission", titre, message, "/deplacements/" + m.get("id")));
    }

    private List<String> actions(AuthenticatedUser u, Map<String, Object> m) {
        List<String> a = new ArrayList<>();
        String st = str(m.get("statut"));
        boolean soi = str(m.get("personnel_id")).equals(u.personnelId());
        boolean saisie = permissions.a(u, soi ? "mission.demander" : "mission.saisir_perimetre");
        if (st.equals("brouillon") && saisie) { a.add("modifier"); a.add("soumettre"); }
        if (st.equals("soumise") && !soi && permissions.a(u, "mission.valider_niveau1") && monTour(u, str(m.get("id")))) { a.add("valider_niveau1"); a.add("rejeter"); }
        if (st.equals("validee_responsable") && !soi && permissions.a(u, "mission.valider")) { a.add("approuver"); a.add("rejeter"); }
        if (st.equals("approuvee") && !soi && permissions.a(u, "mission.valider")) a.add("finaliser");
        if (st.equals("finalisee") && !soi && permissions.a(u, "mission.valider_paiement")) a.add("payer");
        if (st.equals("payee") && permissions.a(u, "mission.annuler_paiement")) a.add("annuler_paiement");
        if (!Set.of("payee", "rejetee", "annulee").contains(st) && (soi && Set.of("brouillon", "soumise").contains(st) || permissions.a(u, "mission.corriger"))) a.add("annuler");
        if (Set.of("approuvee", "finalisee", "payee").contains(st) && (soi || permissions.a(u, "mission.etats"))) a.add("telecharger");
        return a;
    }

    private boolean monTour(AuthenticatedUser u, String id) {
        return u.personnelId() != null && chaine.active("mission", id).map(e -> perimeters.effectivePersonnelIds(u.personnelId(), true).contains(e.responsablePersonnelId())).orElse(false);
    }

    private MissionDto dto(AuthenticatedUser u, Map<String, Object> m, boolean detail) {
        List<String> avert = new ArrayList<>();
        if (bool(m.get("retour_non_renseigne"))) avert.add("RETOUR_NON_RENSEIGNE");
        if (bool(m.get("anomalie_depart_arrivee"))) avert.add("DEPART_EGAL_ARRIVEE");
        if (!"aucune".equals(m.get("anomalie_numero"))) avert.add(str(m.get("anomalie_numero")).toUpperCase());
        List<Map<String, Object>> hist = detail ? jdbc.queryForList("SELECT h.seq, h.ancien_statut AS de, h.nouveau_statut AS vers, h.commentaire, h.migre, h.cree_le AS date, CONCAT(p.prenom_fr,' ',p.nom_fr) AS acteur FROM ordre_mission_historique h LEFT JOIN utilisateur ut ON ut.id = h.acteur_id LEFT JOIN personnel p ON p.id = ut.personnel_id WHERE h.ordre_mission_id = ? ORDER BY h.seq", m.get("id")) : null;
        return new MissionDto(str(m.get("id")), str(m.get("personnel_id")), m.get("prenom_fr") + " " + m.get("nom_fr"), str(m.get("matricule")), str(m.get("objet")), str(m.get("statut")),
                str(m.get("numero_ordm")), str(m.get("numero_etat")), str(m.get("numero_ordm_legacy")), str(m.get("numero_etat_legacy")), str(m.get("anomalie_numero")),
                dt(m.get("date_depart")), dt(m.get("date_retour")), str(m.get("depart_nom")), str(m.get("arrivee_nom")), str(m.get("moyen_nom")), str(m.get("programme_nom")),
                bool(m.get("deplacement_remboursable")), ((Number) m.get("nombre_repas_taux_1")).intValue(), ((Number) m.get("nombre_repas_taux_2")).intValue(), dec(m.get("indemnite_taux_1")), dec(m.get("indemnite_taux_2")),
                dec(m.get("montant_indemnites")), dec(m.get("frais_transport")), dec(m.get("distance_km")), m.get("nombre_trajets") == null ? null : ((Number) m.get("nombre_trajets")).intValue(), dec(m.get("taux_km")), dec(m.get("montant_km")), dec(m.get("montant_total")),
                bool(m.get("ecart_moteur_historique")), bool(m.get("retour_non_renseigne")), str(m.get("motif_rejet")), avert, hist, actions(u, m),
                detail ? chaine.liste("mission", str(m.get("id"))) : null);
    }
}
