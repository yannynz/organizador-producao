package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.service.OrderService;
import git.yannynz.organizadorproducao.service.WebSocketMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Optional;

@Controller
public class PriorityWebSocketController {

    @Autowired
    private OrderService orderService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    
    @MessageMapping("/prioridades")
    public void getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        messagingTemplate.convertAndSend("/topic/prioridades", orders);
    }

   
    @MessageMapping("/prioridades/update")
    public void updateOrderPriority(Order order) {
        Optional<Order> existingOrder = orderService.getOrderById(order.getId());
        if (existingOrder.isPresent()) {
            Order updatedOrder = existingOrder.get();
            updatedOrder.setPrioridade(order.getPrioridade());

            Order savedOrder = orderService.saveOrder(updatedOrder);
            System.out.println("Prioridade atualizada: " + savedOrder);

            // Enviar atualização via WebSocket para o tópico /topic/prioridades
            WebSocketMessage message = new WebSocketMessage("update", savedOrder);
            messagingTemplate.convertAndSend("/topic/prioridades", message);
        }
    }
}

