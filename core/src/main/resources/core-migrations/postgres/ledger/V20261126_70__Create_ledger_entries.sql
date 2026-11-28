-- Every coin movement a server made while it was its own ledger.
CREATE TABLE IF NOT EXISTS ledger_entries
(
    id           BIGSERIAL PRIMARY KEY,
    from_account VARCHAR(64) NOT NULL,
    to_account   VARCHAR(64) NOT NULL,
    amount       BIGINT      NOT NULL,
    reason       VARCHAR(64) NOT NULL,
    reference    VARCHAR(128),
    posted_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ledger_entries_from ON ledger_entries (from_account);
CREATE INDEX IF NOT EXISTS ledger_entries_to ON ledger_entries (to_account);
