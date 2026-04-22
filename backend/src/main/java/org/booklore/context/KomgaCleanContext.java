package org.booklore.context;

/**
 * ThreadLocal context to track whether the Komga API "clean" mode is enabled.
 * When clean mode is enabled:
 * - Fields ending with "Lock" are excluded from JSON serialization
 * - Null values are excluded from JSON serialization
 * - Metadata fields (language, summary, etc.) are allowed to be null
 */
public class KomgaCleanContext {
    private static final ThreadLocal<Boolean> cleanModeEnabled = ThreadLocal.withInitial(() -> false);

    public static void setCleanMode(boolean enabled) {
        cleanModeEnabled.set(enabled);
    }

    public static boolean isCleanMode() {
        return cleanModeEnabled.get();
    }

    public static void clear() {
        cleanModeEnabled.remove();
    }
}
