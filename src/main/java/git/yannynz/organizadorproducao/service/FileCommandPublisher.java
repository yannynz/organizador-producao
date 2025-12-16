package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.model.dto.FileCommandDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class FileCommandPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public FileCommandPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendCommand(FileCommandDTO command) {
        try {
            String json = objectMapper.writeValueAsString(command);
            rabbitTemplate.convertAndSend("file_commands", json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing FileCommandDTO", e);
        }
    }
}
