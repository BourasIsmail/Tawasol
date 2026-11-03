package ma.entraide.tawassol.security;

/** Utilisateur authentifié de la requête courante. Le rôle vient de la base, jamais du jeton. */
public record AuthenticatedUser(String userId, String personnelId, Role role, boolean mustChangePassword) {}
