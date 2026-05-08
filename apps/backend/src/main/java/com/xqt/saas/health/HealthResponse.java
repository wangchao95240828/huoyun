package com.xqt.saas.health;

public record HealthResponse(boolean ok, String service, String time, String database) {
}
