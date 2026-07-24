package com.flashblocker;

import java.util.Arrays;
import java.util.List;

/**
 * Describes how to recognize and dismiss the Class 0 (Flash SMS) popup on a
 * given device family.
 *
 * Across devices the popup differs mainly in three data points: the messaging
 * package that draws it, the popup's Activity/dialog class name, and the label
 * of the button that discards it. So adding support for a new device is usually
 * just adding ONE entry to {@link #KNOWN} below — no new logic required.
 *
 * If some device needs custom matching logic, subclass this and override
 * {@link #matches}.
 *
 * To discover a new device's package/class, turn on Learning Mode from the app's
 * ⋮ menu and read the entry it writes to the history — see the README section
 * "Adaptar para outro aparelho".
 */
public class FlashPopupRule {

    /**
     * Known devices. Contributors: add new ones here (one line each).
     * Keep the tested/verified ones documented.
     */
    public static final List<FlashPopupRule> KNOWN = Arrays.asList(

        // Samsung Galaxy A73 / One UI (TESTED). The popup is the Activity
        //   com.samsung.android.messaging/...classzero.ClassZeroActivity
        // with an AlertDialog whose buttons are "Cancel" / "Save".
        new FlashPopupRule(
            "Samsung One UI",
            "com.samsung.android.messaging",
            "ClassZeroActivity",
            "class 0 message",
            new String[] { "cancel", "cancelar", "descartar", "dismiss", "discard" })

        // --- Add more devices below (remove the "//" and the leading comma) ---
        //
        // , new FlashPopupRule(
        //       "AOSP / Pixel (UNVERIFIED - placeholder)",
        //       "com.android.messaging",
        //       "ClassZeroActivity",
        //       "class 0",
        //       new String[] { "ok", "dismiss", "close" })
    );

    /** Returns the first known rule that matches this window, or null if none. */
    public static FlashPopupRule match(String pkg, String cls, String lowerText) {
        for (FlashPopupRule rule : KNOWN) {
            if (rule.matches(pkg, cls, lowerText)) return rule;
        }
        return null;
    }

    /** Human-readable name of the device family (for logs). */
    public final String label;
    /** Package that draws the Class 0 popup and holds the dismiss button. */
    public final String messagingPackage;
    /** Substring of the popup's Activity/dialog class name. */
    public final String classContains;
    /** Fallback: substring to look for in the event text (lowercase). May be null. */
    public final String textContains;
    /** Button labels (lowercase) that discard the popup WITHOUT saving. */
    public final String[] dismissLabels;

    public FlashPopupRule(String label, String messagingPackage, String classContains,
                          String textContains, String[] dismissLabels) {
        this.label = label;
        this.messagingPackage = messagingPackage;
        this.classContains = classContains;
        this.textContains = textContains;
        this.dismissLabels = dismissLabels;
    }

    /**
     * True if this window looks like this device's Class 0 popup. The window
     * must belong to the device's messaging package; within it, matches by
     * class name or, as a fallback (e.g. the OEM renamed the Activity), by a
     * marker in the event text. Never matches other packages — that would risk
     * dismissing legitimate dialogs.
     * Override this for devices that need custom matching logic.
     */
    public boolean matches(String pkg, String cls, String lowerText) {
        if (!messagingPackage.equals(pkg)) return false;
        if (classContains != null && cls.contains(classContains)) return true;
        return textContains != null && lowerText.contains(textContains);
    }

    /**
     * True if this (lowercase) button label is one of the labels that discard
     * the popup. Substring match, so "cancel" also covers "cancel message".
     */
    public boolean isDismissLabel(String lowerLabel) {
        if (lowerLabel == null || lowerLabel.isEmpty()) return false;
        for (String want : dismissLabels) {
            if (lowerLabel.contains(want)) return true;
        }
        return false;
    }
}
