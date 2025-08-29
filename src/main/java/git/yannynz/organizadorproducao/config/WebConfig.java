package git.yannynz.organizadorproducao.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost", "http://nginx-container:80", "http://frontend-container/", "http://192.168.10.13", "http://192.168.10.29") 
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true); 
    }
}
