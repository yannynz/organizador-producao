package git.yannynz.organizadorproducao.health; 

import git.yannynz.organizadorproducao.service.FileWatcherPingClient; 
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class FileWatcherHealthIndicator implements HealthIndicator {

    private final FileWatcherPingClient client;

    public FileWatcherHealthIndicator(FileWatcherPingClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        var result = client.pingNow();
        if (result.online()) {
            return Health.up()
                    .withDetail("instanceId", result.instanceId())
                    .withDetail("latencyMs", result.latencyMs())
                    .withDetail("lastSeenTs", result.lastSeenTs())
                    .build();
        }
        return Health.down()
                .withDetail("latencyMs", result.latencyMs())
                .withDetail("reason", "No pong within timeout")
                .build();
    }
}

