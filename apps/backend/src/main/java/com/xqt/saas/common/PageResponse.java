package com.xqt.saas.common;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int pageSize) {
}
