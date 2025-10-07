package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Order;
import java.util.Set;

/**
 * Regras utilitárias para estados automáticos dos pedidos.
 */
public final class OrderStatusRules {

  private static final int STATUS_EM_PRODUCAO = 0;
  private static final int STATUS_CORTADA = 1;
  private static final int STATUS_PRONTO_ENTREGA = 2;
  private static final int STATUS_TIRADA = 6;
  private static final int STATUS_MONTADA_CORTE = 7;
  private static final int STATUS_MONTADA_COMPLETA = 8;

  private static final Set<Integer> STATUS_ELEGIVEIS_PRONTO = Set.of(
      STATUS_EM_PRODUCAO,
      STATUS_CORTADA,
      STATUS_TIRADA,
      STATUS_MONTADA_CORTE,
      STATUS_MONTADA_COMPLETA
  );

  private OrderStatusRules() {}

  /**
   * Ajusta automaticamente o status do pedido para "Pronto para entrega" (2)
   * quando as regras de negócio estiverem satisfeitas.
   *
   * @return {@code true} se o status foi alterado.
   */
  public static boolean applyAutoProntoEntrega(Order order) {
    if (order == null) return false;
    if (order.isEmborrachada()) return false;

    boolean montagemConcluidaSemVinco = !order.isVaiVinco() && order.getDataMontagem() != null;
    boolean vincoConcluido = order.isVaiVinco() && order.getDataVinco() != null;

    if ((montagemConcluidaSemVinco || vincoConcluido)
        && STATUS_ELEGIVEIS_PRONTO.contains(order.getStatus())) {
      order.setStatus(STATUS_PRONTO_ENTREGA);
      return true;
    }

    return false;
  }
}
