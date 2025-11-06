package git.yannynz.organizadorproducao.repository;

import git.yannynz.organizadorproducao.model.DXFAnalysis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DXFAnalysisRepository extends JpaRepository<DXFAnalysis, Long> {
    Optional<DXFAnalysis> findByAnalysisId(String analysisId);
    Optional<DXFAnalysis> findTopByOrderNrOrderByAnalyzedAtDesc(String orderNr);
    List<DXFAnalysis> findTop5ByOrderNrOrderByAnalyzedAtDesc(String orderNr);
    List<DXFAnalysis> findByOrderNrOrderByAnalyzedAtDesc(String orderNr, Pageable pageable);
}
