package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Cliente;
import git.yannynz.organizadorproducao.model.ClienteEndereco;
import git.yannynz.organizadorproducao.model.OpImport;
import git.yannynz.organizadorproducao.model.dto.EnderecoSugeridoDTO;
import git.yannynz.organizadorproducao.model.dto.OpImportRequestDTO;
import git.yannynz.organizadorproducao.repository.ClienteEnderecoRepository;
import git.yannynz.organizadorproducao.repository.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteAutoEnrichmentServiceTest {

    @Mock
    private ClienteRepository clienteRepo;
    @Mock
    private ClienteEnderecoRepository enderecoRepo;

    private ClienteAutoEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new ClienteAutoEnrichmentService(clienteRepo, enderecoRepo);
    }

    @Test
    void upsertFromOp_ShouldCreateNewClient_WhenNotFound() {
        // Arrange
        OpImport op = new OpImport();
        OpImportRequestDTO dto = new OpImportRequestDTO();
        dto.setClienteNomeOficial("Novo Cliente Ltda");
        
        when(clienteRepo.findByNomeNormalizado(anyString())).thenReturn(Optional.empty());
        when(clienteRepo.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        // Act
        service.upsertFromOp(op, dto);

        // Assert
        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepo).save(captor.capture());
        Cliente saved = captor.getValue();
        assertEquals("Novo Cliente Ltda", saved.getNomeOficial());
        assertEquals("NOVO CLIENTE LTDA", saved.getNomeNormalizado());
        assertNotNull(op.getClienteRef());
    }

    @Test
    void upsertFromOp_ShouldCreateAddress_WhenNewAndSuggested() {
        // Arrange
        OpImport op = new OpImport();
        OpImportRequestDTO dto = new OpImportRequestDTO();
        dto.setClienteNomeOficial("Cliente Existente");
        
        EnderecoSugeridoDTO endDTO = new EnderecoSugeridoDTO();
        endDTO.setLogradouro("Rua Nova, 123");
        endDTO.setCidade("São Paulo");
        endDTO.setUf("SP");
        dto.setEnderecosSugeridos(List.of(endDTO));

        Cliente existingClient = new Cliente();
        existingClient.setId(10L);
        existingClient.setNomeOficial("Cliente Existente");

        when(clienteRepo.findByNomeNormalizado(anyString())).thenReturn(Optional.of(existingClient));
        when(enderecoRepo.findByClienteId(10L)).thenReturn(Collections.emptyList());
        when(enderecoRepo.save(any(ClienteEndereco.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ClienteEndereco result = service.upsertFromOp(op, dto);

        // Assert
        assertNotNull(result);
        assertEquals("Rua Nova, 123", result.getLogradouro());
        assertEquals("São Paulo", result.getCidade());
        assertEquals("SP", result.getUf());
        assertEquals(existingClient, result.getCliente());
        assertTrue(result.getIsDefault()); // First address should be default
        
        verify(enderecoRepo).save(any(ClienteEndereco.class));
        assertEquals(result, op.getEndereco());
    }

    @Test
    void upsertFromOp_ShouldMatchExistingAddress() {
        // Arrange
        OpImport op = new OpImport();
        OpImportRequestDTO dto = new OpImportRequestDTO();
        dto.setClienteNomeOficial("Cliente Existente");

        EnderecoSugeridoDTO endDTO = new EnderecoSugeridoDTO();
        endDTO.setLogradouro("Av Paulista, 1000"); // Matches existing
        dto.setEnderecosSugeridos(List.of(endDTO));

        Cliente existingClient = new Cliente();
        existingClient.setId(10L);

        ClienteEndereco existingAddr = new ClienteEndereco();
        existingAddr.setId(50L);
        existingAddr.setLogradouro("Avenida Paulista, 1000"); // Slightly different but matching normalization
        existingAddr.setCliente(existingClient);

        when(clienteRepo.findByNomeNormalizado(anyString())).thenReturn(Optional.of(existingClient));
        when(enderecoRepo.findByClienteId(10L)).thenReturn(List.of(existingAddr));

        // Act
        ClienteEndereco result = service.upsertFromOp(op, dto);

        // Assert
        assertNotNull(result);
        assertEquals(50L, result.getId());
        verify(enderecoRepo, never()).save(any(ClienteEndereco.class)); // Should not create new
        assertEquals(existingAddr, op.getEndereco());
    }
}