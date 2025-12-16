package git.yannynz.organizadorproducao.controller;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import git.yannynz.organizadorproducao.domain.user.User;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.service.OrderService;
import git.yannynz.organizadorproducao.service.OpImportService;
import git.yannynz.organizadorproducao.service.OrderStatusRules;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private git.yannynz.organizadorproducao.service.FileCommandPublisher fileCommandPublisher;

    @Autowired
    private git.yannynz.organizadorproducao.service.OrderHistoryService orderHistoryService;

    @Autowired
    private OpImportService opImportService;

    @GetMapping("/{id}/history")
    public ResponseEntity<List<git.yannynz.organizadorproducao.model.OrderHistory>> getOrderHistory(@PathVariable Long id) {
        return ResponseEntity.ok(orderHistoryService.getHistory(id));
    }

    // ... existing validateUserAvailability ...

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/priority")
    public ResponseEntity<?> patchPriority(@PathVariable Long id, @RequestBody java.util.Map<String, String> payload) {
        String newPriority = payload.get("priority");
        if (newPriority == null || newPriority.isBlank()) {
            return ResponseEntity.badRequest().body("Priority is required");
        }

        Optional<Order> orderOpt = orderService.getOrderById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = orderOpt.get();
        String oldPriority = order.getPrioridade();
        
        if (!Objects.equals(oldPriority, newPriority)) {
            // Update DB
            order.setPrioridade(newPriority);
            orderService.saveOrder(order);

            // Log History
            orderHistoryService.logChange(order, "PRIORIDADE", oldPriority, newPriority);

            // Publish Command to C# Service
            git.yannynz.organizadorproducao.model.dto.FileCommandDTO command = git.yannynz.organizadorproducao.model.dto.FileCommandDTO.builder()
                    .action("RENAME_PRIORITY")
                    .nr(order.getNr())
                    .newPriority(newPriority)
                    .directory("LASER") 
                    .build();
            
            fileCommandPublisher.sendCommand(command);
        }

        return ResponseEntity.ok(order);
    }

    private void validateUserAvailability(Long targetOrderId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            List<Order> activeDeliveries = orderService.findActiveDeliveriesByUser(user.getName());
            if (!activeDeliveries.isEmpty()) {
                boolean isTargetOrderActive = activeDeliveries.stream()
                        .anyMatch(o -> Objects.equals(o.getId(), targetOrderId));
                
                if (!isTargetOrderActive) {
                    throw new RuntimeException("Usuário com entrega ativa não pode realizar outras tarefas na fábrica.");
                }
            }
        }
    }

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
            // Check if user can create order (generally yes, but if strictly factory task...)
            // Assuming creating order is not "factory task" blocked by delivery, or user creating is Admin/Desenhista.
            // If operator can create, maybe block. But let's stick to updates for now.
            Order createdOrder = orderService.saveOrder(order);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateOrder(@PathVariable Long id, @RequestBody Order orderDetails) {
        try {
            validateUserAvailability(id);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }

        Optional<Order> orderOptional = orderService.getOrderById(id);
        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            boolean prevEmborrachada = order.isEmborrachada();
            boolean prevPertinax = order.isPertinax();
            boolean prevPoliester = order.isPoliester();
            boolean prevPapelCalibrado = order.isPapelCalibrado();
            boolean prevVaiVinco = order.isVaiVinco();

            String oldPriority = order.getPrioridade();
            int oldStatus = order.getStatus();

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
            order.setMontador(orderDetails.getMontador());
            order.setDataMontagem(orderDetails.getDataMontagem());
            order.setEmborrachador(orderDetails.getEmborrachador());
            order.setDataEmborrachamento(orderDetails.getDataEmborrachamento());
            order.setEmborrachada(orderDetails.isEmborrachada());
            order.setDataCortada(orderDetails.getDataCortada());
            order.setDataTirada(orderDetails.getDataTirada());
            // extras
            if (orderDetails.getDestacador() != null) order.setDestacador(orderDetails.getDestacador());
            if (orderDetails.getModalidadeEntrega() != null) order.setModalidadeEntrega(orderDetails.getModalidadeEntrega());
            if (orderDetails.getDataRequeridaEntrega() != null) order.setDataRequeridaEntrega(orderDetails.getDataRequeridaEntrega());
            if (orderDetails.getUsuarioImportacao() != null) order.setUsuarioImportacao(orderDetails.getUsuarioImportacao());
            order.setPertinax(orderDetails.isPertinax());
            order.setPoliester(orderDetails.isPoliester());
            order.setPapelCalibrado(orderDetails.isPapelCalibrado());
            order.setVaiVinco(orderDetails.isVaiVinco());
            order.setVincador(orderDetails.getVincador());
            order.setDataVinco(orderDetails.getDataVinco());

            if (!Objects.equals(oldPriority, orderDetails.getPrioridade())) {
                orderHistoryService.logChange(order, "PRIORIDADE", oldPriority, orderDetails.getPrioridade());
            }
            if (oldStatus != orderDetails.getStatus()) {
                orderHistoryService.logChange(order, "STATUS", String.valueOf(oldStatus), String.valueOf(orderDetails.getStatus()));
            }

            OrderStatusRules.applyAutoProntoEntrega(order);

            opImportService.applyManualLocksForOrder(
                order,
                prevEmborrachada,
                prevPertinax,
                prevPoliester,
                prevPapelCalibrado,
                prevVaiVinco);

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
public ResponseEntity<?> updateOrderStatus(
        @PathVariable Long id, 
        @RequestParam(value = "status", required = false) Integer status,
        @RequestParam(value = "entregador", required = false) String entregador,
        @RequestParam(value = "observacao", required = false) String observacao) {

    try {
        validateUserAvailability(id);
    } catch (RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    Optional<Order> optionalOrder = orderService.getOrderById(id);
    
    if (optionalOrder.isPresent()) {
        Order order = optionalOrder.get();

        if (status != null && order.getStatus() != status) {
            String oldStatus = String.valueOf(order.getStatus());
            order.setStatus(status);
            orderHistoryService.logChange(order, "STATUS", oldStatus, String.valueOf(status));
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

@PostMapping("/search-cursor")
public ResponseEntity<?> searchDeliveredCursor(
        @RequestParam(name = "limit", defaultValue = "50") int limit,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(name = "strategy", required = false) String strategyParam,
        @RequestBody(required = false) git.yannynz.organizadorproducao.model.dto.OrderSearchDTO filters
) {
    // 1) Limita o page size (1..200)
    int pageSize = Math.max(1, Math.min(limit, 200));

    // 2) Strategy com tolerância a nulos/brancos e case-insensitive
    git.yannynz.organizadorproducao.config.pagination.CursorStrategy strategy =
            git.yannynz.organizadorproducao.config.pagination.CursorStrategy.ID;
    if (strategyParam != null && !strategyParam.isBlank()) {
        try {
            strategy = git.yannynz.organizadorproducao.config.pagination.CursorStrategy
                    .valueOf(strategyParam.trim().toUpperCase());
        } catch (IllegalArgumentException ignore) {
            // fica no default ID
        }
    }

    // 3) Normaliza o cursor: trata "null"/"undefined"/vazio como null
    String normalizedCursor = (cursor == null) ? null : cursor.trim();
    if (normalizedCursor != null &&
        (normalizedCursor.isEmpty()
         || "null".equalsIgnoreCase(normalizedCursor)
         || "undefined".equalsIgnoreCase(normalizedCursor))) {
        normalizedCursor = null;
    }

    // 4) Decodifica o cursor (gera 400 apenas se realmente for inválido)
    git.yannynz.organizadorproducao.config.pagination.CursorPaging.Key after;
    try {
        after = git.yannynz.organizadorproducao.config.pagination.CursorPaging.decode(normalizedCursor);
    } catch (IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body("cursor inválido (use exatamente o 'nextCursor' retornado pela API)");
    }

    // 5) Assegura filters != null
    if (filters == null) {
        filters = new git.yannynz.organizadorproducao.model.dto.OrderSearchDTO();
    }

    // 6) Executa a busca e monta o envelope
    var result = orderService.searchDeliveredByCursor(filters, pageSize, after, strategy);
    String nextCursor = (result.lastKey() == null)
            ? null
            : git.yannynz.organizadorproducao.config.pagination.CursorPaging.encode(result.lastKey());

    var envelope = new git.yannynz.organizadorproducao.config.pagination.CursorPaging.PageEnvelope<>(
            result.items(), nextCursor, result.hasMore());

    return ResponseEntity.ok(envelope);
}




}
