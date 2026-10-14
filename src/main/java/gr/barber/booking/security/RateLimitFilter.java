package gr.barber.booking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits how often one IP address may call the sign-in and account endpoints, so passwords
 * cannot be guessed in bulk and emails cannot be used to spam people. Answers 429 with
 * Retry-After. In memory: limits are per app instance and reset on restart.
 */
// Runs inside the security chain right after CORS (see SecurityConfig), so 429 answers carry
// CORS headers and the browser shows "too many attempts" rather than a network error.
@Component
@ConditionalOnProperty(value = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private record Limit(int requests, Duration window) {
    }

    private static final Map<String, Limit> LIMITS = Map.of(
            "/auth/login", new Limit(10, Duration.ofMinutes(10)),
            "/auth/register", new Limit(5, Duration.ofHours(1)),
            "/auth/forgot-password", new Limit(5, Duration.ofHours(1)),
            "/auth/resend-verification", new Limit(5, Duration.ofHours(1)),
            "/auth/reset-password", new Limit(10, Duration.ofMinutes(10)),
            "/auth/verify-email", new Limit(10, Duration.ofMinutes(10)));

    private static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITS.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        Limit limit = LIMITS.get(path);
        Instant now = clock.instant();
        Instant windowStart = now.minus(limit.window());

        if (hits.size() > MAX_TRACKED_KEYS) {
            hits.values().removeIf(times -> times.isEmpty() || times.peekLast().isBefore(now.minus(Duration.ofHours(1))));
        }

        // The client address comes from the proxy's X-Forwarded-For only when
        // server.forward-headers-strategy is set (behind a trusted proxy), so it cannot be spoofed otherwise.
        Deque<Instant> times = hits.computeIfAbsent(path + "|" + request.getRemoteAddr(), key -> new ArrayDeque<>());
        long retryAfter;
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(windowStart)) {
                times.pollFirst();
            }
            if (times.size() < limit.requests()) {
                times.addLast(now);
                retryAfter = 0;
            } else {
                retryAfter = Math.max(1, Duration.between(now, times.peekFirst().plus(limit.window())).toSeconds());
            }
        }

        if (retryAfter == 0) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType("application/problem+json");
        response.getWriter().write("""
                {"status":429,"title":"Too Many Requests","detail":"Too many attempts. Try again later.","code":"RATE_LIMITED","instance":"%s"}"""
                .formatted(path));
    }
}
