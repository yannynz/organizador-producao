package git.yannynz.organizadorproducao.service;

import org.springframework.scheduling.annotation.Async;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.repository.OrderRepository;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order saveOrder(Order order) {
        Order savedOrder = orderRepository.save(order);
        return savedOrder;
    }

    public void deleteOrder(Long id) {
        orderRepository.deleteById(id);
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);

    }

    public Optional<Order> getOrderByNr(String nr) {
        return orderRepository.findByNr(nr);
    }

    public void deleteAllOrders() {
        orderRepository.deleteAll();
    }

    @Scheduled(fixedRate = 60000) // Executa a cada 10 minutos
public void updateOrderPriorities() {
    List<Integer> statuses = Arrays.asList(0, 1); // Status relevantes
    List<Order> orders = orderRepository.findByStatusIn(statuses);

    System.out.println("Atualizando prioridades dos pedidos...");

    for (Order order : orders) {
        updatePriority(order);
    }
}
@Async
private void updatePriority(Order order) {
    if (order.getDataH() == null) {
        System.out.println("Data de criação não encontrada para o pedido: " + order.getId());
        return;
    }

    long hoursSinceCreation = ChronoUnit.HOURS.between(order.getDataH().toInstant().atZone(ZoneId.systemDefault()), ZonedDateTime.now());

    // Verifique se a prioridade já é "VERMELHA" e não faça nada
    if ("VERMELHA".equals(order.getPrioridade())) {
        System.out.println("Prioridade já é VERMELHA para o pedido: " + order.getId());
        return; // Não faz nada se a prioridade for "VERMELHA"
    }

    String newPriority = order.getPrioridade();

    // Se o pedido tem 48 horas ou mais e a prioridade for VERDE, deve virar AZUL
    if (hoursSinceCreation >= 48 && "VERDE".equals(order.getPrioridade())) {
        newPriority = "AZUL";
    }
    // Se o pedido tem 24 horas ou mais e a prioridade for AZUL, deve virar AMARELO
    else if (hoursSinceCreation >= 24 && "AZUL".equals(order.getPrioridade())) {
        newPriority = "AMARELO";
    }

    // Se a prioridade mudou, atualize o pedido
    if (!order.getPrioridade().equals(newPriority)) {
        order.setPrioridade(newPriority);
        orderRepository.save(order);
        messagingTemplate.convertAndSend("/topic/prioridades", order);
    }
}
}
