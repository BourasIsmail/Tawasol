package ma.entraide.tawassol.deplacement;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class MissionModels {
    private MissionModels() {}

    /** Saisie d'une mission. personnelId est facultatif : absent = l'agent connecté ; présent = saisie pour un agent du périmètre. */
    public record MissionRequest(String personnelId, @NotBlank String objet, String programmeId, String departDelegationId, @NotBlank String arrivalDelegationId,
                                 String moyenTransportId, LocalDateTime dateDepart, LocalDateTime dateRetour, Boolean remboursable,
                                 BigDecimal fraisTransport, String vehiculeImmatriculation, String vehiculeNom, String puissanceFiscaleId,
                                 BigDecimal distanceKm, Integer nombreTrajets) {}

    /** Données réelles saisies à la finalisation (après le retour). */
    public record FinalisationRequest(LocalDateTime dateDepart, LocalDateTime dateRetour, BigDecimal fraisTransport, BigDecimal distanceKm, Integer nombreTrajets, String commentaire) {}

    public record MotifRequest(String commentaire) {}

    public record MissionDto(String id, String personnelId, String agentNom, String matricule, String objet, String statut, String numeroOrdm, String numeroEtat,
                             String numeroOrdmLegacy, String numeroEtatLegacy, String anomalieNumero, LocalDateTime dateDepart, LocalDateTime dateRetour,
                             String departDelegation, String arrivalDelegation, String moyenTransport, String programme, boolean remboursable,
                             int repasTaux1, int repasTaux2, BigDecimal indemniteTaux1, BigDecimal indemniteTaux2, BigDecimal montantIndemnites,
                             BigDecimal fraisTransport, BigDecimal distanceKm, Integer nombreTrajets, BigDecimal tauxKm, BigDecimal montantKm, BigDecimal montantTotal,
                             boolean historiqueNonReproductible, boolean retourNonRenseigne, String motifRejet, List<String> avertissements,
                             List<Map<String, Object>> historique, List<String> actionsPossibles, List<Map<String, Object>> chaine) {}
}
