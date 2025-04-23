package git.yannynz.organizadorproducao.service;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.ZoneId;
import java.util.Date;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

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
    @RabbitListener(queues = "laser_notifications")
    public void handleLaserQueue(String message) {
        System.out.println("Mensagem recebida na fila 'laserQueue': " + message);
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(message);
            String fileName = jsonNode.get("file_name").asText(); // Extraindo file_name
            processFile(fileName);
        } catch (Exception e) {
            System.err.println("Erro ao processar mensagem JSON na fila 'laserQueue': " + e.getMessage());
        }
    }

    @RabbitListener(queues = "facas_notifications")
    public void handleFacasOkQueue(String message) {
        System.out.println("Mensagem recebida na fila 'facasOkQueue': " + message);
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(message);
            String fileName = jsonNode.get("file_name").asText(); // Extraindo file_name
            trackFileInFacasOk(fileName);
        } catch (Exception e) {
            System.err.println("Erro ao processar mensagem JSON na fila 'facasOkQueue': " + e.getMessage());
        }
    }

    private void processFile(String fileName) {
    System.out.println("Processando mensagem simulando arquivo na pasta laser: " + fileName);

    // Padrões para identificar os tipos de arquivos
    Pattern nrPattern = Pattern.compile("NR(\\d+)([\\p{L}\\s\\d]+?)_(VERMELHO|AMARELO|AZUL|VERDE)(?:\\.CNC)?");
    Pattern clPattern = Pattern.compile("CL(\\d+)([\\p{L}\\s\\d]+?)_(VERMELHO|AMARELO|AZUL|VERDE)(?:\\.CNC)?");

    Matcher nrMatcher = nrPattern.matcher(fileName);
    Matcher clMatcher = clPattern.matcher(fileName);

    if (clMatcher.matches()) {
        // Processamento para arquivos CL
        String orderNumber = clMatcher.group(1);
        String client = clMatcher.group(2).trim(); // Removendo espaços desnecessários
        String priority = clMatcher.group(3);

        if (orderRepository.findByNr(orderNumber).isPresent()) {
            System.out.println("Pedido com NR " + orderNumber + " já existe. Ignorando nova mensagem.");
            return;
        }

        ZonedDateTime creationTime = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
        System.out.println("Informações extraídas da mensagem (CL): NR=" + orderNumber + ", Cliente=" + client + ", Prioridade=" + priority);

        Order order = new Order();
        order.setNr(orderNumber);
        order.setCliente(client);
        order.setPrioridade(priority);
        order.setDataH(creationTime);
        order.setStatus(0); // Status inicial para arquivos CL 

        Order savedOrder = orderRepository.save(order);
        messagingTemplate.convertAndSend("/topic/orders", savedOrder);
        System.out.println("Pedido criado e enviado via WebSocket: " + savedOrder);
    } else if (nrMatcher.matches()) {
        // Processamento para arquivos NR
        String orderNumber = nrMatcher.group(1);
        String client = nrMatcher.group(2).trim(); // Removendo espaços desnecessários
        String priority = nrMatcher.group(3);

        if (orderRepository.findByNr(orderNumber).isPresent()) {
            System.out.println("Pedido com NR " + orderNumber + " já existe. Ignorando nova mensagem.");
            return;
        }

        ZonedDateTime creationTime = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
        System.out.println("Informações extraídas da mensagem (NR): NR=" + orderNumber + ", Cliente=" + client + ", Prioridade=" + priority);

        Order order = new Order();
        order.setNr(orderNumber);
        order.setCliente(client);
        order.setPrioridade(priority);
        order.setDataH(creationTime);
        order.setStatus(0); // Status inicial para arquivos NR

        Order savedOrder = orderRepository.save(order);
        messagingTemplate.convertAndSend("/topic/orders", savedOrder);
        System.out.println("Pedido criado e enviado via WebSocket: " + savedOrder);
    } else {
        // Arquivo fora dos padrões esperados
        System.out.println("A mensagem não corresponde ao padrão esperado e será ignorada: " + fileName);
    }
}

    private void trackFileInFacasOk(String fileName) {
        System.out.println("Processando mensagem simulando arquivo na pasta facasOk: " + fileName);

        // Padrão para identificar pedidos gerais (com NR...)
        Pattern nrPattern = Pattern.compile("NR(\\d+).*?_(VERMELHO|AMARELO|AZUL|VERDE)(?:\\.CNC)?");
        // Padrão específico para pedidos de corte a laser (com CL...)
        Pattern clPattern = Pattern.compile("CL(\\d+).*?_(VERMELHO|AMARELO|AZUL|VERDE)(?:\\.CNC)?");

        Matcher nrMatcher = nrPattern.matcher(fileName);
        Matcher clMatcher = clPattern.matcher(fileName);

        if (clMatcher.find()) {
            // Pedido de corte a laser (CL...)
            String orderNumber = clMatcher.group(1);
            System.out.println("Pedido de corte a laser identificado. NR=" + orderNumber);
            updateOrderStatus(orderNumber, 2); // Atualizar status para "pronta"
        } else if (nrMatcher.find()) {
            // Pedido regular (NR...)
            String orderNumber = nrMatcher.group(1);
            System.out.println("Pedido regular identificado. NR=" + orderNumber);
            updateOrderStatus(orderNumber, 1); // Atualizar status para "cortada"
        } else {
            // Arquivo fora dos padrões esperados
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

