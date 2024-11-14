package git.yannynz.organizadorproducao;

import git.yannynz.organizadorproducao.service.FileWatcherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrganizadorProducao implements CommandLineRunner {

    @Autowired
    private FileWatcherService fileWatcherService;

    public static void main(String[] args) {
        SpringApplication.run(OrganizadorProducao.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        fileWatcherService.startWatching();
    }
}
