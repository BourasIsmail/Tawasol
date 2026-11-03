package ma.entraide.tawassol.personnel;

import java.util.List;

public record PageResult<T>(List<T> content, long total, int page, int size) {}
