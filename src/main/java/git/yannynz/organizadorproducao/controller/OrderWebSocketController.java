package git.yannynz.organizadorproducao.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.event.TransactionalEventListener;

import git.yannynz.organizadorproducao.model.OrderSavedEvent;

@Controller
public class OrderWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener
    public void handleOrderSaved(OrderSavedEvent event) {
        messagingTemplate.convertAndSend("/topic/orders", event.getOrder());
    }
}
