package io.korion.offlinepay.domain.policy;

/**
 * Defines the policy constants for offline payment settlement flows.
 *
 * <h3>Trigger Modes</h3>
 * <ul>
 *   <li>{@link #TRIGGER_MODE_AUTO} — submitted automatically by the device on a scheduled cycle
 *       (see foxya_coin_service {@code settlementAutoEnabled} / {@code settlementCycleMinutes}).</li>
 *   <li>{@link #TRIGGER_MODE_MANUAL} — initiated explicitly by the user via the settlement action.</li>
 * </ul>
 *
 * <h3>Settlement Lifecycle</h3>
 * <ol>
 *   <li>Batch submitted (CREATED → ACCEPTED)</li>
 *   <li>Each proof validated → {@code SettlementRequest} persisted (PENDING)</li>
 *   <li>Policy evaluation: SETTLED | REJECTED | EXPIRED | CONFLICT</li>
 *   <li>If SETTLED: saga starts — LEDGER_SYNC → HISTORY_SYNC → RECEIVER_HISTORY_SYNC</li>
 *   <li>If saga fails: COMPENSATION_REQUIRED → COMPENSATING → COMPENSATED</li>
 *   <li>If saga dead-lettered: reconciliation case OPEN</li>
 * </ol>
 *
 * <h3>Reconciliation Trigger Conditions</h3>
 * <ul>
 *   <li>Saga enters DEAD_LETTERED state (max retry exceeded)</li>
 *   <li>Settlement status is CONFLICT and no saga compensation was triggered</li>
 *   <li>Manual finalization via {@code /settlements/{id}/finalize} needed after external fix</li>
 * </ul>
 */
public final class SettlementPolicyConstants {

    private SettlementPolicyConstants() {}

    // --- Trigger modes ---
    public static final String TRIGGER_MODE_AUTO = "AUTO";
    public static final String TRIGGER_MODE_MANUAL = "MANUAL";

    // --- Ledger execution modes (from proof payload) ---
    public static final String LEDGER_MODE_INTERNAL_ONLY = "INTERNAL_LEDGER_ONLY";

    // --- Reconciliation condition identifiers ---
    public static final String RECONCILIATION_DEAD_LETTER = "DEAD_LETTER";
    public static final String RECONCILIATION_CONFLICT = "CONFLICT";
    public static final String RECONCILIATION_POLICY_REJECTED = "POLICY_REJECTED";

    // --- Snapshot staleness hint (ms) exposed in CurrentSnapshot.staleAfterMs ---
    public static final long COLLATERAL_SNAPSHOT_STALE_AFTER_MS = 60_000L;
    public static final long TRUST_CENTER_SNAPSHOT_STALE_AFTER_MS = 300_000L;
}
