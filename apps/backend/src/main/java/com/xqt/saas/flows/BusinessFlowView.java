package com.xqt.saas.flows;

import java.util.List;

public record BusinessFlowView(
    String id,
    String flowCode,
    String customerDirection,
    String name,
    List<String> entryChannels,
    List<String> defaultSteps,
    String settlementModel,
    boolean active,
    String notes,
    String createdAt,
    String updatedAt
) {
}
