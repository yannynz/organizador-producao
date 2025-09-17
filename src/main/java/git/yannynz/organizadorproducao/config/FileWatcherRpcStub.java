package git.yannynz.organizadorproducao.config;

import git.yannynz.organizadorproducao.model.dto.FileWatcherPong;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Optional local stub that answers the FileWatcher RPC ping.
 * Enable with: app.rpc.filewatcher.stub.enabled=true
 */
@Component
@ConditionalOnProperty(name = "app.rpc.filewatcher.stub.enabled", havingValue = "true")
public class FileWatcherRpcStub {

    private final String instanceId;

    public FileWatcherRpcStub(@Value("${spring.application.name:backend}") String appName) {
        this.instanceId = appName + "-stub";
    }

    @RabbitListener(queues = "${app.rpc.filewatcher.queue}")
    public FileWatcherPong onPing(Map<String, Object> payload) {
        return new FileWatcherPong(true, instanceId, Instant.now().toString(), "stub");
    }
}

