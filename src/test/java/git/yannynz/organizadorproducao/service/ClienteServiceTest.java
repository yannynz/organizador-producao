package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Cliente;
import git.yannynz.organizadorproducao.model.Transportadora;
import git.yannynz.organizadorproducao.repository.ClienteEnderecoRepository;
import git.yannynz.organizadorproducao.repository.ClienteRepository;
import git.yannynz.organizadorproducao.repository.TransportadoraRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository repo;

    @Mock
    private ClienteEnderecoRepository enderecoRepo;

    @Mock
    private TransportadoraRepository transportadoraRepo;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(repo, enderecoRepo, transportadoraRepo);
    }

    @Test
    void create_shouldNormalizeNameApplyDefaultFlagsAndLinkTransportadora() {
        Cliente cliente = new Cliente();
        cliente.setNomeOficial("  Gráfica São João  ");
        cliente.setTransportadoraId(7L);
        Transportadora transportadora = new Transportadora();
        transportadora.setId(7L);

        when(transportadoraRepo.findById(7L)).thenReturn(Optional.of(transportadora));
        when(repo.save(cliente)).thenReturn(cliente);

        Cliente result = service.create(cliente);

        assertThat(result.getNomeNormalizado()).isEqualTo("GRAFICA SAO JOAO");
        assertThat(result.getDefaultEmborrachada()).isFalse();
        assertThat(result.getDefaultPertinax()).isFalse();
        assertThat(result.getDefaultPoliester()).isFalse();
        assertThat(result.getDefaultPapelCalibrado()).isFalse();
        assertThat(result.getTransportadora()).isSameAs(transportadora);
    }

    @Test
    void create_shouldRejectBlankOfficialName() {
        Cliente cliente = new Cliente();
        cliente.setNomeOficial(" ");

        assertThatThrownBy(() -> service.create(cliente))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nome oficial");
    }

    @Test
    void update_shouldPatchOnlyProvidedFieldsAndNormalizeName() {
        Cliente existing = new Cliente();
        existing.setId(12L);
        existing.setNomeOficial("Nome Antigo");
        existing.setNomeNormalizado("NOME ANTIGO");
        existing.setDefaultDestacador("NAO");
        existing.setDefaultPertinax(false);

        Cliente update = new Cliente();
        update.setNomeOficial("Cliente Ágil");
        update.setDefaultDestacador("SIM");

        when(repo.findById(12L)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Cliente result = service.update(12L, update);

        assertThat(result.getNomeOficial()).isEqualTo("Cliente Ágil");
        assertThat(result.getNomeNormalizado()).isEqualTo("CLIENTE AGIL");
        assertThat(result.getDefaultDestacador()).isEqualTo("SIM");
        assertThat(result.getDefaultPertinax()).isFalse();
    }

    @Test
    void linkAliases_shouldAddOfficialNamesOnBothClientsWithoutDuplicates() {
        Cliente principal = new Cliente();
        principal.setId(1L);
        principal.setNomeOficial("Cliente Principal");
        principal.setApelidos(List.of("Cliente Alias"));

        Cliente alias = new Cliente();
        alias.setId(2L);
        alias.setNomeOficial("Cliente Álias");

        when(repo.findById(1L)).thenReturn(Optional.of(principal));
        when(repo.findById(2L)).thenReturn(Optional.of(alias));

        Cliente result = service.linkAliases(1L, 2L);

        assertThat(result).isSameAs(principal);
        assertThat(principal.getApelidos()).containsExactly("Cliente Alias");
        assertThat(alias.getApelidos()).containsExactly("Cliente Principal");
        verify(repo).save(principal);
        verify(repo).save(alias);
    }

    @Test
    void linkAliases_shouldRejectSameClient() {
        assertThatThrownBy(() -> service.linkAliases(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não podem ser o mesmo");
    }
}
