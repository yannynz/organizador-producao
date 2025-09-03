package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.model.dto.StatusEvent;
import git.yannynz.organizadorproducao.service.FileWatcherPingClient;
import git.yannynz.organizadorproducao.service.StatusWsPublisher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;

@Controller
public class StatusWsController {

    private final FileWatcherPingClient pingClient;
    private final StatusWsPublisher publisher;

    public StatusWsController(FileWatcherPingClient pingClient, StatusWsPublisher publisher) {
        this.pingClient = pingClient;
        this.publisher = publisher;
    }

    // Cliente envia STOMP para /app/status/ping-now
    @MessageMapping("/status/ping-now")
    public void pingNow() {
        var res = pingClient.pingNow();
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

