package ma.entraide.tawassol.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int status, String code, String message, List<String> details, Instant timestamp) {
    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, null, Instant.now());
    }
}
