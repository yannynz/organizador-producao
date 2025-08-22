package git.yannynz.organizadorproducao.config.pagination;

public enum CursorStrategy {
    ID,       // super performático: ORDER BY id DESC (usa PK)
    DATE_ID   // exatidão por dataEntrega DESC, id DESC (sem novo índice)
}

