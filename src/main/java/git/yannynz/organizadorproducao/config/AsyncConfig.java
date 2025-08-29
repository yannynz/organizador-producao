package git.yannynz.organizadorproducao.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
  @Bean(name = "opExecutor")
  public Executor opExecutor() {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setThreadNamePrefix("op-async-");
    ex.setCorePoolSize(2);
    ex.setMaxPoolSize(4);
    ex.setQueueCapacity(100);
    ex.initialize();
    return ex;
  }

  // em alguma @Configuration do seu projeto
@org.springframework.scheduling.annotation.EnableScheduling
class SchedulingConfig {}

}

