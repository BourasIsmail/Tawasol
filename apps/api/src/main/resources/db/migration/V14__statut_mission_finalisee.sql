-- Circuit : le numéro d'ordre est attribué à l'approbation (avant le déplacement), le numéro d'état à la finalisation
-- (après le retour, avec les dates, heures et repas réels), puis le paiement. D'où un statut distinct « finalisee ».
ALTER TABLE ordre_mission
  MODIFY statut ENUM('brouillon','soumise','validee_responsable','approuvee','finalisee','payee','rejetee','annulee') NOT NULL DEFAULT 'soumise';
