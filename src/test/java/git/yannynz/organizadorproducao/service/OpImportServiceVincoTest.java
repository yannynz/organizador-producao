package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.model.OpImport;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.model.dto.OpImportRequestDTO;
import git.yannynz.organizadorproducao.repository.OpImportRepository;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpImportServiceVincoTest {

    @Mock
    private OpImportRepository opImportRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ClienteAutoEnrichmentService clienteAuto;

    @Mock
    private ClienteDefaultsService clienteDefaultsService;

    private ObjectMapper mapper;

    private OpImportService service;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new OpImportService(opImportRepository, orderRepository, mapper, messagingTemplate, clienteAuto, clienteDefaultsService);
    }

    @Test
    void importarMarcaOrderComoVaiVincoQuandoNaoBloqueado() {
        OpImport op = new OpImport();
        op.setNumeroOp("123");
        op.setManualLockVaiVinco(false);

        Order order = new Order();
        order.setId(42L);
        order.setNr("123");
        order.setVaiVinco(false);

        when(opImportRepository.findByNumeroOp("123")).thenReturn(Optional.of(op));
        when(opImportRepository.save(any(OpImport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.findTopByNrOrderByIdDesc("123")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OpImportRequestDTO req = new OpImportRequestDTO();
        req.setNumeroOp("123");
        req.setVaiVinco(true);
        req.setMateriais(Collections.emptyList());

        service.importar(req);

        assertTrue(Boolean.TRUE.equals(op.getVaiVinco()), "OP deve registrar vaiVinco=true");
        assertTrue(order.isVaiVinco(), "Pedido deve ser marcado como vaiVinco");

        verify(orderRepository, atLeastOnce()).save(any(Order.class));
    }

    @Test
    void importarRespeitaManualLockVaiVinco() {
        OpImport op = new OpImport();
        op.setNumeroOp("789");
        op.setManualLockVaiVinco(true);

        Order order = new Order();
        order.setId(77L);
        order.setNr("789");
        order.setVaiVinco(false);

        when(opImportRepository.findByNumeroOp("789")).thenReturn(Optional.of(op));
        when(opImportRepository.save(any(OpImport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.findTopByNrOrderByIdDesc("789")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OpImportRequestDTO req = new OpImportRequestDTO();
        req.setNumeroOp("789");
        req.setVaiVinco(true);
        req.setMateriais(Collections.emptyList());

        service.importar(req);

        assertTrue(Boolean.TRUE.equals(op.getVaiVinco()), "OP deve registrar vaiVinco=true mesmo com lock");
        assertTrue(op.isManualLockVaiVinco(), "Lock deve permanecer ativo após import");
        assertFalse(order.isVaiVinco(), "Pedido não deve ser atualizado devido ao lock");
    }
}
