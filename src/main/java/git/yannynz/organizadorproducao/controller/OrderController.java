package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

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

    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(@PathVariable Long id, @RequestBody Order orderDetails) {
        Optional<Order> orderOptional = orderService.getOrderById(id);
        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            order.setNr(orderDetails.getNr());
            order.setCliente(orderDetails.getCliente());
            order.setPrioridade(orderDetails.getPrioridade());
            order.setDataH(orderDetails.getDataH());

            return ResponseEntity.ok(orderService.saveOrder(order));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearDatabase() {
        orderService.deleteAllOrders();
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
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
            return ResponseEntity.ok(updatedOrder);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

}