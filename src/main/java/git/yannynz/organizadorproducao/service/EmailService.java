package git.yannynz.organizadorproducao.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Async
    public void sendPasswordResetEmail(String to, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        
        String subject = "Recuperação de Senha - Organizador de Produção";
        String text = "Olá,\n\n" +
                "Você solicitou a redefinição de senha para sua conta.\n" +
                "Clique no link abaixo para definir uma nova senha:\n\n" +
                resetUrl + "\n\n" +
                "Se você não solicitou isso, ignore este e-mail.\n\n" +
                "Este link expira em 30 minutos.";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);
    }
}
