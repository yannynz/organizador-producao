package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.model.OpImport;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpImportServiceMaterialsTest {

    @Mock private OpImportRepository opImportRepo;
    @Mock private OrderRepository orderRepo;
    private ObjectMapper mapper = new ObjectMapper(); // Use real mapper for JSON logic
    @Mock private SimpMessagingTemplate ws;
    @Mock private ClienteAutoEnrichmentService clienteAuto;

    private OpImportService service;

    @BeforeEach
    void setUp() {
        service = new OpImportService(opImportRepo, orderRepo, mapper, ws, clienteAuto);
    }

    @Test
    void importar_ShouldMapComplexMaterialStringsToJson() {
        // Given
        String realMaterial1 = "MISTA 2PT 5,0 X 5,0 X 23,3 C 23,80";
        String realMaterial2 = "PICOTE 2PT TRAV ONDU 2X1X23,5MM LAMINA BRASIL";
        
        OpImportRequestDTO req = new OpImportRequestDTO();
        req.setNumeroOp("120488");
        req.setMateriais(List.of(realMaterial1, realMaterial2));

        when(opImportRepo.findByNumeroOp("120488")).thenReturn(Optional.empty()); // New OP
        when(opImportRepo.save(any())).thenAnswer(i -> i.getArgument(0)); // Return what is saved

        // When
        service.importar(req);

        // Then
        ArgumentCaptor<OpImport> captor = ArgumentCaptor.forClass(OpImport.class);
        verify(opImportRepo).save(captor.capture());
        OpImport saved = captor.getValue();

        assertNotNull(saved.getMateriais());
        assertTrue(saved.getMateriais().isArray());
        assertEquals(2, saved.getMateriais().size());
        assertEquals(realMaterial1, saved.getMateriais().get(0).asText());
        assertEquals(realMaterial2, saved.getMateriais().get(1).asText());
    }
}
