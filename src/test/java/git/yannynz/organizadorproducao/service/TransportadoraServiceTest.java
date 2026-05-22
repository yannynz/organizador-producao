package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Transportadora;
import git.yannynz.organizadorproducao.repository.TransportadoraRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportadoraServiceTest {

    @Mock
    private TransportadoraRepository repo;

    private TransportadoraService service;

    @BeforeEach
    void setUp() {
        service = new TransportadoraService(repo);
    }

    @Test
    void create_shouldClearIdAndNormalizeOfficialName() {
        Transportadora transportadora = new Transportadora();
        transportadora.setId(99L);
        transportadora.setNomeOficial("  Rápido São José  ");

        when(repo.save(transportadora)).thenReturn(transportadora);

        Transportadora result = service.create(transportadora);

        assertThat(result.getId()).isNull();
        assertThat(result.getNomeNormalizado()).isEqualTo("RAPIDO SAO JOSE");
    }

    @Test
    void create_shouldRejectBlankOfficialName() {
        Transportadora transportadora = new Transportadora();
        transportadora.setNomeOficial("");

        assertThatThrownBy(() -> service.create(transportadora))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nome oficial");
    }

    @Test
    void update_shouldPatchProvidedFieldsWithoutClearingMissingOnes() {
        Transportadora existing = new Transportadora();
        existing.setId(5L);
        existing.setNomeOficial("Transportadora Antiga");
        existing.setNomeNormalizado("TRANSPORTADORA ANTIGA");
        existing.setLocalizacao("Galpão 1");
        existing.setAtivo(true);

        Transportadora update = new Transportadora();
        update.setNomeOficial("Nova Entrega");
        update.setAtivo(false);

        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Transportadora result = service.update(5L, update);

        assertThat(result.getNomeOficial()).isEqualTo("Nova Entrega");
        assertThat(result.getNomeNormalizado()).isEqualTo("NOVA ENTREGA");
        assertThat(result.getLocalizacao()).isEqualTo("Galpão 1");
        assertThat(result.getAtivo()).isFalse();
    }
}
