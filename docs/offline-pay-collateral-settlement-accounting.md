# Offline Pay Collateral And Settlement Accounting

## Invariant

- Foxya wallet balance and offline collateral are separate before settlement.
- Collateral topup moves Foxya wallet available balance into offline collateral.
- Collateral release moves releasable offline collateral back to Foxya wallet available balance.
- Online payments deduct the server-side online collateral and the local offline snapshot.
- Offline payments deduct only the local offline collateral snapshot first; server-side online collateral is deducted when the queued settlement syncs after connectivity returns.
- Settlement reflects offline transaction results to wallet/history, and must be idempotent by settlement/proof reference.
- Sender wallet must not be debited again during settlement because collateral topup already moved value out of available wallet balance.
- Receiver wallet/history is reflected only after settlement finalization. Manual or automatic receiver settlement can then convert received available balance into offline collateral through the existing collateral topup queue.

## Balance Model

- `onlineCollateral`: server-side current collateral amount after topup, release, and finalized payment deductions.
- `offlineCollateralSnapshot`: local snapshot synchronized from `onlineCollateral` while online and used as the spendable offline amount while offline.
- `offlineTransactionPending`: completed offline transactions that have not been synced to server collateral yet.
- `collateralAvailableForPay`: current spendable amount. Online uses server collateral; offline uses the local snapshot after local transaction deductions.
- `offlineTransactionSettled`: transactions finalized through settlement.
- `foxyaWalletBalance`: independent from offline transaction activity until settlement finalization.

## Service Ownership

- `korion_offline` stores offline transactions and validates proof/device/signature/idempotency. Successful sender settlement deducts `collateral_locks.remaining_amount` so the next online snapshot matches the canonical online collateral.
- `coin_manage` owns collateral topup/release and settlement journal records. Successful sender settlement debits `user:*:offline_pay_pending`; it must not debit sender wallet available again.
- `fox_coin` records wallet history from finalized settlement events. Sender settlement is history-only; receiver settlement credits receiver wallet/history.
- `fox_coin_frontend` displays online collateral, offline snapshot, pending sync amount, and payment-available projection separately. Receiver manual/auto settlement enqueues received-amount collateral topup and syncs it online.

## State Meaning

- `LOCAL_COMPLETED` and `SERVER_ACCEPTED`: transaction occurred, settlement not reflected to wallet yet.
- `SETTLEMENT_READY`: server validation completed and the transaction can be finalized.
- `SETTLED`: settlement finalized and wallet/history reflection may be refreshed.
- `RETRYABLE_FAILED` / `DEAD_LETTERED`: settlement finalization failed or exhausted retry policy.
