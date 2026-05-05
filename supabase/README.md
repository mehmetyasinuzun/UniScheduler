# UniScheduler — Supabase Setup

## 1. Create a Supabase Project
1. Go to https://supabase.com → New Project
2. Choose a name (e.g. `unischeduler`), set a strong DB password, select a region close to you.

## 2. Run Migrations (in order)
Open **Supabase Dashboard → SQL Editor** and run:
1. `migrations/001_schema.sql` — creates all tables + indexes
2. `migrations/002_seed_admin.sql` — inserts default admin (admin / Admin123)

## 3. Get Your API Keys
Dashboard → Settings → API:
- **Project URL** → `SUPABASE_URL`
- **anon / public key** → `SUPABASE_ANON_KEY`

## 4. Add Keys to the Android Project
Open `UniScheduler/local.properties` and add:
```
SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

> `local.properties` is gitignored — never commit API keys.

## 5. Disable RLS (for student project)
Dashboard → Table Editor → each table → RLS → **Disable**.
Or run in SQL Editor:
```sql
ALTER TABLE users            DISABLE ROW LEVEL SECURITY;
ALTER TABLE departments      DISABLE ROW LEVEL SECURITY;
ALTER TABLE lecturers        DISABLE ROW LEVEL SECURITY;
ALTER TABLE courses          DISABLE ROW LEVEL SECURITY;
ALTER TABLE classrooms       DISABLE ROW LEVEL SECURITY;
ALTER TABLE schedule_entries DISABLE ROW LEVEL SECURITY;
```

## Table Summary
| Table | Purpose |
|---|---|
| `organizations` | Multi-tenant root (one row per institution) |
| `org_settings` | Organization-level configuration (time step, days) |
| `users` | Auth for both admin and lecturers (global-unique username) |
| `departments` | Department list (per org) |
| `lecturers` | Lecturer profiles (linked to users) |
| `courses` | Course catalogue (per org) |
| `classrooms` | Physical room list (per org) |
| `schedule_entries` | Course-Lecturer-Classroom-Day-TimeSlot assignments |

## Default Admin
| Field | Value |
|---|---|
| Username | `admin` |
| Password | `Admin123` |
| Organization | `Default University` (code: `default`) |
