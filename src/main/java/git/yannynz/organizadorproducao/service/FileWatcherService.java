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

@Service
public class FileWatcherService {

    private final Path directoryToWatchTestPaste = Paths.get("/laser");
    private final Path directoryToWatchFacasOk = Paths.get("/facasOk");
    private final Set<String> processedFiles = new HashSet<>();

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private OrderRepository orderRepository;

    public void startWatching() {
        if (Files.isDirectory(directoryToWatchTestPaste)) {
            System.out.println("Iniciando monitoramento da pasta testPaste: " + directoryToWatchTestPaste);
            new Thread(() -> watchDirectoryPath(directoryToWatchTestPaste, "testPaste")).start();
            scanDirectory(directoryToWatchTestPaste, "testPaste");
        } else {
            System.out.println("O caminho fornecido não é um diretório: " + directoryToWatchTestPaste);
        }

        if (Files.isDirectory(directoryToWatchFacasOk)) {
            System.out.println("Iniciando monitoramento da pasta facasOk: " + directoryToWatchFacasOk);
            new Thread(() -> watchDirectoryPath(directoryToWatchFacasOk, "facasOk")).start();
            scanDirectory(directoryToWatchFacasOk, "facasOk");
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

        Pattern pattern = Pattern.compile("NR(\\d+)(\\w+?)_(VERMELHO|AMARELO|AZUL|VERDE)(?:\\.CNC)?");
        Matcher matcher = pattern.matcher(fileName);

        if (matcher.matches()) {
            String orderNumber = matcher.group(1);
            String client = matcher.group(2);
            String priority = matcher.group(3);

            if (orderRepository.findByNr(orderNumber).isPresent()) {
                System.out.println("Pedido com NR " + orderNumber + " já existe. Ignorando novo arquivo.");
                return;
            }

            LocalDateTime creationTime = getCreationTime(filePath);
            System.out.println("Informações extraídas do arquivo: NR=" + orderNumber + ", Cliente=" + client + ", Prioridade=" + priority);

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
            System.out.println("O arquivo não corresponde ao padrão esperado e será ignorado: " + fileName);
        }
    }

    private void trackFileInFacasOk(Path filePath) {
        String fileName = filePath.getFileName().toString();

        Pattern pattern = Pattern.compile("NR(\\d+)(\\w+?)_(VERMELHO|AMARELO|AZUL|VERDE)(?:\\.CNC)?");
        Matcher matcher = pattern.matcher(fileName);

        if (matcher.matches()) {
            String orderNumber = matcher.group(1);
            updateOrderStatus(orderNumber, 1); // Atualizar status para "cortada"
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

    private LocalDateTime getCreationTime(Path filePath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            return LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            System.out.println("Erro ao obter a data de criação do arquivo: " + e.getMessage());
            return LocalDateTime.now();
        }
    }

    private void scanDirectory(Path directory, String directoryName) {
        try {
            Files.list(directory).filter(Files::isRegularFile).forEach(filePath -> {
                System.out.println("Arquivo existente encontrado na pasta " + directoryName + ": " + filePath.getFileName());
                if (directoryName.equals("facasOk")) {
                    trackFileInFacasOk(filePath);
                } else {
                    processFile(filePath);
                }
            });
        } catch (IOException e) {
            System.out.println("Erro ao realizar varredura inicial na pasta " + directoryName + ": " + e.getMessage());
        }
    }
}

