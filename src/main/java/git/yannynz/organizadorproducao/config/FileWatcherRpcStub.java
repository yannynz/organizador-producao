package git.yannynz.organizadorproducao.config;

import java.time.Instant;
import java.util.Map;

import git.yannynz.organizadorproducao.model.dto.FileWatcherPong;
import git.yannynz.organizadorproducao.monitoring.MessageProcessingMetrics;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Optional local stub that answers the FileWatcher RPC ping.
 * Enable with: app.rpc.filewatcher.stub.enabled=true
 */
@Component
@ConditionalOnProperty(name = "app.rpc.filewatcher.stub.enabled", havingValue = "true")
public class FileWatcherRpcStub {

    private final String instanceId;
    private final String metricsQueue;
    private final MessageProcessingMetrics messageProcessingMetrics;

    public FileWatcherRpcStub(
        @Value("${spring.application.name:backend}") String appName,
        @Value("${app.rpc.filewatcher.queue}") String metricsQueue,
        MessageProcessingMetrics messageProcessingMetrics
    ) {
        this.instanceId = appName + "-stub";
        this.metricsQueue = metricsQueue;
        this.messageProcessingMetrics = messageProcessingMetrics;
    }

    @RabbitListener(queues = "${app.rpc.filewatcher.queue}")
    public FileWatcherPong onPing(Map<String, Object> payload) throws Exception {
        return messageProcessingMetrics.recordProcessing(metricsQueue,
            () -> new FileWatcherPong(true, instanceId, Instant.now().toString(), "stub"));
    }
}
