package git.yannynz.organizadorproducao.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Publica métricas sobre sessões ativas no WebSocket STOMP.
 */
@Component
public class WebSocketMetrics {

    private static final String SESSION_GAUGE = "organizador_websocket_active_sessions";

    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private final Map<String, Boolean> sessions = new ConcurrentHashMap<>();

    public WebSocketMetrics(MeterRegistry meterRegistry) {
        Gauge.builder(SESSION_GAUGE, activeSessions, AtomicInteger::get)
            .description("Total de conexões WebSocket STOMP ativas")
            .register(meterRegistry);
    }

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId != null && sessions.putIfAbsent(sessionId, Boolean.TRUE) == null) {
            activeSessions.incrementAndGet();
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId != null && sessions.remove(sessionId) != null) {
            activeSessions.decrementAndGet();
        }
    }
}
