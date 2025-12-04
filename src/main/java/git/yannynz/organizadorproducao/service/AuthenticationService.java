package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.controller.auth.AuthenticationRequest;
import git.yannynz.organizadorproducao.controller.auth.AuthenticationResponse;
import git.yannynz.organizadorproducao.controller.auth.RegisterRequest;
import git.yannynz.organizadorproducao.domain.user.*;
import git.yannynz.organizadorproducao.infra.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository repository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationResponse register(RegisterRequest request) {
        if (repository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        var user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.OPERADOR)
                .active(true)
                .build();
        
        repository.save(user);

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("id", user.getId());
        claims.put("name", user.getName());

        var jwtToken = jwtService.generateToken(claims, user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var user = repository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("id", user.getId());
        claims.put("name", user.getName());
        
        var jwtToken = jwtService.generateToken(claims, user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    @Transactional
    public void forgotPassword(String email) {
        var userOptional = repository.findByEmail(email);
        if (userOptional.isEmpty()) {
            // Return silently to avoid user enumeration
            return;
        }
        var user = userOptional.get();
        String token = UUID.randomUUID().toString();
        
        // Invalidate existing tokens for this user
        // Note: Ideally we should have a method in repo to find by user or just let multiple tokens exist. 
        // For simplicity and security, let's just create a new one. 
        // If we want to enforce one token at a time, we would delete old ones here.
        
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(30))
                .build();
        
        tokenRepository.save(resetToken);
        
        emailService.sendPasswordResetEmail(user.getEmail(), token);
    }

    public boolean validateResetToken(String token) {
        var resetTokenOptional = tokenRepository.findByToken(token);
        if (resetTokenOptional.isEmpty()) {
            return false;
        }
        var resetToken = resetTokenOptional.get();
        return resetToken.getExpiryDate().isAfter(LocalDateTime.now());
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        var resetTokenOptional = tokenRepository.findByToken(token);
        if (resetTokenOptional.isEmpty()) {
            throw new IllegalArgumentException("Invalid token");
        }
        var resetToken = resetTokenOptional.get();
        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token expired");
        }
        
        var user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        repository.save(user);
        
        tokenRepository.delete(resetToken);
    }
}
