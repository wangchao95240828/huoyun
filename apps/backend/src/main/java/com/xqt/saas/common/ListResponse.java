package com.xqt.saas.common;

import java.util.List;

public record ListResponse<T>(List<T> items) {
    public ListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
