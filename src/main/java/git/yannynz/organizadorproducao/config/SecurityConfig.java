package git.yannynz.organizadorproducao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeRequests()
                .requestMatchers("/api/orders/**").permitAll() // Permite acesso aos endpoints de pedidos sem
                .requestMatchers("/ws/**").permitAll()        // Permite acesso ao WebSocket sem autenticação
                // autenticação
                .anyRequest().authenticated()
                .and()
                .httpBasic(); // Ou .formLogin() para autenticação via formulário

        return http.build();
    }
}
