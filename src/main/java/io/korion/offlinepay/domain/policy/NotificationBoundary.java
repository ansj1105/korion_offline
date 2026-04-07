package io.korion.offlinepay.domain.policy;

/**
 * Defines the boundary between user-facing notifications and operational alerts.
 *
 * <h3>Audience</h3>
 * <ul>
 *   <li>{@link #AUDIENCE_USER} — shown in the user's in-app notification / settlement center only.
 *       Examples: settlement completed, payment received, collateral topup confirmed.</li>
 *   <li>{@link #AUDIENCE_OPS} — routed to operational monitoring only (Telegram, PagerDuty, etc.).
 *       Examples: dead letter, circuit open, reconciliation case opened.</li>
 *   <li>{@link #AUDIENCE_BOTH} — appears in both user notification feed and ops alerts.
 *       Examples: compensation completed (payment reversed — user must know, ops must track).</li>
 * </ul>
 *
 * <h3>Log Source (Flow)</h3>
 * <ul>
 *   <li>{@link #SOURCE_SETTLEMENT_FLOW} — settlement saga / batch processing</li>
 *   <li>{@link #SOURCE_COLLATERAL_FLOW} — collateral topup / release operations</li>
 *   <li>{@link #SOURCE_TRUST_FLOW} — trust center / security state changes</li>
 *   <li>{@link #SOURCE_RECONCILIATION_FLOW} — manual reconciliation interventions</li>
 *   <li>{@link #SOURCE_SYSTEM} — infrastructure-level events (circuit breakers, health)</li>
 * </ul>
 */
public final class NotificationBoundary {

    private NotificationBoundary() {}

    // --- Audience constants ---
    public static final String AUDIENCE_USER = "USER";
    public static final String AUDIENCE_OPS = "OPS";
    public static final String AUDIENCE_BOTH = "BOTH";

    // --- Log source constants ---
    public static final String SOURCE_SETTLEMENT_FLOW = "SETTLEMENT_FLOW";
    public static final String SOURCE_COLLATERAL_FLOW = "COLLATERAL_FLOW";
    public static final String SOURCE_TRUST_FLOW = "TRUST_FLOW";
    public static final String SOURCE_RECONCILIATION_FLOW = "RECONCILIATION_FLOW";
    public static final String SOURCE_SYSTEM = "SYSTEM";
}
