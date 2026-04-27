package git.yannynz.organizadorproducao.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@WebMvcTest(
        controllers = WebSocketSecurityTest.ProbeController.class,
        properties = "app.cors.allowed-origins=http://192.168.10.13,http://localhost:4200")
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class})
class WebSocketSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @MockBean
    private AuthenticationProvider authenticationProvider;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void websocketHandshakePath_IsPublicWithoutJwt() throws Exception {
        mvc.perform(get("/ws/orders"))
                .andExpect(status().isNotFound());
    }

    @Test
    void corsOrigins_ComeFromApplicationProperty() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/orders");
        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).containsExactly("http://192.168.10.13", "http://localhost:4200");
    }

    @RestController
    static class ProbeController {

        @GetMapping("/ws/orders")
        ResponseEntity<Void> websocketProbe() {
            return ResponseEntity.ok().build();
        }
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
