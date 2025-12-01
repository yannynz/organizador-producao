package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.model.*;
import git.yannynz.organizadorproducao.model.dto.OpImportRequestDTO;
import git.yannynz.organizadorproducao.repository.OpImportRepository;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpImportServiceTransportadoraTest {

    @Mock private OpImportRepository opImportRepo;
    @Mock private OrderRepository orderRepo;
    @Mock private ObjectMapper mapper;
    @Mock private SimpMessagingTemplate ws;
    @Mock private ClienteAutoEnrichmentService clienteAuto;

    private OpImportService service;

    @BeforeEach
    void setUp() {
        service = new OpImportService(opImportRepo, orderRepo, mapper, ws, clienteAuto);
    }

    @Test
    void importar_ShouldLinkTransportadoraToOrder_WhenClientHasOne() {
        // Given
        OpImportRequestDTO req = new OpImportRequestDTO();
        req.setNumeroOp("OP123");
        req.setClienteNomeOficial("Client X");

        OpImport opImport = new OpImport();
        opImport.setNumeroOp("OP123");

        Cliente cliente = new Cliente();
        cliente.setId(1L);
        Transportadora transp = new Transportadora();
        transp.setId(99L);
        cliente.setTransportadora(transp);
        opImport.setClienteRef(cliente);

        Order order = new Order();
        order.setId(50L);
        order.setNr("OP123");
        // order.getTransportadora() is null initially

        when(opImportRepo.findByNumeroOp("OP123")).thenReturn(Optional.of(opImport));
        when(opImportRepo.save(any())).thenReturn(opImport);
        when(orderRepo.findTopByNrOrderByIdDesc("OP123")).thenReturn(Optional.of(order));
        
        // Mock enrichment to set the client on the opImport (OpImportService calls this)
        doAnswer(invocation -> {
            OpImport op = invocation.getArgument(0);
            op.setClienteRef(cliente);
            return null; // returns ClienteEndereco, can be null here
        }).when(clienteAuto).upsertFromOp(any(), any());

        // When
        service.importar(req);

        // Then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepo, atLeastOnce()).save(orderCaptor.capture());
        
        // Verify the last saved state or one of them
        Order savedOrder = orderCaptor.getValue();
        assertNotNull(savedOrder.getTransportadora());
        assertEquals(99L, savedOrder.getTransportadora().getId());
        assertEquals(cliente, savedOrder.getClienteRef());
    }

    @Test
    void tryLinkAsync_ShouldLinkTransportadora_WhenMissingOnOrder() {
        // Given
        String nr = "OP-LINK";
        Long facaId = 100L;

        OpImport op = new OpImport();
        op.setNumeroOp(nr);
        op.setFacaId(null); // Not linked yet

        Cliente cliente = new Cliente();
        Transportadora transp = new Transportadora();
        transp.setId(88L);
        cliente.setTransportadora(transp);
        op.setClienteRef(cliente);

        Order order = new Order();
        order.setId(facaId);
        order.setNr(nr);
        // Transportadora missing

        when(opImportRepo.findByNumeroOp(nr)).thenReturn(Optional.of(op));
        when(orderRepo.findById(facaId)).thenReturn(Optional.of(order));

        // When
        service.tryLinkAsync(nr, facaId);

        // Then
        verify(orderRepo).save(order);
        assertEquals(transp, order.getTransportadora());
        assertEquals(cliente, order.getClienteRef());
    }
}
