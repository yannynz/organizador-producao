package git.yannynz.organizadorproducao.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.repository.OrderRepository;

@Service
public class FileWatcherService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Ouve mensagens da fila RabbitMQ associada à pasta /laser.
     * A mensagem contém informações simulando o "arquivo" ou seus dados.
     */
    @RabbitListener(queues = "laserQueue")
    public void handleLaserQueue(String message) {
        System.out.println("Mensagem recebida na fila 'laserQueue': " + message);
        processFile(message);
    }
    /**
     * Ouve mensagens da fila RabbitMQ associada à pasta /facasOk.
     * A mensagem contém informações simulando o "arquivo" ou seus dados.
     */
    @RabbitListener(queues = "facasOkQueue")
    public void handleFacasOkQueue(String message) {
        System.out.println("Mensagem recebida na fila 'facasOkQueue': " + message);
        trackFileInFacasOk(message);
    }

    private void processFile(String fileName) {
        System.out.println("Processando mensagem simulando arquivo na pasta laser: " + fileName);

        Pattern pattern = Pattern.compile("NR(\\d+)([\\w\\s]+?)_(VERMELHO|AMARELO|AZUL|VERDE)(?:\\.CNC)?");
        Matcher matcher = pattern.matcher(fileName);

        if (matcher.matches()) {
            String orderNumber = matcher.group(1);
            String client = matcher.group(2);
            String priority = matcher.group(3);

            if (orderRepository.findByNr(orderNumber).isPresent()) {
                System.out.println("Pedido com NR " + orderNumber + " já existe. Ignorando nova mensagem.");
                return;
            }

            LocalDateTime creationTime = LocalDateTime.now();
            System.out.println("Informações extraídas da mensagem: NR=" + orderNumber + ", Cliente=" + client + ", Prioridade=" + priority);

            Order order = new Order();
            order.setNr(orderNumber);
            order.setCliente(client);
            order.setPrioridade(priority);
            order.setDataH(creationTime);
            order.setStatus(0); // Status inicial

            Order savedOrder = orderRepository.save(order);
            messagingTemplate.convertAndSend("/topic/orders", savedOrder);
            System.out.println("Pedido criado e enviado via WebSocket: " + savedOrder);

        } else {
            System.out.println("A mensagem não corresponde ao padrão esperado e será ignorada: " + fileName);
        }
    }

    private void trackFileInFacasOk(String fileName) {
        System.out.println("Processando mensagem simulando arquivo na pasta facasOk: " + fileName);

        Pattern pattern = Pattern.compile("NR(\\d+)([\\w\\s]+?)_(VERMELHO|AMARELO|AZUL|VERDE)(?:\\.CNC)?");
        Matcher matcher = pattern.matcher(fileName);

        if (matcher.matches()) {
            String orderNumber = matcher.group(1);
            updateOrderStatus(orderNumber, 1); // Atualizar status para "cortada"
        } else {
            System.out.println("A mensagem não corresponde ao padrão esperado e será ignorada: " + fileName);
        }
    }

    private void updateOrderStatus(String orderNumber, int newStatus) {
        Optional<Order> orderOpt = orderRepository.findByNr(orderNumber);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            if (order.getStatus() != newStatus) {
                order.setStatus(newStatus);
                orderRepository.save(order);
                messagingTemplate.convertAndSend("/topic/orders", order);
                System.out.println("Status do pedido " + orderNumber + " atualizado para " + newStatus);
            } else {
                System.out.println("Status do pedido " + orderNumber + " já está atualizado para " + newStatus);
            }
        } else {
            System.out.println("Pedido não encontrado para o número: " + orderNumber);
        }
    }
}
