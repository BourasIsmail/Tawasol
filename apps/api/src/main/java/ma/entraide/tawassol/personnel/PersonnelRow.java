package ma.entraide.tawassol.personnel;

import java.time.LocalDate;

public record PersonnelRow(String id, String matricule, String nomFr, String prenomFr, String nomAr, String prenomAr,
                           String gradeFr, String gradeAr, String structureId, String structureFr, String structureAr,
                           LocalDate dateRecrutement, String email, String photo, String echelon, Integer echelle,
                           LocalDate dateEchelon, boolean actif, String positionAdministrative, String telephone) {}
