package com.maduka.rentmanager.ui.common;

import com.maduka.rentmanager.util.StatusPresentation;

/** A computed, real-data-derived notification row (overdue/due-soon/due-today/payment-status),
 * not a persisted record - Admin and Tenant Notifications screens compute these live from
 * TenantRepository/PaymentRepository each time they render, per this session's scope decision
 * to skip a full push/notification-store pipeline. */
public class NotifItem {
    public enum Category { OVERDUE, DUE_TODAY, DUE_SOON, CONFIRMED }

    public final Category category;
    public final String title;
    public final String message;
    public final String timeLabel;
    public final StatusPresentation.Tone tone;
    public final long sortKey;

    public NotifItem(Category category, String title, String message, String timeLabel,
                      StatusPresentation.Tone tone, long sortKey) {
        this.category = category;
        this.title = title;
        this.message = message;
        this.timeLabel = timeLabel;
        this.tone = tone;
        this.sortKey = sortKey;
    }
}
