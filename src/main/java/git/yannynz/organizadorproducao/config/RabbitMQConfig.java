package git.yannynz.organizadorproducao.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String QUEUE_LASER = "laserQueue";
    public static final String QUEUE_FACAS = "facasOkQueue";

    @Bean
    public Queue laserQueue() {
        return new Queue(QUEUE_LASER, true);
    }

    @Bean
    public Queue facasQueue() {
        return new Queue(QUEUE_FACAS, true);
    }
}

