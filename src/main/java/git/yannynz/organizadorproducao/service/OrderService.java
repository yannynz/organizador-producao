package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order saveOrder(Order order) {
        return orderRepository.save(order);
    }

    public void deleteOrder(Long id) {
        orderRepository.deleteById(id);
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    public void deleteAllOrders() {
        orderRepository.deleteAll();
    }
}