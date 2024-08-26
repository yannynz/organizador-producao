package git.yannynz.organizadorproducao.service;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import git.yannynz.organizadorproducao.model.Order;

public class CustomWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Order order = objectMapper.readValue(message.getPayload(), Order.class);
        System.out.println("Pedido recebido via WebSocket: " + order);

        session.sendMessage(new TextMessage("Pedido recebido: " + order.toString()));
    }
}
