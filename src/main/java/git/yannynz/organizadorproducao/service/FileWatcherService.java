package git.yannynz.organizadorproducao.service;

import java.util.Optional;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import git.yannynz.organizadorproducao.service.OrderService;

@Service
public class FileWatcherService {

    private final Path directoryToWatchTestPaste = Paths.get("/laser");
    private final Path directoryToWatchFacasOk = Paths.get("/facasOk");
    private final Set<String> processedFiles = new HashSet<>();

    @Autowired
    private OrderService orderService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private OrderRepository orderRepository;

    public void startWatching() {
        if (Files.isDirectory(directoryToWatchTestPaste)) {
            System.out.println("Iniciando monitoramento da pasta testPaste: " + directoryToWatchTestPaste);
            new Thread(() -> watchDirectoryPath(directoryToWatchTestPaste, "testPaste")).start();
        } else {
            System.out.println("O caminho fornecido não é um diretório: " + directoryToWatchTestPaste);
        }

        if (Files.isDirectory(directoryToWatchFacasOk)) {
            System.out.println("Iniciando monitoramento da pasta facasOk: " + directoryToWatchFacasOk);
            new Thread(() -> watchDirectoryPath(directoryToWatchFacasOk, "facasOk")).start();
        } else {
            System.out.println("O caminho fornecido não é um diretório: " + directoryToWatchFacasOk);
        }
    }

    private void watchDirectoryPath(Path path, String directoryName) {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            System.out.println("Monitorando pasta: " + path);

            WatchKey key;
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        String fileName = event.context().toString();
                        Path filePath = path.resolve(fileName);
                        System.out.println("Novo arquivo detectado na pasta " + directoryName + ": " + fileName);

                        if (directoryName.equals("facasOk")) {
                            trackFileInFacasOk(filePath);
                        } else {
                            processFile(filePath);
                        }
                    }
                }
                key.reset();
            }
        } catch (IOException | InterruptedException ex) {
            System.out.println("Erro ao monitorar o diretório " + directoryName + ": " + ex.getMessage());
        }
    }

    private void processFile(Path filePath) {
        String fileName = filePath.getFileName().toString();

        if (processedFiles.contains(fileName)) {
            System.out.println("Arquivo já processado na pasta testPaste: " + fileName);
            return;
        }

        processedFiles.add(fileName);
        System.out.println("Processando arquivo na pasta testPaste: " + fileName);

        // Atualizado para separar cliente e prioridade corretamente
        Pattern nrPattern = Pattern.compile("NR(\\d+)(\\w+?)(Vermelho|Amarelo|Azul|Verde)(?:\\.cnc)?");
        Matcher nrMatcher = nrPattern.matcher(fileName);

        Pattern clPattern = Pattern.compile("CL(\\d+)(\\w+?)(Vermelho|Amarelo|Azul|Verde)(?:\\.cnc)?");
        Matcher clMatcher = clPattern.matcher(fileName);

        if (nrMatcher.matches()) {
            String orderNumber = nrMatcher.group(1);
            String client = nrMatcher.group(2);
            String priority = nrMatcher.group(3);

            LocalDateTime creationTime = getCreationTime(filePath);
            System.out.println("Informações extraídas do arquivo NR: NR=" + orderNumber + ", Cliente=" + client + ", Prioridade=" + priority);

            Order order = new Order();
            order.setNr(orderNumber);
            order.setCliente(client);
            order.setPrioridade(priority);
            order.setDataH(creationTime);
            order.setStatus(0); // Status "a cortar" para NR
            Order savedOrder = orderService.saveOrder(order);
            messagingTemplate.convertAndSend("/topic/orders", order);
            System.out.println("Pedido NR criado e enviado via WebSocket: " + savedOrder);

        } else if (clMatcher.matches()) {
            String orderNumber = clMatcher.group(1);
            String client = clMatcher.group(2);
            String priority = clMatcher.group(3);

            LocalDateTime creationTime = getCreationTime(filePath);
            System.out.println("Informações extraídas do arquivo CL: NR=" + orderNumber + ", Cliente=" + client + ", Prioridade=" + priority);

            Order order = new Order();
            order.setNr(orderNumber);
            order.setCliente(client);
            order.setPrioridade(priority);
            order.setDataH(creationTime);
            order.setStatus(0); // Status inicial "a cortar" para CL
            Order savedOrder = orderService.saveOrder(order);
            messagingTemplate.convertAndSend("/topic/orders", order);
            System.out.println("Pedido CL criado e enviado via WebSocket: " + savedOrder);

        } else {
            System.out.println("O arquivo não corresponde ao padrão esperado e será ignorado: " + fileName);
        }
    }

    private void trackFileInFacasOk(Path filePath) {
        String fileName = filePath.getFileName().toString();

        Pattern nrPattern = Pattern.compile("NR(\\d+)(\\w+?)(Vermelho|Amarelo|Azul|Verde)(?:\\.cnc)?");
        Matcher nrMatcher = nrPattern.matcher(fileName);

        Pattern clPattern = Pattern.compile("CL(\\d+)(\\w+?)(Vermelho|Amarelo|Azul|Verde)(?:\\.cnc)?");
        Matcher clMatcher = clPattern.matcher(fileName);

        if (nrMatcher.matches()) {
            String orderNumber = nrMatcher.group(1);
            updateOrderStatus(orderNumber, 1); // Status "cortada pois necessita montar ainda" para NR em facasOk
        } else if (clMatcher.matches()) {
            String orderNumber = clMatcher.group(1);
            updateOrderStatus(orderNumber, 2); // Status "Pronta pq é so isso mesmo" para CL em facasOk
        }
    }

    private void updateOrderStatus(String orderNumber, int status) {
    Optional<Order> orderOpt = orderRepository.findByNr(orderNumber);
    if (orderOpt.isPresent()) {
        Order order = orderOpt.get();
        order.setStatus(status);
        orderRepository.save(order);
        System.out.println("Status do pedido " + orderNumber + " atualizado para " + status);

        // Envia a atualização em tempo real para os clientes via WebSocket
        messagingTemplate.convertAndSend("/topic/orders", order);
        System.out.println("Atualização de status enviada via WebSocket para o pedido: " + orderNumber);
    } else {
        System.out.println("Pedido não encontrado para o número: " + orderNumber);
    }
}

    private LocalDateTime getCreationTime(Path filePath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            return LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            System.out.println("Erro ao obter a data de criação do arquivo: " + e.getMessage());
            return LocalDateTime.now();
        }
    }
}

