package git.yannynz.organizadorproducao.service; 

import git.yannynz.organizadorproducao.model.dto.FileWatcherPong; 
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class FileWatcherPingClient {

    private final RabbitTemplate rabbitTemplate;
    private final String rpcQueue;
    private final long timeoutMs;

    public FileWatcherPingClient(RabbitTemplate rabbitTemplate,
                                 @Value("${app.rpc.filewatcher.queue}") String rpcQueue,
                                 @Value("${app.rpc.filewatcher.timeout-ms:2000}") long timeoutMs) {
        this.rabbitTemplate = rabbitTemplate;
        this.rpcQueue = rpcQueue;
        this.timeoutMs = timeoutMs;
    }

    public PingResult pingNow() {
        long start = System.nanoTime();

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ping");
        payload.put("ts", Instant.now().toString());
        payload.put("source", "backend");

        Object response = rabbitTemplate.convertSendAndReceive(rpcQueue, payload);

        long end = System.nanoTime();
        long latencyMs = Math.max(0, (end - start) / 1_000_000);

        if (response == null) {
            return PingResult.offline(latencyMs);
        }

        if (response instanceof FileWatcherPong pong) {
            return PingResult.online(latencyMs, pong.getInstanceId(), pong.getTs(), pong.getVersion());
        }

        if (response instanceof Map<?, ?> map) {
            boolean ok = Boolean.TRUE.equals(map.get("ok"));
            String inst = map.get("instanceId") != null ? map.get("instanceId").toString() : null;
            String ts = map.get("ts") != null ? map.get("ts").toString() : null;
            String ver = map.get("version") != null ? map.get("version").toString() : null;
            if (ok) {
                return PingResult.online(latencyMs, inst, ts, ver);
            }
        }

        return PingResult.offline(latencyMs);
    }

    public record PingResult(boolean online, long latencyMs, String lastSeenTs, String instanceId, String version) {
        static PingResult offline(long latencyMs) {
            return new PingResult(false, latencyMs, null, null, null);
        }
        static PingResult online(long latencyMs, String instanceId, String ts, String version) {
            return new PingResult(true, latencyMs, ts, instanceId, version);
        }
    }
}

