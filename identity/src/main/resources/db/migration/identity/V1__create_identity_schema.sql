CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.users
(
    id            UUID         NOT NULL,
    tenant_id     UUID         NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    owner         BOOLEAN      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_users
        PRIMARY KEY (id),

    -- The domain stores e-mails trimmed and lower-cased.
    CONSTRAINT ck_users_email_normalized
        CHECK (email = lower(trim(email)) AND length(email) >= 3),

    CONSTRAINT ck_users_password_hash_not_blank
        CHECK (length(trim(password_hash)) > 0)
);

-- E-mails are unique across the whole platform, whatever their case.
CREATE UNIQUE INDEX uk_users_email
    ON identity.users (lower(email));

-- A tenant has exactly one owner at first (PO decision 3).
CREATE UNIQUE INDEX uk_users_one_owner_per_tenant
    ON identity.users (tenant_id)
    WHERE owner;

CREATE INDEX ix_users_tenant_id
    ON identity.users (tenant_id);
