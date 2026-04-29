package com.soymods.oreveil.obfuscation.transport;

import java.util.Locale;

public enum TransportMode {
    AUTO,
    BLOCK_UPDATE_SYNC,
    PROTOCOLLIB;

    public static TransportMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }

        try {
            return TransportMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AUTO;
        }
    }
}
