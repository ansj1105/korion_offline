package io.korion.offlinepay.domain.policy;

/**
 * Defines the minimum trust requirements a device must satisfy for offline payment settlement.
 * <p>
 * These constants are non-blocking in the current policy — they are recorded informally in the
 * settlement result JSON for observability and future enforcement.
 */
public final class DeviceTrustContract {

    private DeviceTrustContract() {}

    /** Minimum attestation verdict required for the trust contract to be considered met. */
    public static final String MINIMUM_ATTESTATION_VERDICT = "HARDWARE_BACKED_VERIFIED";

    /** Server-verified trust level granted when attestation meets MINIMUM_ATTESTATION_VERDICT. */
    public static final String SERVER_VERIFIED = "SERVER_VERIFIED";

    public static final String MIRROR_VERIFIED = "MIRROR_VERIFIED";
    public static final String LOCAL_ONLY = "LOCAL_ONLY";
}
