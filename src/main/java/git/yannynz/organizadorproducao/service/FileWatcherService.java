package git.yannynz.organizadorproducao.service;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.model.ErroredFiles;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.Timestamp;
import java.util.List;

@Service
public class FileWatcherService {
    @Autowired
    private OrderRepository orderRepository;
    ErroredFiles erroredFiles=new ErroredFiles();
    @PostConstruct
    public void watchDirectory() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Path path = Paths.get("/caminho/para/pasta");
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

            WatchKey key;
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    String fileName = event.context().toString();
                    processFile(fileName);
                }
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void processFile(String fileName) {
        try {
            String[] parts = fileName.split(" ");
            if (parts.length < 4) {
                erroredFiles.setErrorName(fileName);
                return;
            }
            Order order = new Order();
            order.setNr(parts[1]);
            order.setCliente(parts[2]);
            order.setPrioridade(parts[3]);
            order.setDataHora(new Timestamp(new File("/caminho/para/pasta/" + fileName).lastModified()).toLocalDateTime());
            orderRepository.save(order);
        } catch (Exception e) {
            erroredFiles.setErrorName(fileName);
            e.printStackTrace();
        }
    }


    public List<String> getErroredFiles() {
        return List.of(erroredFiles.getErrorName());
    }
}


