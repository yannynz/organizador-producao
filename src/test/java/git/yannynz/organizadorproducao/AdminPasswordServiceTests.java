package git.yannynz.organizadorproducao.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AdminPasswordServiceTests {

    @Test
    void validatePassword_ShouldReturnTrue_ForCorrectPassword() {
        // Setup: Cria o serviço com o hash correto
        AdminPasswordService passwordService = new AdminPasswordService();
        String correctPassword = "password";
        String correctHash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd8f1098f48e16c8cc9"; // SHA-256 de 'password'

        // Simula o hash armazenado
        passwordService.setStoredHash(correctHash);

        // Valida a senha correta
        boolean result = passwordService.validatePassword(correctPassword);

        // Verifica o resultado
        assertTrue(result, "A senha correta deveria ser validada com sucesso.");
    }

    @Test
    void validatePassword_ShouldReturnFalse_ForIncorrectPassword() {
        // Setup: Cria o serviço com o hash correto
        AdminPasswordService passwordService = new AdminPasswordService();
        String incorrectPassword = "wrongpassword";
        String correctHash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd8f1098f48e16c8cc9"; // SHA-256 de 'password'

        // Simula o hash armazenado
        passwordService.setStoredHash(correctHash);

        // Valida uma senha incorreta
        boolean result = passwordService.validatePassword(incorrectPassword);

        // Verifica o resultado
        assertFalse(result, "Uma senha incorreta não deveria ser validada.");
    }
}

