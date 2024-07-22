package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FileWatcherService {

    private final Path directoryToWatch = Paths.get("/home/yann/Documentos/testPaste/");

    @Autowired
    private OrderService orderService;

    public void startWatching() {
        if (Files.isDirectory(directoryToWatch)) {
            System.out.println("Iniciando monitoramento do diretório: " + directoryToWatch);
            watchDirectoryPath(directoryToWatch);
        } else {
            System.out.println("O caminho fornecido não é um diretório: " + directoryToWatch);
        }
    }

    public void watchDirectoryPath(Path path) {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            System.out.println("Monitorando diretório para novos arquivos...");

            WatchKey key;
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        String fileName = event.context().toString();
                        System.out.println("Arquivo criado: " + fileName);
                        processFile(path.resolve(fileName));
                    }
                }
                key.reset();
            }
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    private void processFile(Path filePath) {
        String fileName = filePath.getFileName().toString();
        System.out.println("Processando arquivo: " + fileName);

        // Regex para extrair NR, cliente e prioridade do nome do arquivo
        Pattern pattern = Pattern.compile("NR(\\d+)(\\w+)(Vermelho|Amarelo|Azul|Verde)(?:\\.pdf)?");
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.matches()) {
            String orderNumber = matcher.group(1);
            String client = matcher.group(2);
            String priority = matcher.group(3);

            Order order = new Order();
            order.setNr(orderNumber);
            order.setCliente(client);
            order.setPrioridade(priority);

            orderService.saveOrder(order);
            System.out.println("Pedido salvo: " + order);
        } else {
            System.out.println("Nome do arquivo não corresponde ao formato esperado: " + fileName);
        }
    }
}
