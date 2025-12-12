package git.yannynz.organizadorproducao.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import git.yannynz.organizadorproducao.infra.security.JwtService;
import git.yannynz.organizadorproducao.model.OpImport;
import git.yannynz.organizadorproducao.repository.OpImportRepository;
import git.yannynz.organizadorproducao.service.OpImportService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsInAnyOrder;

@WebMvcTest(OpImportController.class)
class OpImportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private OpImportService service;

    @MockBean
    private OpImportRepository repo;

    @MockBean
    private JwtService jwtService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @WithMockUser
    void getByNr_ShouldReturnOp_WhenFound() throws Exception {
        OpImport op = new OpImport();
        op.setId(1L);
        op.setNumeroOp("120488"); // Real case from file
        op.setEmborrachada(true);
        
        ArrayNode materiais = mapper.createArrayNode();
        materiais.add("MISTA 2PT 5,0 X 5,0 X 23,3 C 23,80");
        materiais.add("PICOTE 2PT TRAV ONDU 2X1X23,5MM LAMINA BRASIL");
        op.setMateriais(materiais);

        given(repo.findByNumeroOp("120488")).willReturn(Optional.of(op));

        mvc.perform(get("/ops/120488"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroOp").value("120488"))
                .andExpect(jsonPath("$.emborrachada").value(true))
                .andExpect(jsonPath("$.materiais[0]").value("MISTA 2PT 5,0 X 5,0 X 23,3 C 23,80"))
                .andExpect(jsonPath("$.materiais[1]").value("PICOTE 2PT TRAV ONDU 2X1X23,5MM LAMINA BRASIL"));
    }

    @Test
    @WithMockUser
    void getByNr_ShouldReturn404_WhenNotFound() throws Exception {
        given(repo.findByNumeroOp("99999")).willReturn(Optional.empty());

        mvc.perform(get("/ops/99999"))
                .andExpect(status().isNotFound());
    }
}
