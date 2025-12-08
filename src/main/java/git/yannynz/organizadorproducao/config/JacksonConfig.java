package git.yannynz.organizadorproducao.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        // This module handles Hibernate proxies during serialization.
        // It prevents "No serializer found" errors for lazy-loaded entities.
        return new Hibernate6Module();
    }
}
