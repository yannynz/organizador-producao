package git.yannynz.organizadorproducao.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.model.dto.OpImportRequestDTO;
import git.yannynz.organizadorproducao.service.OpImportService;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class AmqpConfig {

    @Bean
    TopicExchange opExchange() {
        return new TopicExchange("op.exchange", true, false);
    }

    @Bean
    Queue opImportedQueue() {
        return new Queue("op.imported", true);
    }

    @Bean
    Binding binding(Queue opImportedQueue, TopicExchange opExchange) {
        return BindingBuilder.bind(opImportedQueue).to(opExchange).with("op.imported");
    }
}

@Component
class OpImportedListener {

    private final OpImportService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpImportedListener.class);

    public OpImportedListener(OpImportService service) {
        this.service = service;
    }

    @RabbitListener(queues = "op.imported")
    public void onMessage(String json) throws Exception {
        log.info("[AMQP] op.imported received bytes={} payloadSample={}...",
            (json == null ? 0 : json.length()), (json == null ? null : json.substring(0, Math.min(120, json.length()))));
        try {
            var req = mapper.readValue(json, OpImportRequestDTO.class);
            log.info("[AMQP] parsed: numeroOp={}, dataOp={}", req.getNumeroOp(), req.getDataOp());
            service.importar(req);
        } catch (Exception ex) {
            log.error("[AMQP] error parsing/dispatching message: {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
