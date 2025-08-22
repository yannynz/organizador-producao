package git.yannynz.organizadorproducao.repository;

import git.yannynz.organizadorproducao.config.pagination.CursorPaging;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.model.dto.OrderSearchDTO;
import git.yannynz.organizadorproducao.service.SearchResult;
import git.yannynz.organizadorproducao.config.pagination.CursorStrategy;

public interface OrderRepositoryCustom {
    SearchResult<Order> searchDeliveredByCursor(OrderSearchDTO filters, int limit,
            CursorPaging.Key after, CursorStrategy strategy);
}

