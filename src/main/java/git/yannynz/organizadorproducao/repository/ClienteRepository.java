package git.yannynz.organizadorproducao.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import git.yannynz.organizadorproducao.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByNomeNormalizado(String nomeNormalizado);

    @Query(value = """
              select * from clientes c
              where (:search is null or
                     upper(unaccent(c.nome_oficial)) like upper(unaccent(concat('%', :search, '%')))
                    )
              order by c.ultimo_servico_em desc nulls last
            """,
            countQuery = """
              select count(*) from clientes c
              where (:search is null or
                     upper(unaccent(c.nome_oficial)) like upper(unaccent(concat('%', :search, '%')))
                    )
            """,
            nativeQuery = true)
    Page<Cliente> search(@Param("search") String search, Pageable pageable);
}
