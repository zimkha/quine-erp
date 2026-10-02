# Database migrations on large tables

Applies once a deployed database holds large tables. While none exists (as for `organization` V3,
confirmed by the product owner on 2026-09-27), one migration in one transaction is fine.

Add a column, backfill it and constrain it in stages, one migration or deployment per stage:

1. **Add the column as nullable.** Cheap, no table rewrite.
2. **Backfill in batches**, so no single transaction holds locks on the whole table.
3. **Add the foreign key as `NOT VALID`, then `VALIDATE CONSTRAINT`.** Validation doesn't block writes.
4. **Set `NOT NULL`**, backed by a validated `CHECK (col IS NOT NULL)` so Postgres doesn't rescan the table.

One Flyway path and one Postgres schema per module (see `CLAUDE.md`, Database).

Source: `docs/tickets/tenant-scoping-T1-T4.md` ("Rules to add to T6").
