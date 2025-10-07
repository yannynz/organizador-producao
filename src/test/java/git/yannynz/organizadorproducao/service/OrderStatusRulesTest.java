package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Order;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderStatusRulesTest {

  @Test
  void applyAutoProntoEntrega_MontadaSemVincoPromoveStatus() {
    Order order = new Order();
    order.setVaiVinco(false);
    order.setEmborrachada(false);
    order.setDataMontagem(ZonedDateTime.now());
    order.setStatus(7); // Montada (corte)

    boolean changed = OrderStatusRules.applyAutoProntoEntrega(order);

    assertTrue(changed);
    assertEquals(2, order.getStatus());
  }

  @Test
  void applyAutoProntoEntrega_VincoConcluidoPromoveStatus() {
    Order order = new Order();
    order.setVaiVinco(true);
    order.setEmborrachada(false);
    order.setDataVinco(ZonedDateTime.now());
    order.setStatus(7);

    boolean changed = OrderStatusRules.applyAutoProntoEntrega(order);

    assertTrue(changed);
    assertEquals(2, order.getStatus());
  }

  @Test
  void applyAutoProntoEntrega_EmborrachadaNaoPromove() {
    Order order = new Order();
    order.setVaiVinco(false);
    order.setEmborrachada(true);
    order.setDataMontagem(ZonedDateTime.now());
    order.setStatus(7);

    boolean changed = OrderStatusRules.applyAutoProntoEntrega(order);

    assertFalse(changed);
    assertEquals(7, order.getStatus());
  }

  @Test
  void applyAutoProntoEntrega_StatusSuperiorNaoRegride() {
    Order order = new Order();
    order.setVaiVinco(false);
    order.setEmborrachada(false);
    order.setDataMontagem(ZonedDateTime.now());
    order.setStatus(3); // Saiu para entrega

    boolean changed = OrderStatusRules.applyAutoProntoEntrega(order);

    assertFalse(changed);
    assertEquals(3, order.getStatus());
  }
}
