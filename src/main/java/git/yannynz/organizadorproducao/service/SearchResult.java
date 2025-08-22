package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.config.pagination.CursorPaging;
import java.util.List;

public record SearchResult<T>(List<T> items, boolean hasMore, CursorPaging.Key lastKey) {}

