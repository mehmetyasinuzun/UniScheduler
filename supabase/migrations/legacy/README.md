# Legacy Migration Files — DO NOT RUN

**These files are archived for historical reference only.**

For a fresh Supabase install, **only run** [`../../schema.sql`](../../schema.sql).

The files in this folder were the incremental migration scripts used during
early development. They contain partially-applied policies, deprecated
columns (e.g. `password_hash` from before Supabase Auth integration), and
`DISABLE ROW LEVEL SECURITY` statements that would create a security hole if
run on a production database.

## Why we kept them

Some teams may have a database that was bootstrapped from these scripts
*before* `schema.sql` was introduced as the single source of truth. If your
DB still has a `password_hash` column or RLS disabled, see
[`../README.md`](../README.md) for a one-time upgrade procedure that brings
your schema up to current.

## For new installs

```sql
-- Run this in Supabase SQL Editor:
-- supabase/schema.sql

-- Do NOT run anything in this legacy/ folder.
```
