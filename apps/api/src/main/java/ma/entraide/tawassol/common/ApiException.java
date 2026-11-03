package ma.entraide.tawassol.common;

import org.springframework.http.HttpStatus;

/** Erreur métier avec un code stable lisible par les clients web et mobile. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ressource introuvable");
    }
}
