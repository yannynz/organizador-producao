package git.yannynz.organizadorproducao.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_LASER = "laser_notifications";
    public static final String QUEUE_FACAS = "facas_notifications";
    public static final String QUEUE_DXF_ANALYSIS_REQUEST = "facas.analysis.request";
    public static final String QUEUE_DXF_ANALYSIS_RESULT = "facas.analysis.result";

    @Bean
    public Queue laserQueue() {
        return new Queue(QUEUE_LASER, true);
    }

    @Bean
    public Queue facasQueue() {
        return new Queue(QUEUE_FACAS, true);
    }

    @Bean
    public Queue dobraQueue() {
        return new Queue("dobra_notifications", true);
    }

    @Bean
    public Queue dxfAnalysisRequestQueue(DXFAnalysisProperties properties) {
        return new Queue(properties.getRequestQueue(), true);
    }

    @Bean
    public Queue dxfAnalysisResultQueue(DXFAnalysisProperties properties) {
        return new Queue(properties.getResultQueue(), true);
    }
}
