package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.domain.user.User;
import git.yannynz.organizadorproducao.domain.user.UserRepository;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.model.OrderHistory;
import git.yannynz.organizadorproducao.repository.OrderHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class OrderHistoryService {

    @Autowired
    private OrderHistoryRepository repo;

    @Autowired
    private UserRepository userRepo;

    public void logChange(Order order, String field, String oldVal, String newVal) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = null;
        
        if (auth != null) {
            if (auth.getPrincipal() instanceof User u) {
                user = u;
            } else if (auth.getName() != null) {
                user = userRepo.findByEmail(auth.getName()).orElse(null);
            }
        }

        OrderHistory h = new OrderHistory();
        h.setOrder(order);
        h.setUser(user);
        h.setTimestamp(ZonedDateTime.now());
        h.setFieldName(field);
        h.setOldValue(oldVal);
        h.setNewValue(newVal);
        repo.save(h);
    }

    public List<OrderHistory> getHistory(Long orderId) {
        return repo.findByOrderIdOrderByTimestampDesc(orderId);
    }
}
