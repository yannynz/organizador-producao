package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.model.dto.FileCommandDTO;
import git.yannynz.organizadorproducao.service.FileCommandPublisher;
import git.yannynz.organizadorproducao.service.OrderHistoryService;
import git.yannynz.organizadorproducao.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrderControllerPriorityTest {

    @Mock
    private OrderService orderService;

    @Mock
    private FileCommandPublisher fileCommandPublisher;

    @Mock
    private OrderHistoryService orderHistoryService;

    @InjectMocks
    private OrderController orderController;

    @Test
    public void shouldUpdatePriorityAndPublishCommand() {
        Order order = new Order();
        order.setId(1L);
        order.setNr("1234");
        order.setPrioridade("VERMELHO");

        when(orderService.getOrderById(1L)).thenReturn(Optional.of(order));

        orderController.patchPriority(1L, Map.of("priority", "AZUL"));

        verify(orderService).saveOrder(any(Order.class));
        verify(fileCommandPublisher).sendCommand(any(FileCommandDTO.class));
    }
}
