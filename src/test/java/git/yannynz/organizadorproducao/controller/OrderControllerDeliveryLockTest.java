package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.domain.user.User;
import git.yannynz.organizadorproducao.domain.user.UserRole;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.service.OpImportService;
import git.yannynz.organizadorproducao.service.OrderHistoryService;
import git.yannynz.organizadorproducao.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class OrderControllerDeliveryLockTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OpImportService opImportService;

    @Mock
    private OrderHistoryService orderHistoryService;

    @InjectMocks
    private OrderController orderController;

    private User operatorUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        operatorUser = User.builder()
                .id(1L)
                .name("Operator")
                .email("op@example.com")
                .role(UserRole.OPERADOR)
                .build();
        
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(operatorUser, null, operatorUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void updateOrder_ShouldBlock_WhenUserHasActiveDelivery_AndTargetIsDifferent() {
        // Arrange
        Long targetOrderId = 100L;
        Long deliveryOrderId = 200L;
        
        Order deliveryOrder = new Order();
        deliveryOrder.setId(deliveryOrderId);
        deliveryOrder.setNr("200");
        deliveryOrder.setStatus(3); // Saiu para entrega

        Order updateDetails = new Order();
        updateDetails.setNr("100");

        // User has an active delivery (order 200)
        when(orderService.findActiveDeliveriesByUser(operatorUser.getName())).thenReturn(List.of(deliveryOrder));
        
        // Act
        ResponseEntity<?> response = orderController.updateOrder(targetOrderId, updateDetails);

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Usuário com entrega ativa não pode realizar outras tarefas na fábrica.", response.getBody());
    }

    @Test
    void updateOrder_ShouldAllow_WhenUserHasActiveDelivery_AndTargetIsTheDeliveryOrder() {
        // Arrange
        Long targetOrderId = 200L; // Updating the same order being delivered
        
        Order deliveryOrder = new Order();
        deliveryOrder.setId(targetOrderId);
        deliveryOrder.setNr("200");
        deliveryOrder.setStatus(3);

        Order updateDetails = new Order();
        updateDetails.setNr("200");
        
        // Existing order
        when(orderService.getOrderById(targetOrderId)).thenReturn(Optional.of(deliveryOrder));
        when(orderService.saveOrder(any(Order.class))).thenReturn(deliveryOrder);

        // User has this active delivery
        when(orderService.findActiveDeliveriesByUser(operatorUser.getName())).thenReturn(List.of(deliveryOrder));
        
        // Act
        ResponseEntity<?> response = orderController.updateOrder(targetOrderId, updateDetails);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void updateOrder_ShouldAllow_WhenUserHasNoActiveDelivery() {
        // Arrange
        Long targetOrderId = 100L;
        Order updateDetails = new Order();
        updateDetails.setNr("100");
        Order existingOrder = new Order();
        existingOrder.setId(100L);

        when(orderService.findActiveDeliveriesByUser(operatorUser.getName())).thenReturn(Collections.emptyList());
        when(orderService.getOrderById(targetOrderId)).thenReturn(Optional.of(existingOrder));
        when(orderService.saveOrder(any(Order.class))).thenReturn(existingOrder);

        // Act
        ResponseEntity<?> response = orderController.updateOrder(targetOrderId, updateDetails);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
    
    @Test
    void updateOrderStatus_ShouldBlock_WhenUserHasActiveDelivery_AndTargetIsDifferent() {
         // Arrange
        Long targetOrderId = 100L;
        Long deliveryOrderId = 200L;
        
        Order deliveryOrder = new Order();
        deliveryOrder.setId(deliveryOrderId);
        
        when(orderService.findActiveDeliveriesByUser(operatorUser.getName())).thenReturn(List.of(deliveryOrder));
        
        // Act
        ResponseEntity<?> response = orderController.updateOrderStatus(targetOrderId, 4, null, null);
        
        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
