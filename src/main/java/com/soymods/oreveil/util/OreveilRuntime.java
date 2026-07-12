package com.soymods.oreveil.util;

public final class OreveilRuntime {
    private OreveilRuntime() {
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
