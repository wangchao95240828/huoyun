package com.xqt.saas.common;

public record CommandResponse(boolean success) {
    public static CommandResponse ok() {
        return new CommandResponse(true);
    }
}
