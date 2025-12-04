package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.domain.user.*;
import git.yannynz.organizadorproducao.infra.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository repository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(
                repository,
                tokenRepository,
                jwtService,
                authenticationManager,
                emailService,
                passwordEncoder
        );
    }

    @Test
    void forgotPassword_WithValidEmail_ShouldSaveTokenAndSendEmail() {
        String email = "user@example.com";
        User user = User.builder().id(1L).email(email).build();
        
        when(repository.findByEmail(email)).thenReturn(Optional.of(user));
        
        service.forgotPassword(email);
        
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getToken()).isNotNull();
        assertThat(savedToken.getExpiryDate()).isAfter(LocalDateTime.now());
        
        verify(emailService).sendPasswordResetEmail(eq(email), eq(savedToken.getToken()));
    }

    @Test
    void forgotPassword_WithInvalidEmail_ShouldDoNothing() {
        String email = "unknown@example.com";
        when(repository.findByEmail(email)).thenReturn(Optional.empty());
        
        service.forgotPassword(email);
        
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void validateResetToken_WithValidToken_ShouldReturnTrue() {
        String tokenString = "valid-token";
        PasswordResetToken token = PasswordResetToken.builder()
                .token(tokenString)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        
        when(tokenRepository.findByToken(tokenString)).thenReturn(Optional.of(token));
        
        boolean isValid = service.validateResetToken(tokenString);
        
        assertThat(isValid).isTrue();
    }

    @Test
    void validateResetToken_WithExpiredToken_ShouldReturnFalse() {
        String tokenString = "expired-token";
        PasswordResetToken token = PasswordResetToken.builder()
                .token(tokenString)
                .expiryDate(LocalDateTime.now().minusMinutes(1))
                .build();
        
        when(tokenRepository.findByToken(tokenString)).thenReturn(Optional.of(token));
        
        boolean isValid = service.validateResetToken(tokenString);
        
        assertThat(isValid).isFalse();
    }

    @Test
    void validateResetToken_WithNonExistentToken_ShouldReturnFalse() {
        String tokenString = "unknown-token";
        when(tokenRepository.findByToken(tokenString)).thenReturn(Optional.empty());
        
        boolean isValid = service.validateResetToken(tokenString);
        
        assertThat(isValid).isFalse();
    }

    @Test
    void resetPassword_WithValidToken_ShouldUpdatePasswordAndRemoveToken() {
        String tokenString = "valid-token";
        String newPassword = "newPassword123";
        String encodedPassword = "encodedPassword";
        
        User user = User.builder().id(1L).email("user@example.com").password("oldPassword").build();
        PasswordResetToken token = PasswordResetToken.builder()
                .token(tokenString)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        
        when(tokenRepository.findByToken(tokenString)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode(newPassword)).thenReturn(encodedPassword);
        
        service.resetPassword(tokenString, newPassword);
        
        verify(repository).save(user);
        assertThat(user.getPassword()).isEqualTo(encodedPassword);
        verify(tokenRepository).delete(token);
    }

    @Test
    void resetPassword_WithExpiredToken_ShouldThrowException() {
        String tokenString = "expired-token";
        String newPassword = "newPassword123";
        
        PasswordResetToken token = PasswordResetToken.builder()
                .token(tokenString)
                .expiryDate(LocalDateTime.now().minusMinutes(1))
                .build();
        
        when(tokenRepository.findByToken(tokenString)).thenReturn(Optional.of(token));
        
        assertThatThrownBy(() -> service.resetPassword(tokenString, newPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token expired");
        
        verify(repository, never()).save(any());
        verify(tokenRepository, never()).delete(any());
    }

    @Test
    void resetPassword_WithInvalidToken_ShouldThrowException() {
        String tokenString = "invalid-token";
        String newPassword = "newPassword123";
        
        when(tokenRepository.findByToken(tokenString)).thenReturn(Optional.empty());
        
        assertThatThrownBy(() -> service.resetPassword(tokenString, newPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid token");
        
        verify(repository, never()).save(any());
        verify(tokenRepository, never()).delete(any());
    }
}
