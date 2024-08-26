package git.yannynz.organizadorproducao.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/orders").permitAll() 
                .anyRequest().authenticated()
            .and()
            .csrf().disable(); // Desabilita CSRF para WebSocket
        return http.build();
    }
}
