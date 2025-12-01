package git.yannynz.organizadorproducao.repository;

import git.yannynz.organizadorproducao.model.Transportadora;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TransportadoraRepository extends JpaRepository<Transportadora, Long> {

    Optional<Transportadora> findByNomeNormalizado(String nomeNormalizado);

    @Query(value = """
              select * from transportadoras t
              where (:search is null or
                     upper(unaccent(t.nome_oficial)) like upper(unaccent(concat('%', :search, '%')))
                    )
              order by t.ultimo_servico_em desc nulls last
            """,
            countQuery = """
              select count(*) from transportadoras t
              where (:search is null or
                     upper(unaccent(t.nome_oficial)) like upper(unaccent(concat('%', :search, '%')))
                    )
            """,
            nativeQuery = true)
    Page<Transportadora> search(@Param("search") String search, Pageable pageable);
}