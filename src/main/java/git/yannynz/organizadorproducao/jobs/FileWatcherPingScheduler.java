package git.yannynz.organizadorproducao.jobs;

import git.yannynz.organizadorproducao.model.dto.StatusEvent;
import git.yannynz.organizadorproducao.service.FileWatcherPingClient;
import git.yannynz.organizadorproducao.service.StatusWsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@EnableScheduling
public class FileWatcherPingScheduler {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherPingScheduler.class);
    private final FileWatcherPingClient client;
    private final StatusWsPublisher publisher;

    public FileWatcherPingScheduler(FileWatcherPingClient client, StatusWsPublisher publisher) {
        this.client = client;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 5_000)
    public void tick() {
        var res = client.pingNow();
        log.debug("FileWatcher status: online={} latencyMs={} instanceId={}",
                res.online(), res.latencyMs(), res.instanceId());
        var evt = new StatusEvent(
                "filewatcher",
                res.online(),
                res.latencyMs(),
                Instant.now().toString(),
                res.lastSeenTs(),
                res.instanceId(),
                res.version(),
                "rpc"
        );
        publisher.publish(evt);
    }
}

