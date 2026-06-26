-- V26 reverses an unsafe V25 aggregate data correction.
-- Do not re-apply the V25 restoration during rollback; doing so can overstate
-- active collateral totals. Restore a database backup if V26 itself must be
-- undone for audit reasons.
SELECT 1;
