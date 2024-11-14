package git.yannynz.organizadorproducao.repository;

import git.yannynz.organizadorproducao.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
        Optional<Order> findByNr(String nr);
}
