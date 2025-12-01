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

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    void upsertFromOp_ShouldDoNothing_WhenClienteNameIsMissing() {
        OpImport op = new OpImport();
        OpImportRequestDTO dto = new OpImportRequestDTO();
        dto.setClienteNomeOficial(null);

        ClienteEndereco result = service.upsertFromOp(op, dto);

        assertNull(result);
        verify(clienteRepo, never()).findByNomeNormalizado(any());
        verify(clienteRepo, never()).save(any());
    }

    @Test
    void upsertFromOp_ShouldCreateNewClient_WhenNotFound() {
        OpImport op = new OpImport();
        OpImportRequestDTO dto = new OpImportRequestDTO();
        dto.setClienteNomeOficial("New Client");
        dto.setPadraoEntregaSugerido("RETIRA");

        when(clienteRepo.findByNomeNormalizado("NEW CLIENT")).thenReturn(Optional.empty());
        
        Cliente savedClient = new Cliente();
        savedClient.setId(1L);
        savedClient.setNomeOficial("New Client");
        savedClient.setPadraoEntrega("RETIRA");
        when(clienteRepo.save(any(Cliente.class))).thenReturn(savedClient);

        service.upsertFromOp(op, dto);

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepo).save(captor.capture());
        Cliente c = captor.getValue();
        assertEquals("New Client", c.getNomeOficial());
        assertEquals("NEW CLIENT", c.getNomeNormalizado());
        assertEquals("RETIRA", c.getPadraoEntrega());
        assertEquals("OP", c.getOrigin());
        
        assertEquals(savedClient, op.getClienteRef());
    }

    @Test
    void upsertFromOp_ShouldUpdateLastService_WhenExistingClient() {
        OpImport op = new OpImport();
        OpImportRequestDTO dto = new OpImportRequestDTO();
        dto.setClienteNomeOficial("Existing Client");
        dto.setDataUltimoServicoSugerida("2025-11-27T10:00");

        Cliente existing = new Cliente();
        existing.setId(2L);
        existing.setNomeOficial("Existing Client");
        // Current last service is older
        existing.setUltimoServicoEm(OffsetDateTime.parse("2025-01-01T10:00:00+00:00"));

        when(clienteRepo.findByNomeNormalizado("EXISTING CLIENT")).thenReturn(Optional.of(existing));

        service.upsertFromOp(op, dto);

        verify(clienteRepo).save(existing);
        assertNotNull(existing.getUltimoServicoEm());
        // Check if date was updated (simple check if year is 2025)
        assertEquals(2025, existing.getUltimoServicoEm().getYear());
        assertEquals(11, existing.getUltimoServicoEm().getMonthValue());
    }

    @Test
    void upsertFromOp_ShouldCreateAddress_WhenNewAndSuggested() {
        OpImport op = new OpImport();
        OpImportRequestDTO dto = new OpImportRequestDTO();
        dto.setClienteNomeOficial("Client With Address");
        
        EnderecoSugeridoDTO addrDto = new EnderecoSugeridoDTO();
        addrDto.setLogradouro("Rua A");
        addrDto.setCidade("City");
        addrDto.setUf("UF");
        dto.setEnderecosSugeridos(List.of(addrDto));

        Cliente client = new Cliente();
        client.setId(10L);
        when(clienteRepo.findByNomeNormalizado(any())).thenReturn(Optional.of(client));
        when(enderecoRepo.findByClienteId(10L)).thenReturn(Collections.emptyList());

        ClienteEndereco savedAddr = new ClienteEndereco();
        savedAddr.setId(100L);
        when(enderecoRepo.save(any(ClienteEndereco.class))).thenReturn(savedAddr);

        ClienteEndereco result = service.upsertFromOp(op, dto);

        assertNotNull(result);
        assertEquals(savedAddr, result);
        assertEquals(savedAddr, op.getEndereco());
        
        ArgumentCaptor<ClienteEndereco> captor = ArgumentCaptor.forClass(ClienteEndereco.class);
        verify(enderecoRepo).save(captor.capture());
        ClienteEndereco e = captor.getValue();
        assertEquals("Rua A", e.getLogradouro());
        assertEquals("City", e.getCidade());
        assertTrue(e.getIsDefault());
    }

    @Test
    void upsertFromOp_ShouldMatchExistingAddress() {
        OpImport op = new OpImport();
        OpImportRequestDTO dto = new OpImportRequestDTO();
        dto.setClienteNomeOficial("Client With Existing Address");
        
        EnderecoSugeridoDTO addrDto = new EnderecoSugeridoDTO();
        addrDto.setLogradouro("Rua Teste"); // Similar to existing
        dto.setEnderecosSugeridos(List.of(addrDto));

        Cliente client = new Cliente();
        client.setId(20L);

        ClienteEndereco existingAddr = new ClienteEndereco();
        existingAddr.setId(200L);
        existingAddr.setLogradouro("Rua Teste, 123"); // Contains "Rua Teste"

        when(clienteRepo.findByNomeNormalizado(any())).thenReturn(Optional.of(client));
        when(enderecoRepo.findByClienteId(20L)).thenReturn(List.of(existingAddr));

        ClienteEndereco result = service.upsertFromOp(op, dto);

        assertEquals(existingAddr, result);
        verify(enderecoRepo, never()).save(any(ClienteEndereco.class));
    }
}
