package com.example;

import org.bukkit.Bukkit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized logging and telemetry for observability.
 * Purpose: Unified error/info capturing.
 * Pattern: Static utility for global access.
 */
public class TelemetryLogger {
    /** Bukkit logger instance for console output. */
    private static final Logger LOGGER = Bukkit.getLogger();
    
    /** Flag to enable detailed debug telemetry. */
    private static boolean debugMode = false;

    /** 
     * Enable or disable debug telemetry. 
     * @param debug True to enable debug mode.
     */
    public static void setDebugMode(boolean debug) {
        debugMode = debug;
    }

    /** 
     * Log informational messages. 
     * @param message The info message to log.
     */
    public static void info(String message) {
        LOGGER.info("[PlayerLocs] " + message);
    }

    /** 
     * Log warning messages. 
     * @param message The warning message to log.
     */
    public static void warning(String message) {
        LOGGER.warning("[PlayerLocs] " + message);
    }

    /** 
     * Log errors with full stack trace and thread info. 
     * @param context Where the error occurred.
     * @param throwable The exception caught.
     */
    public static void error(String context, Throwable throwable) {
        // Log severe error with context and message
        LOGGER.log(Level.SEVERE, "[PlayerLocs] Error in " + context + ": " + throwable.getMessage());
        
        // Output detailed telemetry if debug mode is enabled
        if (debugMode) {
            LOGGER.severe("Thread: " + Thread.currentThread().getName());
            for (StackTraceElement element : throwable.getStackTrace()) {
                LOGGER.severe("\tat " + element.toString());
            }
        }
    }
}
