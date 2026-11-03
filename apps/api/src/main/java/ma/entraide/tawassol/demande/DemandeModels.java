package ma.entraide.tawassol.demande;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DemandeModels {
    private DemandeModels() {}

    public record DemandeRequest(@NotBlank @Size(max = 30) String typeCode, @Size(max = 250) String objet, String description,
                                 LocalDate dateDebut, LocalDate dateFin, Map<String, Object> donnees) {}

    public record DemandeRow(String id, String numero, String personnelId, String typeId, String typeCode, String typeLibelle,
                             boolean validationResponsable, String objet, String description, String statut, LocalDate dateDebut,
                             LocalDate dateFin, BigDecimal nbJours, String donneesJson, String commentaireResponsable,
                             String commentaireRh, LocalDateTime creeLe, LocalDateTime modifieLe, String responsablePersonnelId) {}

    public record HistoriqueDto(String ancienStatut, String nouveauStatut, String commentaire, String acteur, LocalDateTime creeLe) {}

    public record PieceDto(String id, String nomFichier, String typeMime, Integer tailleKb, LocalDateTime creeLe) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DemandeDto(String id, String numero, String typeCode, String typeLibelle, String objet, String description,
                             String statut, LocalDate dateDebut, LocalDate dateFin, BigDecimal nbJours, Map<String, Object> donnees,
                             String commentaireResponsable, String commentaireRh, LocalDateTime creeLe, LocalDateTime modifieLe,
                             List<HistoriqueDto> historique, List<PieceDto> piecesJointes, List<String> avertissements,
                             List<Map<String, Object>> chaine) {}
}
