CREATE SCHEMA IF NOT EXISTS organization;

CREATE TABLE organization.organizations
(
    id                    UUID         NOT NULL,
    tenant_id             UUID         NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    legal_name            VARCHAR(160) NOT NULL,
    normalized_legal_name VARCHAR(160) NOT NULL,
    currency              VARCHAR(3) NOT NULL,
    status                VARCHAR(30)  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_organizations
        PRIMARY KEY (id),

    CONSTRAINT uk_organizations_tenant_id
        UNIQUE (tenant_id),

    CONSTRAINT uk_organizations_normalized_legal_name
        UNIQUE (normalized_legal_name),

    CONSTRAINT ck_organizations_name_not_blank
        CHECK (length(trim(name)) >= 2),

    CONSTRAINT ck_organizations_legal_name_not_blank
        CHECK (length(trim(legal_name)) >= 2),

    CONSTRAINT ck_organizations_normalized_legal_name_not_blank
        CHECK (length(trim(normalized_legal_name)) >= 2),

    CONSTRAINT ck_organizations_currency
        CHECK (currency IN ('XOF', 'EUR', 'USD')),

    CONSTRAINT ck_organizations_status
        CHECK (
            status IN (
                       'PENDING_ACTIVATION',
                       'ACTIVE',
                       'SUSPENDED',
                       'CLOSED'
                )
            )
);

CREATE TABLE organization.stores
(
    id              UUID         NOT NULL,
    organization_id UUID         NOT NULL,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    headquarters    BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_stores
        PRIMARY KEY (id),

    CONSTRAINT fk_stores_organization
        FOREIGN KEY (organization_id)
            REFERENCES organization.organizations (id)
            ON DELETE RESTRICT,

    CONSTRAINT uk_stores_organization_code
        UNIQUE (organization_id, code),

    CONSTRAINT ck_stores_code_not_blank
        CHECK (length(trim(code)) >= 2),

    CONSTRAINT ck_stores_name_not_blank
        CHECK (length(trim(name)) >= 2),

    CONSTRAINT ck_stores_headquarters_active
        CHECK (NOT headquarters OR active)
);

CREATE UNIQUE INDEX uk_stores_one_headquarters_per_organization
    ON organization.stores (organization_id)
    WHERE headquarters = TRUE;

CREATE INDEX idx_organizations_status
    ON organization.organizations (status);

CREATE INDEX idx_organizations_created_at
    ON organization.organizations (created_at);

CREATE INDEX idx_stores_organization_active
    ON organization.stores (organization_id, active);

CREATE INDEX idx_stores_organization_created_at
    ON organization.stores (organization_id, created_at);