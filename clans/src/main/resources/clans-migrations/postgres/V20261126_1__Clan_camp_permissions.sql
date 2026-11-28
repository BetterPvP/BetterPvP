CREATE TABLE IF NOT EXISTS clan_camp_permissions
(
    clan    BIGINT      NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    scope   VARCHAR(32) NOT NULL,
    rank    VARCHAR(16) NOT NULL DEFAULT '',
    action  VARCHAR(64) NOT NULL DEFAULT '',
    allowed BOOLEAN     NOT NULL,
    PRIMARY KEY (clan, scope, rank, action)
);
