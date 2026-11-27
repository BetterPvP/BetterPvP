CREATE TABLE IF NOT EXISTS site_records
(
    site       VARCHAR(64) NOT NULL,
    owner      BIGINT      NOT NULL DEFAULT 0,
    data       BYTEA       NOT NULL,
    version    BIGINT      NOT NULL,
    updated_at BIGINT      NOT NULL,
    PRIMARY KEY (site, owner)
);
