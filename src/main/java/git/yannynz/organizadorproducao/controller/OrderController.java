    package git.yannynz.organizadorproducao.controller;

    import git.yannynz.organizadorproducao.model.Order;
    import git.yannynz.organizadorproducao.service.OrderService;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.*;

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
        @PutMapping("/{orderId}/status")
        public Optional<Order> updateOrderStatus(@PathVariable Long orderId, @RequestParam int status, @RequestParam String entregador, @RequestParam String observacao) {
            return orderService.updateOrderStatus(orderId, status, entregador, observacao);
        }
    }