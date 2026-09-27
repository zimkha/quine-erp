-- Every store carries its organization's tenant, and the database guarantees
-- that the two match, even for writes that bypass the domain.
-- One migration in one transaction: no deployed database has V1/V2 applied
-- yet (Architect decision 7).

-- 1. Target of the composite foreign key below.
ALTER TABLE organization.organizations
  ADD CONSTRAINT uk_organizations_id_tenant_id
    UNIQUE (id, tenant_id);

-- 2. Add the column, backfill it from the owning organization, then require it.
ALTER TABLE organization.stores
  ADD COLUMN tenant_id UUID;

UPDATE organization.stores AS s
SET tenant_id = o.tenant_id
FROM organization.organizations AS o
WHERE o.id = s.organization_id;

ALTER TABLE organization.stores
  ALTER COLUMN tenant_id SET NOT NULL;

-- 3. A store's (organization, tenant) pair must match an organization row.
--    It replaces the single-column foreign key and keeps ON DELETE RESTRICT.
ALTER TABLE organization.stores
  ADD CONSTRAINT fk_stores_organization_tenant
    FOREIGN KEY (organization_id, tenant_id)
      REFERENCES organization.organizations (id, tenant_id)
      ON DELETE RESTRICT;

ALTER TABLE organization.stores
  DROP CONSTRAINT fk_stores_organization;

-- 4. Tenant-first lookups of stores.
CREATE INDEX idx_stores_tenant_id_id
  ON organization.stores (tenant_id, id);
