package git.yannynz.organizadorproducao.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.model.OrderSavedEvent;
import git.yannynz.organizadorproducao.repository.OrderRepository;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public List<Order> getAllOrders() {
        return orderRepository.findAll();

    }

    public OrderService(ApplicationEventPublisher eventPublisher, OrderRepository orderRepository) {
        this.eventPublisher = eventPublisher;
        this.orderRepository = orderRepository;
    }

    public Order saveOrder(Order order) {
        Order savedOrder = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderSavedEvent(this, savedOrder));
        return savedOrder;
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
