package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Cliente;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.repository.ClienteRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteDefaultsServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    private ClienteDefaultsService service;

    @BeforeEach
    void setUp() {
        service = new ClienteDefaultsService(clienteRepository);
    }

    @Test
    void applyDefaults_ShouldSetFlagsAndDestacador() {
        Cliente cliente = new Cliente();
        cliente.setDefaultEmborrachada(true);
        cliente.setDefaultDestacador("M/F");
        cliente.setDefaultPertinax(true);
        cliente.setDefaultPoliester(false);
        cliente.setDefaultPapelCalibrado(true);

        Order order = new Order();
        order.setEmborrachada(false);
        order.setPertinax(false);
        order.setPoliester(false);
        order.setPapelCalibrado(false);
        order.setDestacador("F");

        boolean changed = service.applyDefaults(order, cliente);

        assertTrue(changed);
        assertTrue(order.isEmborrachada());
        assertTrue(order.isPertinax());
        assertTrue(order.isPapelCalibrado());
        assertFalse(order.isPoliester());
        assertEquals("MF", order.getDestacador());
    }

    @Test
    void applyDefaults_ShouldNotChangeWhenDefaultsAreDisabled() {
        Cliente cliente = new Cliente();
        cliente.setDefaultEmborrachada(false);
        cliente.setDefaultDestacador(" ");
        cliente.setDefaultPertinax(false);
        cliente.setDefaultPoliester(false);
        cliente.setDefaultPapelCalibrado(false);

        Order order = new Order();
        order.setEmborrachada(true);
        order.setPertinax(true);
        order.setPoliester(true);
        order.setPapelCalibrado(true);
        order.setDestacador("F");

        boolean changed = service.applyDefaults(order, cliente);

        assertFalse(changed);
        assertTrue(order.isEmborrachada());
        assertEquals("F", order.getDestacador());
    }

    @Test
    void applyDefaults_ShouldResolveClienteByNomeWhenMissing() {
        Cliente cliente = new Cliente();
        cliente.setDefaultEmborrachada(true);

        Order order = new Order();
        order.setCliente("Cliente Ótimo");

        when(clienteRepository.findByNomeNormalizado("CLIENTE OTIMO"))
                .thenReturn(Optional.of(cliente));

        boolean changed = service.applyDefaults(order);

        assertTrue(changed);
        assertEquals(cliente, order.getClienteRef());
        assertTrue(order.isEmborrachada());
        verify(clienteRepository).findByNomeNormalizado("CLIENTE OTIMO");
    }
}
