package git.yannynz.organizadorproducao.repository;

import git.yannynz.organizadorproducao.model.DXFAnalysis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DXFAnalysisRepository extends JpaRepository<DXFAnalysis, Long> {
    Optional<DXFAnalysis> findByAnalysisId(String analysisId);
    Optional<DXFAnalysis> findTopByOrderNrOrderByAnalyzedAtDesc(String orderNr);
    List<DXFAnalysis> findTop5ByOrderNrOrderByAnalyzedAtDesc(String orderNr);
    List<DXFAnalysis> findByOrderNrOrderByAnalyzedAtDesc(String orderNr, Pageable pageable);

    @Query("""
            select d from DXFAnalysis d
            where d.orderNr = :orderNr
              and (
                (d.imageKey is not null and d.imageKey <> '')
                or (d.imageUri is not null and d.imageUri <> '')
                or (d.imagePath is not null and d.imagePath <> '')
              )
            order by d.analyzedAt desc
            """)
    List<DXFAnalysis> findLatestWithImageByOrderNr(@Param("orderNr") String orderNr, Pageable pageable);

    @Query("""
            select d from DXFAnalysis d
            where d.fileHash = :fileHash
              and (
                (d.imageKey is not null and d.imageKey <> '')
                or (d.imageUri is not null and d.imageUri <> '')
                or (d.imagePath is not null and d.imagePath <> '')
              )
            order by d.analyzedAt desc
            """)
    List<DXFAnalysis> findLatestWithImageByFileHash(@Param("fileHash") String fileHash, Pageable pageable);
}
