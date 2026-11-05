package ma.entraide.tawassol.personnel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

/** Les champs de détail restent absents (null) dans la vue synthétique d'un responsable. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonnelDto(String id, String niveau, String matricule, String nomFr, String prenomFr, String nomAr,
                           String prenomAr, String gradeFr, String gradeAr, String structureId, String structureFr,
                           String structureAr, LocalDate dateRecrutement, boolean actif,
                           String email, String photo, String echelon, Integer echelle, LocalDate dateEchelon, LocalDate dateEchelle,
                           String positionAdministrative, String telephone) {

    public static PersonnelDto synthese(PersonnelRow r) {
        return new PersonnelDto(r.id(), "SYNTHESE", r.matricule(), r.nomFr(), r.prenomFr(), r.nomAr(), r.prenomAr(), r.gradeFr(),
                r.gradeAr(), r.structureId(), r.structureFr(), r.structureAr(), r.dateRecrutement(), r.actif(),
                null, null, null, null, null, null, null, null);
    }

    public static PersonnelDto detail(PersonnelRow r) {
        return new PersonnelDto(r.id(), "DETAIL", r.matricule(), r.nomFr(), r.prenomFr(), r.nomAr(), r.prenomAr(), r.gradeFr(),
                r.gradeAr(), r.structureId(), r.structureFr(), r.structureAr(), r.dateRecrutement(), r.actif(),
                r.email(), r.photo(), r.echelon(), r.echelle(), r.dateEchelon(), r.dateEchelle(), r.positionAdministrative(), r.telephone());
    }
}
