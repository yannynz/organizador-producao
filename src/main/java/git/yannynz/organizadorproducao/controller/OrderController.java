package git.yannynz.organizadorproducao.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public List<Order> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        Optional<Order> order = orderService.getOrderById(id);
        return order.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public Order createOrder(@RequestBody Order order) {
        return orderService.saveOrder(order);
    }

    @MessageMapping("/send/message")
    @SendTo("/topic/orders")
    public String handleMessage(String message) {
        return message;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(@PathVariable Long id, @RequestBody Order orderDetails) {
        Optional<Order> orderOptional = orderService.getOrderById(id);
        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            order.setNr(orderDetails.getNr());
            order.setCliente(orderDetails.getCliente());
            order.setPrioridade(orderDetails.getPrioridade());
            order.setDataH(orderDetails.getDataH());

            Order updatedOrder = orderService.saveOrder(order);
            messagingTemplate.convertAndSend("/topic/orders", updatedOrder); // Envia a atualização via WebSocket
            return ResponseEntity.ok(updatedOrder);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearDatabase() {
        orderService.deleteAllOrders();
        messagingTemplate.convertAndSend("/topic/orders", "clear"); // Envia a atualização via WebSocket
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        messagingTemplate.convertAndSend("/topic/orders", "delete-" + id); // Envia a atualização via WebSocket
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "entregador", required = false) String entregador,
            @RequestParam(value = "observacao", required = false) String observacao) {

        Optional<Order> optionalOrder = orderService.getOrderById(id);
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            if (status != null) {
                order.setStatus(status);
                if (status == 1) {
                    order.setDataEntrega(LocalDateTime.now());
                    if (entregador != null) {
                        order.setEntregador(entregador);
                    }
                    if (observacao != null) {
                        order.setObservacao(observacao);
                    }
                }
            }
            Order updatedOrder = orderService.saveOrder(order);
            messagingTemplate.convertAndSend("/topic/orders", updatedOrder); // Envia a atualização via WebSocket
            return ResponseEntity.ok(updatedOrder);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
