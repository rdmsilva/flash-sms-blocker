package com.flashblocker;

import android.content.SharedPreferences;

/**
 * Central storage for statistics and the block log.
 * Uses SharedPreferences (no database needed).
 *
 * Takes the SharedPreferences instance directly (callers pass
 * {@code context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)}) so the whole
 * class is unit-testable on the JVM with an in-memory implementation.
 */
public class BlockStats {
    public static final String PREFS_NAME = "flash_blocker_stats";
    private static final String KEY_COUNT = "blocked_count";
    private static final String KEY_LAST_TIME = "last_blocked_time";
    private static final String KEY_LAST_SENDER = "last_blocked_sender";
    private static final String KEY_LOG = "event_log";
    private static final String KEY_LEARNING = "learning_mode";
    private static final String KEY_PAUSED = "paused_by_user";
    private static final String KEY_PAUSE_UNTIL = "paused_until_millis";

    private final SharedPreferences prefs;

    public BlockStats(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public void recordBlock(String source, String detail) {
        int count = prefs.getInt(KEY_COUNT, 0) + 1;
        String timestamp = appendLog(source, detail);
        prefs.edit()
            .putInt(KEY_COUNT, count)
            .putString(KEY_LAST_TIME, timestamp)
            .putString(KEY_LAST_SENDER, source + " - " + detail)
            .apply();
    }

    /**
     * Records an event in the log WITHOUT incrementing the block counter.
     * Used by learning mode to inspect dialogs without closing them.
     */
    public void recordEvent(String source, String detail) {
        appendLog(source, detail);
    }

    /**
     * Appends an entry to the log (keeping the last 50 events) and persists it.
     * Returns the timestamp used.
     */
    private String appendLog(String source, String detail) {
        String timestamp = java.text.SimpleDateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM
        ).format(new java.util.Date());

        String log = prefs.getString(KEY_LOG, "");
        String entry = "[" + timestamp + "] " + source + ": " + detail + "\n";
        // Keep only the 50 most recent events (the log is newest-first, so the
        // first lines are the ones to keep).
        String[] lines = (entry + log).split("\n");
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (int i = 0; i < lines.length && kept < 50; i++) {
            if (!lines[i].trim().isEmpty()) {
                sb.append(lines[i]).append("\n");
                kept++;
            }
        }
        prefs.edit().putString(KEY_LOG, sb.toString()).apply();
        return timestamp;
    }

    public int getBlockedCount() {
        return prefs.getInt(KEY_COUNT, 0);
    }

    public String getLastBlockedTime() {
        return prefs.getString(KEY_LAST_TIME, "nunca");
    }

    public String getLastBlockedSender() {
        return prefs.getString(KEY_LAST_SENDER, "nenhum");
    }

    public String getLog() {
        return prefs.getString(KEY_LOG, "Nenhum evento ainda");
    }

    /** Clears the counter and history, but keeps the learning-mode setting. */
    public void clear() {
        prefs.edit()
            .remove(KEY_COUNT)
            .remove(KEY_LAST_TIME)
            .remove(KEY_LAST_SENDER)
            .remove(KEY_LOG)
            .apply();
    }

    // ---- Learning mode (runtime toggle, controlled from the ⋮ menu) ----

    public boolean isLearningMode() {
        return prefs.getBoolean(KEY_LEARNING, false);
    }

    public void setLearningMode(boolean on) {
        prefs.edit().putBoolean(KEY_LEARNING, on).apply();
    }

    // ---- Pause (the user disabled the service on purpose, e.g. for bank apps) ----

    /**
     * True while the user has paused blocking from the ⋮ menu. Set right before
     * the service disables itself, cleared when it reconnects, so the UI can
     * show "paused" instead of a generic "inactive".
     */
    public boolean isPaused() {
        return prefs.getBoolean(KEY_PAUSED, false);
    }

    public void setPaused(boolean on) {
        prefs.edit().putBoolean(KEY_PAUSED, on).apply();
    }

    /**
     * Epoch millis when a timed pause ({@link #setPauseUntil}) is due to
     * expire. 0 means no timed pause is pending.
     */
    public long getPauseUntil() {
        return prefs.getLong(KEY_PAUSE_UNTIL, 0L);
    }

    public void setPauseUntil(long epochMillis) {
        prefs.edit().putLong(KEY_PAUSE_UNTIL, epochMillis).apply();
    }
}
