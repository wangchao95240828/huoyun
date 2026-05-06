package com.xqt.saas.orders;

public record FlowDefinition(
    String customerDirection,
    String serviceMode,
    String defaultOrderEntryType,
    String orderNoPrefix
) {
}
