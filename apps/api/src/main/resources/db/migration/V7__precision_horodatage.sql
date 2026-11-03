-- V7 : horodatage à la milliseconde pour les journaux ordonnés (historique de demande, notifications, audit).
-- Sans cela, deux transitions dans la même seconde ne se départagent pas.
ALTER TABLE demande_historique MODIFY COLUMN cree_le datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
ALTER TABLE notification MODIFY COLUMN cree_le datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
ALTER TABLE journal_audit MODIFY COLUMN cree_le datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);

-- Ordre d'insertion garanti pour l'historique d'une demande, même si deux transitions ont le même horodatage.
ALTER TABLE demande_historique ADD COLUMN seq bigint NOT NULL AUTO_INCREMENT, ADD UNIQUE KEY uq_dh_seq (seq);
