package ma.entraide.tawassol.access;

import java.util.Set;

/** Ce qu'un utilisateur peut atteindre parmi le personnel : tout, son périmètre de structures, ou lui seul. */
public record Scope(boolean all, Set<String> structureIds, String selfPersonnelId) {
    public boolean covers(String personnelId, String structureId) {
        if (all) return true;
        if (selfPersonnelId != null && selfPersonnelId.equals(personnelId)) return true;
        return structureId != null && structureIds.contains(structureId);
    }
}
