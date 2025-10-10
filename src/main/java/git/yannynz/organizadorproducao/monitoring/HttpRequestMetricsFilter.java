package git.yannynz.organizadorproducao.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

/**
 * Observa requisições HTTP e publica métricas customizadas para latência,
 * volume (RPS) e erros por endpoint.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class HttpRequestMetricsFilter extends OncePerRequestFilter {

    private static final String LATENCY_TIMER = "organizador_http_server_latency_seconds";
    private static final String REQUEST_COUNTER = "organizador_http_server_requests_total";
    private static final String ERROR_COUNTER = "organizador_http_server_errors_total";
    private static final Set<String> EXCLUDED_PREFIXES = Set.of("/actuator/prometheus");
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> requestCounterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> errorCounterCache = new ConcurrentHashMap<>();

    public HttpRequestMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = urlPathHelper.getPathWithinApplication(request);
        return EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        long start = System.nanoTime();
        int statusToRecord = HttpServletResponse.SC_OK;

        try {
            filterChain.doFilter(request, response);
            statusToRecord = response.getStatus();
        } catch (Exception ex) {
            statusToRecord = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            throw ex;
        } finally {
            long durationNanos = System.nanoTime() - start;
            recordMetrics(request, statusToRecord, Duration.ofNanos(durationNanos));
        }
    }

    private void recordMetrics(HttpServletRequest request, int status, Duration duration) {
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null || pattern.isBlank()) {
            pattern = urlPathHelper.getPathWithinApplication(request);
        }

        String method = request.getMethod();
        String outcome = outcomeFromStatus(status);
        String statusCode = String.valueOf(status);
        Tags tags = Tags.of(
            "uri", pattern,
            "method", method,
            "status", statusCode,
            "outcome", outcome
        );

        String meterKey = pattern + "|" + method + "|" + statusCode;
        timerCache
            .computeIfAbsent(meterKey,
                key -> Timer.builder(LATENCY_TIMER)
                    .description("Latência das requisições HTTP")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .publishPercentileHistogram()
                    .tags(tags)
                    .register(meterRegistry))
            .record(duration);

        requestCounterCache
            .computeIfAbsent(meterKey,
                key -> Counter.builder(REQUEST_COUNTER)
                    .description("Total de requisições HTTP por endpoint")
                    .tags(tags)
                    .register(meterRegistry))
            .increment();

        if (status >= 400) {
            String family = status >= 500 ? "5xx" : "4xx";
            Tags errorTags = Tags.of(
                "uri", pattern,
                "method", method,
                "status_family", family
            );

            errorCounterCache
                .computeIfAbsent(pattern + "|" + method + "|" + family,
                    key -> Counter.builder(ERROR_COUNTER)
                        .description("Total de erros HTTP por família 4xx/5xx")
                        .tags(errorTags)
                        .register(meterRegistry))
                .increment();
        }
    }

    private String outcomeFromStatus(int status) {
        if (status >= 200 && status < 300) {
            return "SUCCESS";
        }
        if (status >= 300 && status < 400) {
            return "REDIRECTION";
        }
        if (status >= 400 && status < 500) {
            return "CLIENT_ERROR";
        }
        if (status >= 500) {
            return "SERVER_ERROR";
        }
        return "UNKNOWN";
    }
}
