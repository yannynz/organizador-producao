package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.dto.StatusEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StatusWsPublisher {

    private final SimpMessagingTemplate template;

    public StatusWsPublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void publish(StatusEvent event) {
        template.convertAndSend("/topic/status", event);
    }
}

