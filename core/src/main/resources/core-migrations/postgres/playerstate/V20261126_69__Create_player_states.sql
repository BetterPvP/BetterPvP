CREATE TABLE IF NOT EXISTS player_states
(
    client   UUID   NOT NULL PRIMARY KEY,
    version  BIGINT NOT NULL,
    saved_at BIGINT NOT NULL,
    state    JSONB  NOT NULL
);
