# Offline Pay Collateral And Settlement Accounting

## Invariant

- Foxya wallet balance and offline collateral are separate before settlement.
- Collateral principal changes only through collateral topup and collateral release.
- Offline transactions do not directly reduce collateral principal.
- Settlement reflects offline transaction results to wallet/history, and must be idempotent by settlement/proof reference.
- Sender wallet must not be debited again during settlement because collateral topup already moved value out of available wallet balance.
- Receiver wallet/history is reflected only after settlement finalization.

## Balance Model

- `collateralTotal`: collateral principal funded by topup and reduced by explicit release only.
- `offlineTransactionPending`: completed offline transactions that are not settled yet.
- `collateralAvailableForPay`: derived projection, `collateralTotal - unsettled outgoing amount`.
- `offlineTransactionSettled`: transactions finalized through settlement.
- `foxyaWalletBalance`: independent from offline transaction activity until settlement finalization.

## Service Ownership

- `korion_offline` stores offline transactions and validates proof/device/signature/idempotency. Successful settlement must not mutate `collateral_locks.remaining_amount`.
- `coin_manage` owns collateral topup/release and settlement journal records. Settlement validates offline collateral coverage but does not debit `user:*:offline_pay_pending` for settled offline transactions.
- `fox_coin` records wallet history from finalized settlement events. Sender settlement is history-only; receiver settlement credits receiver wallet/history.
- `fox_coin_frontend` displays collateral principal, pending settlement amount, and payment-available projection separately.

## State Meaning

- `LOCAL_COMPLETED` and `SERVER_ACCEPTED`: transaction occurred, settlement not reflected to wallet yet.
- `SETTLEMENT_READY`: server validation completed and the transaction can be finalized.
- `SETTLED`: settlement finalized and wallet/history reflection may be refreshed.
- `RETRYABLE_FAILED` / `DEAD_LETTERED`: settlement finalization failed or exhausted retry policy.

