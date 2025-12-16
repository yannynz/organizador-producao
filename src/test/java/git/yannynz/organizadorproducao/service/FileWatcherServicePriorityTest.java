package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.monitoring.MessageProcessingMetrics;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FileWatcherServicePriorityTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DestacadorMonitorService destacadorMonitorService;

    @Mock
    private MessageProcessingMetrics messageProcessingMetrics;

    @InjectMocks
    private FileWatcherService fileWatcherService;

    @Test
    public void shouldUpdatePriorityWhenFileRenamed() throws Exception {
        // Arrange
        String initialJson = "{\"file_name\": \"NR1234CLIENTE_VERMELHO.CNC\"}";
        String renamedJson = "{\"file_name\": \"NR1234CLIENTE_AZUL.CNC\"}";

        Order existingOrder = new Order();
        existingOrder.setNr("1234");
        existingOrder.setPrioridade("VERMELHO");

        // Mocking behavior for the processing block
        // Since recordProcessing takes a Runnable, we need to execute it immediately
        doAnswer(invocation -> {
            MessageProcessingMetrics.ThrowingRunnable runnable = invocation.getArgument(1);
            try {
                runnable.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(messageProcessingMetrics).recordProcessing(anyString(), any(MessageProcessingMetrics.ThrowingRunnable.class));

        when(orderRepository.findByNr("1234")).thenReturn(Optional.of(existingOrder));

        // Act
        fileWatcherService.handleLaserQueue(renamedJson);

        // Assert
        verify(orderRepository, times(1)).save(argThat(order -> 
            order.getNr().equals("1234") && order.getPrioridade().equals("AZUL")
        ));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/orders"), any(Order.class));
    }

    @Test
    public void shouldIgnoreWhenPriorityIsSame() throws Exception {
        // Arrange
        String json = "{\"file_name\": \"NR1234CLIENTE_VERMELHO.CNC\"}";

        Order existingOrder = new Order();
        existingOrder.setNr("1234");
        existingOrder.setPrioridade("VERMELHO");

        doAnswer(invocation -> {
            MessageProcessingMetrics.ThrowingRunnable runnable = invocation.getArgument(1);
            try {
                runnable.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(messageProcessingMetrics).recordProcessing(anyString(), any(MessageProcessingMetrics.ThrowingRunnable.class));

        when(orderRepository.findByNr("1234")).thenReturn(Optional.of(existingOrder));

        // Act
        fileWatcherService.handleLaserQueue(json);

        // Assert
        verify(orderRepository, never()).save(any(Order.class));
    }
}
