package git.yannynz.organizadorproducao.repository;

import git.yannynz.organizadorproducao.model.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {
    List<OrderHistory> findByOrderIdOrderByTimestampDesc(Long orderId);
}
