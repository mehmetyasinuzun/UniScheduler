# UniScheduler — Supabase Setup

> **Single-file install.** Run [`schema.sql`](schema.sql) once and the entire
> backend (tables, indexes, RLS policies, helper functions, realtime, overlap
> trigger) is configured correctly. The files in [`migrations/legacy/`](migrations/legacy/)
> are archived — **do not run them** on a fresh install.

---

## 1. Create the Supabase Project

1. Sign in to [supabase.com](https://supabase.com) → **New Project**
2. Set a project name (e.g. `unischeduler-prod`)
3. Pick a strong database password and store it in your password manager
4. Choose the region closest to your users (e.g. `eu-central-1`)
5. Wait ~2 minutes for the project to provision

## 2. Run the Schema

1. Dashboard → **SQL Editor** → **New query**
2. Open `schema.sql` from this repository, copy-paste the entire contents
3. Click **Run**

This creates:
- 11 tables (organizations, org_settings, users, departments, lecturers,
  courses, classrooms, offerings, schedule_entries, lecturer_availability,
  client_error_logs)
- All indexes for org-scoped queries
- 4 SECURITY DEFINER helper functions (`current_org_id`, `current_user_role`,
  `is_admin`, `current_lecturer_id`) used by RLS
- Row-Level Security policies on every table — multi-tenant safe
- Realtime publication for schedule_entries / courses / lecturer_availability
- A trigger that prevents schedule overlap at the database level (race-condition
  proof, even when two admins click "Assign" at the exact same moment)

> ⚠ The script begins with `DROP TABLE IF EXISTS` and `DELETE FROM auth.users`
> for `*@unischeduler.app` rows. **All current data is wiped.** Only run on a
> fresh project, or take a database backup first.

## 3. Create the First Organization

After `schema.sql` finishes, you have an empty database. Insert at least one
organization (the multi-tenant root) before creating any users:

```sql
INSERT INTO organizations (name, code) VALUES
    ('Default University', 'default')
RETURNING id;  -- note the returned id; use it for all subsequent users
```

## 4. Create the First Admin

UniScheduler uses **Supabase Auth** (email + password) for authentication.
The mobile app converts usernames to synthetic emails (`username@unischeduler.app`)
internally — **you do not need a real email**. Recommended steps:

### 4a. Create the Auth user

Dashboard → **Authentication → Users → Add user**
- Email: `admin@unischeduler.app`
- Password: a strong password (you will share this with the human admin)
- **Auto-confirm user**: ✅ checked (so they can sign in immediately)

Copy the user's UUID from the resulting row.

### 4b. Create the matching profile row

```sql
INSERT INTO public.users (id, org_id, username, role, must_change_password)
VALUES (
    '<auth-user-uuid-from-step-4a>',
    1,                  -- org id from step 3
    'admin',            -- the username they will type in the mobile app
    'admin',
    true                -- forces password change on first login
);
```

You can now sign in to the mobile app with username `admin` and the password
you set in step 4a.

## 5. Get Your API Keys

Dashboard → **Settings → API**:
- **Project URL** → goes into `local.properties` as `SUPABASE_URL`
- **anon / public key** → goes into `local.properties` as `SUPABASE_ANON_KEY`

> The anon key is **safe** to embed in the mobile APK — RLS protects every
> table and the helper functions enforce that each user only sees their own
> organization. Never embed the **service_role** key in the app; that key is
> only for the super-admin panel.

## 6. Add Keys to the Mobile App

Open `UniScheduler/local.properties` (gitignored — never commit) and add:

```
SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Sync the Gradle project. Build → install → log in.

---

## Table Summary

| Table | Purpose |
|---|---|
| `organizations` | Multi-tenant root (one row per institution) |
| `org_settings` | Per-org configuration (time step, day start/end, active days) |
| `users` | Auth profile (FK to `auth.users`) — role + org membership |
| `departments` | Department list (scoped to org) |
| `lecturers` | Lecturer profiles (FK to `users` + `departments`) |
| `courses` | Course catalogue (scoped to org) |
| `classrooms` | Physical / lab rooms (scoped to org) |
| `offerings` | Opened sections per academic year + term |
| `schedule_entries` | Final timetable: offering × lecturer × classroom × day × time |
| `lecturer_availability` | Lecturer's blocked / preferred hours |
| `client_error_logs` | Mobile + panel error reports for triage |

## RLS Quick Reference

- **Everyone** sees only their own organization (`current_org_id()`).
- **Admins** can write to every table within their org.
- **Lecturers** can only write to their own `lecturer_availability` rows.
- The **service_role** key (used only by the super-admin panel) bypasses
  every policy — guard it like a database root password.

---

## Upgrading From an Older Schema

If your database was bootstrapped from `migrations/legacy/001_schema.sql`
and you want to bring it up to current:

1. **Take a backup.** Dashboard → Database → Backups.
2. Export your data (see SQL Editor → Export buttons).
3. Run `schema.sql` (it drops every table — yes, that is intentional).
4. Re-import data from your export.

There is no in-place migration path because the legacy scripts predate
Supabase Auth integration and `password_hash` was removed.
