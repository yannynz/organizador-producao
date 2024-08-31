package git.yannynz.organizadorproducao.model;

import org.springframework.context.ApplicationEvent;

public class OrderSavedEvent extends ApplicationEvent {

    private final Order order;

    public OrderSavedEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }
}
