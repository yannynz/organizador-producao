package git.yannynz.organizadorproducao.controller;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.service.OrderService;

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

    @GetMapping("/nr/{nr}")
public ResponseEntity<Order> getOrderByNr(@PathVariable String nr) {
    Optional<Order> order = orderService.getOrderByNr(nr);
    return order.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
}

    @PostMapping("/create")
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
    try {
        Order createdOrder = orderService.saveOrder(order); // Salva o pedido no banco de dados
        return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder); // Retorna 201 com o pedido criado

    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build(); // Retorna 400 em caso de erro
    }
}
    @PutMapping("/update/{id}")
    public ResponseEntity<Order> updateOrder(@PathVariable Long id, @RequestBody Order orderDetails) {
        Optional<Order> orderOptional = orderService.getOrderById(id);
        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            order.setNr(orderDetails.getNr());
            order.setCliente(orderDetails.getCliente());
            order.setPrioridade(orderDetails.getPrioridade());
            order.setDataH(orderDetails.getDataH());
            order.setStatus(orderDetails.getStatus());
            order.setVeiculo(orderDetails.getVeiculo());
            order.setDataHRetorno(orderDetails.getDataHRetorno());
            order.setEntregador(orderDetails.getEntregador());
            order.setObservacao(orderDetails.getObservacao());
            order.setDataEntrega(orderDetails.getDataEntrega());
            order.setRecebedor(orderDetails.getRecebedor());


            return ResponseEntity.ok(orderService.saveOrder(order));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/delete/{id}")
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
        }

        // Definir data de entrega como o momento atual
        order.setDataEntrega(ZonedDateTime.now());

        // Atualizar entregador e observação se disponíveis
        if (entregador != null) {
            order.setEntregador(entregador);
        }

        if (observacao != null) {
            order.setObservacao(observacao);
        }

        Order updatedOrder = orderService.saveOrder(order);
        return ResponseEntity.ok(updatedOrder);
    } else {
        return ResponseEntity.notFound().build();
    }
}
}
