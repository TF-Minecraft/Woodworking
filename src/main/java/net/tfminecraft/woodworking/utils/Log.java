package net.tfminecraft.woodworking.utils;

import java.util.logging.Logger;

import net.tfminecraft.woodworking.Woodworking;

/**
 * Config loading can run before the plugin logger is available, so warnings fall back to a
 * plain logger instead of throwing and hiding the config problem they were meant to report.
 */
public final class Log {

    private static final Logger FALLBACK = Logger.getLogger("Woodworking");

    private Log() {
    }

    public static Logger get() {
        return Woodworking.plugin == null ? FALLBACK : Woodworking.plugin.getLogger();
    }

    public static void warn(String message) {
        get().warning(message);
    }

    public static void info(String message) {
        get().info(message);
    }
}
