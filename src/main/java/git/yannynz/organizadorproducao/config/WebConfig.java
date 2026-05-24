package git.yannynz.organizadorproducao.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;

    public WebConfig(
            @Value("${app.cors.allowed-origin-patterns:http://localhost,http://localhost:*,http://nginx-container,http://nginx-container:80,http://frontend-container,http://192.168.*,http://192.168.*:*,http://10.*,http://10.*:*,http://172.*,http://172.*:*}") String[] allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
