package ma.entraide.tawassol.security;

public enum Role {
    AGENT, RESPONSABLE, RH, ADMIN;

    public static Role fromDb(String value) { return valueOf(value.toUpperCase()); }
    public String db() { return name().toLowerCase(); }
}
