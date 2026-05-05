# UniScheduler

A multi-tenant university scheduling system with an Android mobile app and a Node.js admin panel, backed by Supabase (PostgreSQL + GoTrue Auth).

---

## Architecture

| Layer           | Tech                                    |
|:---------------|:----------------------------------------|
| **Mobile**     | Kotlin · MVVM · Supabase SDK (GoTrue + Postgrest + Realtime) |
| **Admin Panel**| Node.js · Express · Supabase JS (service_role) |
| **Database**   | PostgreSQL (Supabase) · Row Level Security (RLS) |
| **Auth**       | Supabase GoTrue (JWT-based) — passwords managed by GoTrue |

### Security Model

- **Mobile app** authenticates via Supabase GoTrue (`signInWith(Email)`) and receives a JWT token
- All Postgrest queries carry the JWT → **RLS is enforced** at the database level
- `auth.uid()` based policies isolate data by organization
- **Admin panel** is protected by session-based token auth (env-configured credentials)
- Admin panel uses `service_role` key for full DB + Auth Admin API access
- Schedule conflict detection runs both client-side (UX) and server-side (DB trigger `trg_schedule_overlap`)

---

## Quick Start

### 1. Supabase Setup

1. Create a project at [supabase.com](https://supabase.com)
2. Run `supabase/migrations/001_schema.sql` in the SQL Editor
3. Run `supabase/migrations/002_seed_admin.sql` for sample data
4. **Disable email confirmation**: Dashboard → Authentication → Settings → uncheck "Enable email confirmations"
5. Note your **Project URL**, **anon key**, and **service_role key**

### 2. Create First Admin User

Use the Admin Panel or Supabase Dashboard:

**Option A — Admin Panel:**
```bash
cd admin-panel
cp .env.example .env
# Edit .env with Supabase credentials + admin panel login
npm install && npm start
```
Open `http://localhost:3000`, log in, create an admin user.

**Option B — Supabase Dashboard:**
1. Go to Authentication → Users → Create User
   - Email: `admin@unischeduler.app`, Password: `Admin123`
2. Copy the UUID, then run in SQL Editor:
   ```sql
   INSERT INTO users (id, org_id, username, role, must_change_password)
   VALUES ('<uuid>', 1, 'admin', 'admin', FALSE);
   ```

### 3. Android App

1. Open `UniScheduler` in Android Studio
2. Create/edit `local.properties`:
   ```properties
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_ANON_KEY=your-anon-key-here
   ```
3. Sync Gradle and run (min Android 8.0 / API 26)

### 4. Admin Panel

```bash
cd admin-panel
cp .env.example .env
# Edit .env:
#   SUPABASE_URL, SUPABASE_SERVICE_KEY
#   ADMIN_USERNAME, ADMIN_PASSWORD
npm install && npm start
```

---

## Features

### Mobile App (Admin Role)
- **Data Management**: Add/edit/delete lecturers, courses, departments, offerings
- **Classrooms**: CRUD with CSV/Excel import/export
- **Schedule Assignment**: Searchable dropdowns, conflict detection, time pickers
- **Calendar View**: Weekly schedule grid
- **Department Settings**: Accessible via Data → Department Settings button
- **Import/Export**: CSV and Excel (Apache POI) for bulk operations
- **Offline Banner**: Real-time connectivity monitoring

### Mobile App (Lecturer Role)
- **My Schedule**: Personal weekly timetable
- **Availability**: Set available time slots
- **Calendar**: Read-only schedule view

### Admin Panel (Web)
- **Session-based auth** with configurable credentials
- **Organization management**: Multi-tenant support
- **User management**: Create admins via Supabase Auth Admin API
- **Data viewer**: Departments, lecturers, courses, classrooms, schedule
- **Error logs**: Client error monitoring

---

## Navigation (Bottom Bar)

| Tab | Target |
|-----|--------|
| Home | Dashboard |
| Calendar | Weekly grid |
| Data | Lecturers, Courses, Offerings + Settings access |
| Classrooms | Classroom CRUD + import/export |
| Assign | Schedule assignment (searchable dropdowns) |

Settings (Department management) is accessible via the **"Department Settings"** button in the Data tab.

---

## Error Handling

All error messages are centralized in `ErrorMessages.kt` and displayed in English:
- Network errors → "No internet connection"
- Auth errors → "Invalid username or password"
- DB constraints → "This record already exists"
- RLS violations → "You don't have permission"
- Session expiry → "Your session has expired"

---

## Project Structure

```
UniScheduler/
├── app/src/main/java/com/unischeduler/
│   ├── data/
│   │   ├── model/          # Data classes (Kotlin Serialization)
│   │   ├── remote/         # SupabaseClient (GoTrue + Postgrest + Realtime)
│   │   └── repository/     # Auth, Lecturer, Course, Offering, Schedule repos
│   ├── ui/
│   │   ├── auth/           # Login, PasswordChange (GoTrue-based)
│   │   ├── admin/          # Home, Calendar, Data, Settings, Classrooms, Assignment
│   │   └── lecturer/       # Home, Availability, Calendar
│   └── util/               # ErrorMessages, NetworkMonitor, SessionManager, etc.
├── admin-panel/
│   ├── server.js           # Express server with auth middleware
│   └── public/index.html   # SPA with login overlay
└── supabase/
    ├── schema.sql          # Full schema + RLS policies + triggers
    └── migrations/         # Incremental migration files
```
