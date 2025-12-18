package git.yannynz.organizadorproducao.service;

import static org.assertj.core.api.Assertions.assertThat;

import git.yannynz.organizadorproducao.model.Cliente;
import git.yannynz.organizadorproducao.model.ClienteEndereco;
import git.yannynz.organizadorproducao.model.OpImport;
import git.yannynz.organizadorproducao.model.dto.EnderecoSugeridoDTO;
import git.yannynz.organizadorproducao.model.dto.OpImportRequestDTO;
import git.yannynz.organizadorproducao.repository.ClienteEnderecoRepository;
import git.yannynz.organizadorproducao.repository.ClienteRepository;
import git.yannynz.organizadorproducao.repository.OpImportRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/teste01",
        "spring.datasource.username=postgres",
        "spring.datasource.password=1234",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.port=5672",
        "spring.rabbitmq.username=guest",
        "spring.rabbitmq.password=guest"
})
@Transactional
class OpImportServiceIntegrationTest {

    @Autowired
    private OpImportService opImportService;

    @Autowired
    private OpImportRepository opImportRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ClienteEnderecoRepository enderecoRepository;

    @MockBean
    private SimpMessagingTemplate simpMessagingTemplate; // evita dependência de websocket nos testes

    @Test
    void importar_op_pdf_cria_cliente_e_endereco() {
        var numeroOp = "120432-TST";
        var clienteNome = "YCAR ARTES GRAFICAS TESTE";
        var nomeNormalizado = normalize(clienteNome);

        opImportRepository.findByNumeroOp(numeroOp).ifPresent(opImportRepository::delete);
        clienteRepository.findByNomeNormalizado(nomeNormalizado).ifPresent(clienteRepository::delete);

        var dto = new OpImportRequestDTO();
        dto.setNumeroOp(numeroOp);
        dto.setCliente(clienteNome);
        dto.setClienteNomeOficial(clienteNome);
        dto.setDataOp("2025-11-12");
        dto.setMateriais(List.of("PAPEL", "CARTAO"));
        dto.setVaiVinco(true);
        dto.setPadraoEntregaSugerido("A_ENTREGAR");
        dto.setSharePath("/tmp/nr/Ordem de Producao 120432.pdf");
        dto.setEnderecosSugeridos(List.of(buildEndereco()));

        opImportService.importar(dto);

        OpImport saved = opImportRepository.findByNumeroOp(numeroOp)
                .orElseThrow(() -> new AssertionError("OpImport não criado"));

        assertThat(saved.getClienteRef()).as("cliente associado").isNotNull();
        assertThat(saved.getEndereco()).as("endereco associado").isNotNull();
        assertThat(saved.getModalidadeEntrega()).isEqualTo("A ENTREGAR");

        Cliente cliente = saved.getClienteRef();
        assertThat(cliente.getNomeNormalizado()).isEqualTo(nomeNormalizado);
        assertThat(cliente.getApelidos()).as("apelidos jsonb não pode ser nulo").isNotNull();

        List<ClienteEndereco> enderecos = enderecoRepository.findByClienteId(cliente.getId());
        assertThat(enderecos).isNotEmpty();
        assertThat(enderecos.get(0).getCidade()).isEqualTo("CAJAMAR");
    }

    private EnderecoSugeridoDTO buildEndereco() {
        var dto = new EnderecoSugeridoDTO();
        dto.setUf("SP");
        dto.setCidade("CAJAMAR");
        dto.setBairro("PAINEIRA");
        dto.setLogradouro("AVENIDA RIBEIRAO DOS CRISTAIS 340");
        dto.setCep("07775-240");
        dto.setPadraoEntrega("A_ENTREGAR");
        dto.setHorarioFuncionamento("08:00 - 18:00");
        return dto;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .trim();
    }
}
