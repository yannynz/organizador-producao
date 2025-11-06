package git.yannynz.organizadorproducao;

import git.yannynz.organizadorproducao.config.DXFAnalysisProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(DXFAnalysisProperties.class)
public class OrganizadorProducao implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(OrganizadorProducao.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Iniciando serviço de monitoramento de pastas...");
    }
}
