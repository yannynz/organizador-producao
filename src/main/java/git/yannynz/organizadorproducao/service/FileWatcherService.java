package git.yannynz.organizadorproducao.service;

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

@Service
public class FileWatcherService {

    private final Path directoryToWatch = Paths.get("/home/yann/Documentos/testPaste/");
    private final Set<String> processedFiles = new HashSet<>();

    @Autowired
    private OrderService orderService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private OrderRepository orderRepository;

    public void startWatching() {
        if (Files.isDirectory(directoryToWatch)) {
            System.out.println("Iniciando monitoramento do diretório: " + directoryToWatch);
            watchDirectoryPath(directoryToWatch);
        } else {
            System.out.println("O caminho fornecido não é um diretório: " + directoryToWatch);
        }
    }

    private void watchDirectoryPath(Path path) {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            WatchKey key;
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        String fileName = event.context().toString();
                        Path filePath = path.resolve(fileName);
                        System.out.println("Novo arquivo detectado: " + fileName);
                        processFile(filePath);
                    }
                }
                key.reset();
            }
        } catch (IOException | InterruptedException ex) {
            System.out.println("Erro ao monitorar o diretório: " + ex.getMessage());
        }
    }

    private void processFile(Path filePath) {
        String fileName = filePath.getFileName().toString();

        if (processedFiles.contains(fileName)) {
            System.out.println("Arquivo já processado: " + fileName);
            return;
        }

        processedFiles.add(fileName);

        System.out.println("Processando arquivo: " + fileName);

        Pattern pattern = Pattern.compile("NR(\\d+)(\\w+)(Vermelho|Amarelo|Azul|Verde)(?:\\.pdf)?");
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.matches()) {
            String orderNumber = matcher.group(1);
            String client = matcher.group(2);
            String priority = matcher.group(3);

            LocalDateTime creationTime = getCreationTime(filePath);
            System.out.println("Informações extraídas do arquivo: NR=" + orderNumber + ", Cliente=" + client
                    + ", Prioridade=" + priority);

            Order order = new Order();
            order.setNr(orderNumber);
            order.setCliente(client);
            order.setPrioridade(priority);
            order.setDataH(creationTime);
            Order savedOrder = orderService.saveOrder(order);
            messagingTemplate.convertAndSend("/topic/orders", order);
            System.out.println("Pedido criado e enviado via WebSocket: "+ savedOrder);

        } else {
            System.out.println("O arquivo não corresponde ao padrão esperado e será ignorado: " + fileName);
        }
    }

    private LocalDateTime getCreationTime(Path filePath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            LocalDateTime creationTime = LocalDateTime.ofInstant(attrs.creationTime().toInstant(),
                    ZoneId.systemDefault());
            System.out.println("Data de criação do arquivo: " + creationTime);
            return creationTime;
        } catch (IOException e) {
            System.out.println("Erro ao obter a data de criação do arquivo: " + e.getMessage());
            return LocalDateTime.now();
        }
    }
}
