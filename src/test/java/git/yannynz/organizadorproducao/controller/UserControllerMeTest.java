package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.domain.user.User;
import git.yannynz.organizadorproducao.domain.user.UserRole;
import git.yannynz.organizadorproducao.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class UserControllerMeTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getMe_ShouldReturnCurrentUser_WhenAuthenticated() {
        // Arrange
        String email = "me@example.com";
        User user = new User();
        user.setEmail(email);
        user.setName("Myself");
        user.setRole(UserRole.ADMIN);

        // Setup Security Context with standard Principal (String username usually, but here we just need getName() to work)
        // In JwtAuthenticationFilter it sets UserDetails as principal.
        // Let's mimic what SecurityContextHolder usually holds. 
        // If we look at Controller: auth.getName() is used.
        
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userService.findByEmail(email)).thenReturn(Optional.of(user));

        // Act
        ResponseEntity<User> response = userController.getMe();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(email, response.getBody().getEmail());
    }

    @Test
    void getMe_ShouldReturn401_WhenNotAuthenticated() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(null);

        // Act
        ResponseEntity<User> response = userController.getMe();

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
