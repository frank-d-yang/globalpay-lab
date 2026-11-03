CREATE UNIQUE INDEX uk_ledger_account_owner_currency_type
    ON ledger_account (
                       owner_type,
                       owner_id,
                       currency,
                       account_type
        );