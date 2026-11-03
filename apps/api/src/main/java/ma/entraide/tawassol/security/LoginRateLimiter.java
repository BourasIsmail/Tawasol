package ma.entraide.tawassol.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import ma.entraide.tawassol.config.TawassolProperties;
import org.springframework.stereotype.Component;

/** Limiteur en mémoire par adresse IP. Remplacé par Redis (Bucket4j) lors du durcissement si plusieurs instances. */
@Component
public class LoginRateLimiter {
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final int perMinute;
    private final Clock clock;

    public LoginRateLimiter(TawassolProperties props, Clock clock) {
        this.perMinute = props.security().loginRatePerMinute();
        this.clock = clock;
    }

    public boolean allow(String key) {
        long now = clock.millis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
            if (q.size() >= perMinute) return false;
            q.addLast(now);
            return true;
        }
    }

    public void reset() { hits.clear(); }
}
