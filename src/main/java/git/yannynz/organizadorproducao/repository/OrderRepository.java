package git.yannynz.organizadorproducao.repository;

import git.yannynz.organizadorproducao.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {
        Optional<Order> findByNr(String nr);
        List<Order> findByStatusIn(List<Integer> statuses);
}
