package git.yannynz.organizadorproducao.config; 

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RabbitRpcConfig {

    @Value("${app.rpc.filewatcher.queue}")
    private String rpcQueueName;

    @Value("${app.rpc.filewatcher.timeout-ms:2000}")
    private long replyTimeoutMs;

    @Bean
    public Queue fileWatcherRpcQueue() {
        return new Queue(rpcQueueName, true);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    @Primary
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // Use temporary reply queues to avoid noisy DirectReplyTo container
        // logs when the responder is offline or slow.
        template.setUseTemporaryReplyQueues(true);
        template.setReplyTimeout(replyTimeoutMs);
        return template;
    }
}
