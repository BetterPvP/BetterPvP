-- Every balance. Player accounts never go below zero, the others may, so all balances add up to zero.
CREATE TABLE IF NOT EXISTS ledger_account
(
    id       TEXT PRIMARY KEY,
    balance  BIGINT NOT NULL DEFAULT 0,
    sequence BIGINT NOT NULL DEFAULT 0
);

-- Entries that landed together. A reference lands at most once.
CREATE TABLE IF NOT EXISTS ledger_posting
(
    id          BIGSERIAL PRIMARY KEY,
    reference   TEXT UNIQUE,
    fingerprint TEXT        NOT NULL,
    posted_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ledger_entry
(
    id           BIGSERIAL PRIMARY KEY,
    posting_id   BIGINT NOT NULL REFERENCES ledger_posting (id),
    from_account TEXT   NOT NULL,
    to_account   TEXT   NOT NULL,
    amount       BIGINT NOT NULL CHECK (amount > 0),
    reason       TEXT   NOT NULL
);

CREATE INDEX IF NOT EXISTS ledger_entry_from ON ledger_entry (from_account);
CREATE INDEX IF NOT EXISTS ledger_entry_to ON ledger_entry (to_account);
