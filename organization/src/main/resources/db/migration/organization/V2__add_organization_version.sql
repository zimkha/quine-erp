-- Optimistic locking for the Organization aggregate.
-- Every successful save of the aggregate increments this column, and a save
-- based on a stale version is rejected.
ALTER TABLE organization.organizations
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
