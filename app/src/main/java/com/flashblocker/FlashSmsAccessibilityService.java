package com.flashblocker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.util.Log;

import java.util.List;
import java.util.Locale;

/**
 * AccessibilityService that closes the Flash SMS (Class 0) popup.
 *
 * This is the only approach that actually works without root. The telephony
 * framework draws the Class 0 popup before any app is notified, so we can't
 * "prevent" the popup; we can only close it instantly.
 *
 * The service is device-agnostic: which windows count as a Class 0 popup, and
 * how to dismiss them, live in {@link FlashPopupRule#KNOWN}. Supporting a new
 * device means adding a rule there, not touching this class.
 *
 * Learning Mode is a runtime toggle (⋮ menu, stored via {@link BlockStats}):
 * when on, the service closes nothing and just logs every new window, to help
 * discover a new device's popup identity.
 */
public class FlashSmsAccessibilityService extends AccessibilityService {
    private static final String TAG = "FlashBlocker/A11y";
    private static final String LEARN_TAG = "FlashBlocker/LEARN";

    /** Prevents acting twice on the same popup (a device may fire two events,
     *  e.g. an AlertDialog and its Activity, a few ms apart). */
    private long lastActionAt = 0L;
    private static final long DEBOUNCE_MS = 3000L;

    /** Right after the event the dismiss button may not be in the a11y tree yet
     *  (seen live on the A73: ~17 ms after the event the dialog had no buttons).
     *  Retry once after this delay before falling back to BACK. */
    private static final long RETRY_DELAY_MS = 150L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BlockStats stats;

    /** Live instance while the service is connected; lets MainActivity pause it. */
    private static FlashSmsAccessibilityService instance;

    /**
     * Pauses blocking by REALLY disabling the accessibility service
     * ({@link #disableSelf()}). A soft pause (keeping the service bound but
     * inert) would not help: bank anti-fraud checks react to the service being
     * enabled at all, not to what it does. Resuming requires the user to
     * re-enable the service in the accessibility settings — Android offers no
     * API to re-enable yourself.
     *
     * @return false if the service is not connected (nothing to pause).
     */
    public static boolean pause() {
        FlashSmsAccessibilityService s = instance;
        if (s == null) return false;
        // Order matters: persist the paused flag before disableSelf() tears
        // the service down, so the UI can tell "paused" from "never enabled".
        s.stats().setPaused(true);
        s.stats().recordEvent("PAUSA", "Bloqueio pausado pelo usuário");
        Log.i(TAG, "Bloqueio pausado pelo usuário (disableSelf)");
        s.disableSelf();
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility service connected");
        instance = this;
        stats = stats();
        // If the user re-enabled the service after a pause, the pause is over.
        stats.setPaused(false);

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            // We only need window-state changes (a dialog appearing).
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.DEFAULT
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.notificationTimeout = 50;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        // Learning mode (runtime toggle): only log, don't close anything.
        if (stats().isLearningMode()) {
            learnDump(event);
            return;
        }

        String pkg = event.getPackageName() != null
            ? event.getPackageName().toString() : "";
        String cls = event.getClassName() != null
            ? event.getClassName().toString() : "";
        String eventText = event.getText() != null
            ? event.getText().toString().toLowerCase(Locale.ROOT) : "";

        // Match against the known devices. null = not a Class 0 popup.
        final FlashPopupRule rule = FlashPopupRule.match(pkg, cls, eventText);
        if (rule == null) return;

        // Debounce: the same popup may fire more than one event.
        long now = SystemClock.uptimeMillis();
        if (now - lastActionAt < DEBOUNCE_MS) return;
        lastActionAt = now;

        Log.w(TAG, ">>> Flash SMS (Class 0) detected [" + rule.label + "] — closing <<<");

        // Strategy 1: click the discard button (discards without saving).
        if (findAndClickDismiss(rule)) {
            recordOutcome(true);
            return;
        }

        // The button may not be in the a11y tree yet; retry once before BACK.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean closed = findAndClickDismiss(rule);
                // Strategy 2: BACK also finishes the popup Activity (discards).
                if (!closed) {
                    Log.i(TAG, "Dismiss button not found, using GLOBAL_ACTION_BACK");
                    closed = performGlobalAction(GLOBAL_ACTION_BACK);
                }
                recordOutcome(closed);
            }
        }, RETRY_DELAY_MS);
    }

    /** Logs the result; only a popup actually closed counts as a block. */
    private void recordOutcome(boolean closed) {
        if (closed) {
            stats().recordBlock("CLASS0", "Flash SMS fechado automaticamente");
        } else {
            stats().recordEvent("CLASS0", "Flash SMS detectado (falha ao fechar)");
        }
        Log.i(TAG, closed ? "Popup closed!" : "Could not close the popup");
    }

    private BlockStats stats() {
        if (stats == null) {
            stats = new BlockStats(getSharedPreferences(BlockStats.PREFS_NAME, MODE_PRIVATE));
        }
        return stats;
    }

    /**
     * Searches all windows of the rule's messaging app for a dismiss button and
     * clicks it. Uses getWindows() because at event time the "active window" may
     * not be the dialog.
     */
    private boolean findAndClickDismiss(FlashPopupRule rule) {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return false;
        for (AccessibilityWindowInfo w : windows) {
            if (w == null) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            CharSequence rp = root.getPackageName();
            boolean isMsg = rp != null && rule.messagingPackage.equals(rp.toString());
            boolean clicked = isMsg && clickByLabelRecursive(root, rule, 0);
            recycleNodes(root);
            if (clicked) return true;
        }
        return false;
    }

    /**
     * Walks the tree looking for a clickable element whose text/description
     * matches one of the rule's dismiss labels.
     */
    private boolean clickByLabelRecursive(AccessibilityNodeInfo node, FlashPopupRule rule, int depth) {
        if (node == null || depth > 20) return false;

        if (node.isClickable() || isButton(node.getClassName())) {
            String label = "";
            if (node.getText() != null) {
                label = node.getText().toString().toLowerCase(Locale.ROOT).trim();
            }
            if (label.isEmpty() && node.getContentDescription() != null) {
                label = node.getContentDescription().toString().toLowerCase(Locale.ROOT).trim();
            }
            if (rule.isDismissLabel(label)) {
                Log.i(TAG, "Dismiss button found: '" + label + "'");
                return performClick(node);
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (clickByLabelRecursive(node.getChild(i), rule, depth + 1)) return true;
        }
        return false;
    }

    private boolean isButton(CharSequence cls) {
        return cls != null && cls.toString().contains("Button");
    }

    /** Clicks the node, falling back to the clickable ancestor. */
    private boolean performClick(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            boolean ok = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            parent.recycle();
            return ok;
        }
        return false;
    }

    /** Recursively recycles nodes to free memory. */
    private void recycleNodes(AccessibilityNodeInfo node) {
        if (node == null) return;
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            recycleNodes(node.getChild(i));
        }
        try {
            node.recycle();
        } catch (Exception ignored) {
        }
    }

    // ==================== LEARNING MODE ====================

    /**
     * Logs everything about a new window without closing it. Runs while Learning
     * Mode is on, to (re)discover a device's popup identity (package/class).
     */
    private void learnDump(AccessibilityEvent event) {
        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "?";
        String cls = event.getClassName() != null ? event.getClassName().toString() : "?";
        String eventText = event.getText() != null ? event.getText().toString() : "";

        StringBuilder allText = new StringBuilder();
        StringBuilder buttons = new StringBuilder();
        // At event time the active window may not be the new dialog, so prefer
        // the window belonging to the event's package (same reasoning as
        // findAndClickDismiss).
        AccessibilityNodeInfo root = findRootForPackage(pkg);
        if (root == null) root = getRootInActiveWindow();
        if (root != null) {
            collectTree(root, 0, allText, buttons);
            recycleNodes(root);
        }

        Log.i(LEARN_TAG, "==================== NEW WINDOW ====================");
        Log.i(LEARN_TAG, "package = " + pkg);
        Log.i(LEARN_TAG, "class   = " + cls);
        Log.i(LEARN_TAG, "evText  = " + eventText);
        Log.i(LEARN_TAG, "text    = " + allText.toString().trim());
        Log.i(LEARN_TAG, "buttons = " + buttons.toString().trim());
        Log.i(LEARN_TAG, "====================================================");

        String peek = allText.length() > 0 ? allText.toString().trim() : eventText;
        if (peek.length() > 90) peek = peek.substring(0, 90);
        stats().recordEvent("LEARN", pkg + " | " + cls + " :: " + peek);
    }

    /** Returns the root of the first window owned by {@code pkg}, or null. */
    private AccessibilityNodeInfo findRootForPackage(String pkg) {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return null;
        for (AccessibilityWindowInfo w : windows) {
            if (w == null) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            CharSequence rp = root.getPackageName();
            if (rp != null && rp.toString().equals(pkg)) return root;
            recycleNodes(root);
        }
        return null;
    }

    private void collectTree(AccessibilityNodeInfo node, int depth,
                             StringBuilder allText, StringBuilder buttons) {
        if (node == null || depth > 20) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null && text.length() > 0) allText.append('[').append(text).append("] ");
        if (desc != null && desc.length() > 0) allText.append('(').append(desc).append(") ");
        if (node.isClickable()) {
            String label = text != null ? text.toString()
                : (desc != null ? desc.toString() : "");
            buttons.append('{').append(label).append(" <")
                   .append(node.getClassName()).append(">} ");
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            collectTree(node.getChild(i), depth + 1, allText, buttons);
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Service interrupted");
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
