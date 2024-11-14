package git.yannynz.organizadorproducao.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.service.OrderService;

@Controller
public class OrderWebSocketController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private List<String> orders = new ArrayList<>();

    @MessageMapping("/orders")
    @SendTo("/topic/orders")
    public List<Order> getAllOrders() {
        return orderService.getAllOrders();
    }

    @MessageMapping("/orders/{id}")
    @SendTo("/topic/orders/{id}")
    public Order getOrderById(Long id) {
        return orderService.getOrderById(id).orElse(null);
    }

    @MessageMapping("/orders/create")
    @SendTo("/topic/orders")
    public Order createOrder(Order order) {
        System.out.println("Pedido criado e enviado via WebSocket: " + order);
        return orderService.saveOrder(order);
    }

    @MessageMapping("/orders/update")
    @SendTo("/topic/orders")
    public Order updateOrder(Order order) {
        Optional<Order> existingOrder = orderService.getOrderById(order.getId());
        if (existingOrder.isPresent()) {
            Order updatedOrder = existingOrder.get();
            updatedOrder.setNr(order.getNr());
            updatedOrder.setCliente(order.getCliente());
            updatedOrder.setPrioridade(order.getPrioridade());
            updatedOrder.setStatus(order.getStatus());
            updatedOrder.setDataEntrega(order.getDataEntrega());
            updatedOrder.setEntregador(order.getEntregador());
            updatedOrder.setObservacao(order.getObservacao());

            Order savedOrder = orderService.saveOrder(updatedOrder);
            System.out.println("Pedido atualizado: " + savedOrder);
            return savedOrder;
        }
        return null;
    }

    @MessageMapping("/orders/delete/{id}")
    @SendTo("/topic/orders")
    public Long deleteOrder(Long id) {
        orderService.deleteOrder(id);
        System.out.println("Pedido deletado via WebSocket: " + id);
        return id;
    }

    @MessageMapping("/orders/clear")
    @SendTo("/topic/orders")
    public void clearDatabase() {
        orderService.deleteAllOrders();
    }
}
