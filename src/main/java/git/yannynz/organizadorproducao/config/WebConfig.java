package git.yannynz.organizadorproducao.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost", "http://192.168.0.101", "http://nginx-container", "http://frontend-container/") // Permitir requisições de qualquer origem
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true); // Permitir credenciais, se necessário
    }
}
