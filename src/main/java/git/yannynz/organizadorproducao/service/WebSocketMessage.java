package git.yannynz.organizadorproducao.service;

public class WebSocketMessage {
    private String action;
    private Object data;

    public WebSocketMessage(String action, Object data) {
        this.action = action;
        this.data = data;
    }

    public String getAction() {
        return action;
    }

    public Object getData() {
        return data;
    }
}