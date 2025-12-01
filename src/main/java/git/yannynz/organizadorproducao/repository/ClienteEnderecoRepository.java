package git.yannynz.organizadorproducao.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import git.yannynz.organizadorproducao.model.ClienteEndereco;

public interface ClienteEnderecoRepository extends JpaRepository<ClienteEndereco, Long> {

    List<ClienteEndereco> findByClienteId(Long clienteId);
    
    java.util.Optional<ClienteEndereco> findByClienteIdAndIsDefaultTrue(Long clienteId);
}
