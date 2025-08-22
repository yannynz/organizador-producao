package git.yannynz.organizadorproducao.repository;

import git.yannynz.organizadorproducao.config.pagination.CursorPaging;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.model.dto.OrderSearchDTO;
import git.yannynz.organizadorproducao.service.SearchResult;
import git.yannynz.organizadorproducao.config.pagination.CursorStrategy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
public SearchResult<Order> searchDeliveredByCursor(OrderSearchDTO f, int limit,
        CursorPaging.Key after, CursorStrategy strategy) {

    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Order> cq = cb.createQuery(Order.class);
    Root<Order> root = cq.from(Order.class);

    List<Predicate> preds = new ArrayList<>();
    // ... (TODOS os filtros que já te passei antes: text, status, ranges, q)

    // === KEYSET por estratégia ===
    List<Order> rows;
    boolean hasMore;
    CursorPaging.Key lastKey = null;

    if (strategy == CursorStrategy.ID) {
        // cursor só por ID (usa PK): WHERE id < :lastId ORDER BY id DESC
        if (after != null && after.id() != null) {
            preds.add(cb.lessThan(root.get("id"), after.id()));
        }
        cq.where(preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0])));
        cq.orderBy(cb.desc(root.get("id")));

    } else { // DATE_ID
        // cursor composto (dataEntrega,id) sem índice novo
        if (after != null && after.id() != null && after.dataEntrega() != null) {
            var last = ZonedDateTime.ofInstant(after.dataEntrega(), ZoneOffset.UTC);
            preds.add(
                cb.or(
                    cb.lessThan(root.get("dataEntrega"), last),
                    cb.and(
                        cb.equal(root.get("dataEntrega"), last),
                        cb.lessThan(root.get("id"), after.id())
                    )
                )
            );
        }
        cq.where(preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0])));
        cq.orderBy(cb.desc(root.get("dataEntrega")), cb.desc(root.get("id")));
    }

    TypedQuery<Order> query = em.createQuery(cq);
    query.setMaxResults(Math.max(1, limit) + 1);

    rows = query.getResultList();
    hasMore = rows.size() > limit;
    if (hasMore) rows = rows.subList(0, limit);

    if (!rows.isEmpty()) {
        Order lastRow = rows.get(rows.size() - 1);
        lastKey = (strategy == CursorStrategy.ID)
            ? new CursorPaging.Key(null, lastRow.getId()) // <— id-only
            : new CursorPaging.Key(
                (lastRow.getDataEntrega() != null ? lastRow.getDataEntrega().toInstant() : null),
                lastRow.getId()
              );
    }

    return new SearchResult<>(rows, hasMore, lastKey);
}

}

