package com.xqt.saas.common;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int pageSize) {
    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
