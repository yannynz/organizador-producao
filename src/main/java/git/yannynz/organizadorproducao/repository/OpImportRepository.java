package git.yannynz.organizadorproducao.repository;
import git.yannynz.organizadorproducao.model.OpImport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OpImportRepository extends JpaRepository<OpImport, Long> {

  Optional<OpImport> findByNumeroOp(String numeroOp);
  Optional<OpImport> findTopByFacaIdOrderByCreatedAtDesc(Long facaId);
}

