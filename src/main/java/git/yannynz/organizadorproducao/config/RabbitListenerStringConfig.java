package git.yannynz.organizadorproducao.config;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitListenerStringConfig {

   @Bean
    public SimpleRabbitListenerContainerFactory stringListenerFactory(ConnectionFactory cf) {
        var f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setMessageConverter(new SimpleMessageConverter());
        // (opcional) tuning:
        // f.setConcurrentConsumers(1);
        // f.setMaxConcurrentConsumers(4);
        return f;
    }
}

