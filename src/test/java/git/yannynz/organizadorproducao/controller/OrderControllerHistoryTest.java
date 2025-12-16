package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.service.OrderHistoryService;
import git.yannynz.organizadorproducao.service.OrderService;
import git.yannynz.organizadorproducao.service.OpImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class OrderControllerHistoryTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderHistoryService orderHistoryService;

    @Mock
    private OpImportService opImportService; // Mock needed because controller calls it

    @InjectMocks
    private OrderController orderController;

    @Test
    public void shouldLogHistoryWhenStatusChanges() {
        // Arrange
        Order existing = new Order();
        existing.setId(1L);
        existing.setStatus(0); // Existing status

        when(orderService.getOrderById(1L)).thenReturn(Optional.of(existing));

        // Act
        // status=1 (Changed)
        orderController.updateOrderStatus(1L, 1, null, null);

        // Assert
        verify(orderHistoryService).logChange(eq(existing), eq("STATUS"), eq("0"), eq("1"));
        verify(orderService).saveOrder(existing);
    }

    @Test
    public void shouldLogHistoryWhenPriorityChangesInUpdateOrder() {
        // Arrange
        Order existing = new Order();
        existing.setId(1L);
        existing.setNr("1234");
        existing.setPrioridade("VERMELHO");

        Order updatePayload = new Order();
        updatePayload.setNr("1234");
        updatePayload.setPrioridade("AZUL"); // Changed

        when(orderService.getOrderById(1L)).thenReturn(Optional.of(existing));

        // Act
        orderController.updateOrder(1L, updatePayload);

        // Assert
        verify(orderHistoryService).logChange(eq(existing), eq("PRIORIDADE"), eq("VERMELHO"), eq("AZUL"));
        verify(orderService).saveOrder(existing);
    }
}
