-- ╔══════════════════════════════════════════════════════════════════╗
-- ║          UniScheduler — Complete Supabase Schema               ║
-- ║          Multi-tenant, free-time scheduling                     ║
-- ╚══════════════════════════════════════════════════════════════════╝
--
-- SINGLE SOURCE OF TRUTH. Run this ONE file in Supabase SQL Editor and
-- everything (tables + RLS + triggers + indexes + helper functions) is set
-- up correctly. The files in supabase/migrations/ are LEGACY upgrade
-- scripts for installations that pre-date this consolidated schema; for a
-- fresh install you DO NOT need to run them.
--
-- WARNING: this script DROPs every existing table. All data is wiped.
--
-- Requires Supabase Auth (profiles live in public.users, FK to auth.users).
-- After running this, create your first admin via Supabase Auth → Users
-- and INSERT a matching row into public.users with role='admin'.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Cleanup ───────────────────────────────────────────────────────────────────
DROP TABLE IF EXISTS schedule_entries CASCADE;
DROP TABLE IF EXISTS client_error_logs CASCADE;
DROP TABLE IF EXISTS lecturer_availability CASCADE;
DROP TABLE IF EXISTS offerings CASCADE;
DROP TABLE IF EXISTS lecturers CASCADE;
DROP TABLE IF EXISTS classrooms CASCADE;
DROP TABLE IF EXISTS courses CASCADE;
DROP TABLE IF EXISTS departments CASCADE;
DROP TABLE IF EXISTS org_settings CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS organizations CASCADE;

-- Wipe orphaned UniScheduler auth users from prior installs.
-- Without this, re-importing the same lecturers fails with
-- "User already registered" because Supabase Auth keeps email records
-- separately from public.users (different schema).
-- Active super-admins are preserved by the NOT IN clause below — but at
-- this point public.users is empty, so we delete every @unischeduler.app
-- email. If you want to keep the superadmin Auth user across resets,
-- comment this block out before running.
DELETE FROM auth.users
WHERE email LIKE '%@unischeduler.app';

-- ── Organizations ─────────────────────────────────────────────────────────────
CREATE TABLE organizations (
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    code TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- ── Org Settings ──────────────────────────────────────────────────────────────
CREATE TABLE org_settings (
    org_id           INT PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    time_step_minutes INT DEFAULT 10,
    active_days      TEXT[] DEFAULT '{"Monday","Tuesday","Wednesday","Thursday","Friday"}',
    day_start        TEXT DEFAULT '08:00',
    day_end          TEXT DEFAULT '18:00'
);

-- ── Users (auth) ──────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                   UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    org_id               INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    username             TEXT NOT NULL UNIQUE,
    role                 TEXT NOT NULL CHECK (role IN ('admin', 'lecturer')),
    must_change_password BOOLEAN DEFAULT false,
    created_at           TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_org     ON users(org_id);

-- ── Departments ───────────────────────────────────────────────────────────────
CREATE TABLE departments (
    id     SERIAL PRIMARY KEY,
    org_id INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name   TEXT NOT NULL,
    UNIQUE(org_id, name)
);

-- ── Lecturers ─────────────────────────────────────────────────────────────────
CREATE TABLE lecturers (
    id            SERIAL PRIMARY KEY,
    org_id        INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title         TEXT DEFAULT '',
    first_name    TEXT NOT NULL,
    last_name     TEXT NOT NULL,
    email         TEXT,
    department_id INT REFERENCES departments(id) ON DELETE SET NULL
);

CREATE INDEX idx_lecturers_org ON lecturers(org_id);

-- ── Courses ───────────────────────────────────────────────────────────────────
CREATE TABLE courses (
    id            SERIAL PRIMARY KEY,
    org_id        INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code          TEXT NOT NULL,
    name          TEXT NOT NULL,
    theory_hours  INT DEFAULT 0,
    lab_hours     INT DEFAULT 0,
    credits       INT DEFAULT 0,
    department_id INT REFERENCES departments(id) ON DELETE SET NULL,
    UNIQUE(org_id, code)
);

CREATE INDEX idx_courses_org ON courses(org_id);

-- ── Classrooms ────────────────────────────────────────────────────────────────
CREATE TABLE classrooms (
    id            SERIAL PRIMARY KEY,
    org_id        INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    room_code     TEXT NOT NULL,
    capacity      INT NOT NULL DEFAULT 30,
    type          TEXT DEFAULT 'theory' CHECK (type IN ('theory', 'lab')),
    department_id INT REFERENCES departments(id) ON DELETE SET NULL,
    UNIQUE(org_id, room_code)
);

-- ── Offerings (opened sections per term) ──────────────────────────────────────
CREATE TABLE offerings (
    id            SERIAL PRIMARY KEY,
    org_id        INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    course_id     INT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    lecturer_id   INT REFERENCES lecturers(id) ON DELETE SET NULL,
    academic_year TEXT NOT NULL,
    term          TEXT NOT NULL CHECK (term IN ('Fall', 'Spring', 'Summer')),
    class_year    INT NOT NULL CHECK (class_year BETWEEN 1 AND 4),
    section       TEXT NOT NULL DEFAULT 'A',
    capacity      INT DEFAULT 0,
    UNIQUE(org_id, course_id, academic_year, term, section)
);

CREATE INDEX idx_offerings_org      ON offerings(org_id);
CREATE INDEX idx_offerings_lecturer ON offerings(lecturer_id);
CREATE INDEX idx_offerings_course   ON offerings(course_id);

-- ── Schedule Entries ──────────────────────────────────────────────────────────
CREATE TABLE schedule_entries (
    id           SERIAL PRIMARY KEY,
    org_id       INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    offering_id  INT NOT NULL REFERENCES offerings(id) ON DELETE CASCADE,
    lecturer_id  INT NOT NULL REFERENCES lecturers(id) ON DELETE CASCADE,
    classroom_id INT NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
    day          TEXT NOT NULL CHECK (day IN ('Monday','Tuesday','Wednesday','Thursday','Friday')),
    start_time   TEXT NOT NULL,
    end_time     TEXT NOT NULL,
    CHECK (start_time < end_time)
);

CREATE INDEX idx_entries_org      ON schedule_entries(org_id);
CREATE INDEX idx_entries_lecturer ON schedule_entries(lecturer_id, day);
CREATE INDEX idx_entries_classroom ON schedule_entries(classroom_id, day);

-- ── Lecturer Availability ─────────────────────────────────────────────────────
CREATE TABLE lecturer_availability (
    id           SERIAL PRIMARY KEY,
    org_id       INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    lecturer_id  INT NOT NULL REFERENCES lecturers(id) ON DELETE CASCADE,
    day          TEXT NOT NULL CHECK (day IN ('Monday','Tuesday','Wednesday','Thursday','Friday')),
    start_time   TEXT NOT NULL,
    end_time     TEXT NOT NULL
);

CREATE INDEX idx_availability_lecturer ON lecturer_availability(lecturer_id, day);

-- ── Client Error Logs ───────────────────────────────────────────────────────
-- `source` distinguishes mobile crashes from super-admin panel errors so
-- triagers can filter. `org_id` is nullable because panel logs are super-
-- admin scope (no org context).
CREATE TABLE client_error_logs (
    id           BIGSERIAL PRIMARY KEY,
    org_id       INT REFERENCES organizations(id) ON DELETE SET NULL,
    user_id      UUID REFERENCES users(id) ON DELETE SET NULL,
    username     TEXT,
    role         TEXT,
    screen       TEXT NOT NULL,
    action       TEXT,
    message      TEXT NOT NULL,
    stack_trace  TEXT,
    app_version  TEXT,
    device_model TEXT,
    os_version   TEXT,
    source       TEXT CHECK (source IN ('mobile', 'panel', 'server')) DEFAULT 'mobile',
    created_at   TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_error_logs_org     ON client_error_logs(org_id);
CREATE INDEX idx_error_logs_created ON client_error_logs(created_at DESC);
CREATE INDEX idx_error_logs_source  ON client_error_logs(source, created_at DESC);

-- ── Disable RLS for development ───────────────────────────────────────────────
ALTER TABLE organizations         ENABLE ROW LEVEL SECURITY;
ALTER TABLE org_settings          ENABLE ROW LEVEL SECURITY;
ALTER TABLE users                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE departments           ENABLE ROW LEVEL SECURITY;
ALTER TABLE lecturers             ENABLE ROW LEVEL SECURITY;
ALTER TABLE courses               ENABLE ROW LEVEL SECURITY;
ALTER TABLE classrooms            ENABLE ROW LEVEL SECURITY;
ALTER TABLE offerings             ENABLE ROW LEVEL SECURITY;
ALTER TABLE schedule_entries      ENABLE ROW LEVEL SECURITY;
ALTER TABLE lecturer_availability ENABLE ROW LEVEL SECURITY;
ALTER TABLE client_error_logs     ENABLE ROW LEVEL SECURITY;

-- ── Enable Realtime ───────────────────────────────────────────────────────────
ALTER PUBLICATION supabase_realtime ADD TABLE schedule_entries;
ALTER PUBLICATION supabase_realtime ADD TABLE courses;
ALTER PUBLICATION supabase_realtime ADD TABLE lecturer_availability;

-- ── Helper functions for RLS ─────────────────────────────────────────────────
-- IMPORTANT: These use SECURITY DEFINER to bypass RLS on the users table,
-- breaking the infinite recursion that occurs when RLS policies call
-- functions that query the same RLS-protected table.

CREATE OR REPLACE FUNCTION public.current_org_id()
RETURNS INT
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT org_id FROM public.users WHERE id = auth.uid();
$$;

CREATE OR REPLACE FUNCTION public.current_user_role()
RETURNS TEXT
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT role FROM public.users WHERE id = auth.uid();
$$;

CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.users
        WHERE id = auth.uid() AND role = 'admin'
    );
$$;

CREATE OR REPLACE FUNCTION public.current_lecturer_id()
RETURNS INT
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT id FROM public.lecturers WHERE user_id = auth.uid() LIMIT 1;
$$;

-- ── RLS Policies ───────────────────────────────────────────────────────────-

-- organizations
CREATE POLICY organizations_select ON organizations
    FOR SELECT USING (id = public.current_org_id());

-- org_settings
CREATE POLICY org_settings_select ON org_settings
    FOR SELECT USING (org_id = public.current_org_id());
CREATE POLICY org_settings_insert ON org_settings
    FOR INSERT WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY org_settings_update ON org_settings
    FOR UPDATE USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());

-- users (profiles)
-- Reads: own row OR same-org rows. Multi-tenant safe; no recursion because
-- current_org_id() is SECURITY DEFINER and bypasses RLS on users.
CREATE POLICY users_select ON users
    FOR SELECT USING (
        id = auth.uid()
        OR org_id = public.current_org_id()
    );
CREATE POLICY users_insert ON users
    FOR INSERT WITH CHECK (id = auth.uid());
CREATE POLICY users_update_self ON users
    FOR UPDATE USING (id = auth.uid())
    WITH CHECK (id = auth.uid());
CREATE POLICY users_update_admin ON users
    FOR UPDATE USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY users_delete ON users
    FOR DELETE USING (public.is_admin() AND org_id = public.current_org_id());

-- departments
CREATE POLICY departments_select ON departments
    FOR SELECT USING (org_id = public.current_org_id());
CREATE POLICY departments_insert ON departments
    FOR INSERT WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY departments_update ON departments
    FOR UPDATE USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY departments_delete ON departments
    FOR DELETE USING (public.is_admin() AND org_id = public.current_org_id());

-- lecturers
CREATE POLICY lecturers_select ON lecturers
    FOR SELECT USING (org_id = public.current_org_id());
CREATE POLICY lecturers_insert ON lecturers
    FOR INSERT WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY lecturers_update ON lecturers
    FOR UPDATE USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY lecturers_delete ON lecturers
    FOR DELETE USING (public.is_admin() AND org_id = public.current_org_id());

-- courses
CREATE POLICY courses_select ON courses
    FOR SELECT USING (org_id = public.current_org_id());
CREATE POLICY courses_insert ON courses
    FOR INSERT WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY courses_update ON courses
    FOR UPDATE USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY courses_delete ON courses
    FOR DELETE USING (public.is_admin() AND org_id = public.current_org_id());

-- classrooms
CREATE POLICY classrooms_select ON classrooms
    FOR SELECT USING (org_id = public.current_org_id());
CREATE POLICY classrooms_insert ON classrooms
    FOR INSERT WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY classrooms_update ON classrooms
    FOR UPDATE USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY classrooms_delete ON classrooms
    FOR DELETE USING (public.is_admin() AND org_id = public.current_org_id());

-- offerings
CREATE POLICY offerings_select ON offerings
    FOR SELECT USING (org_id = public.current_org_id());
CREATE POLICY offerings_insert ON offerings
    FOR INSERT WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY offerings_update ON offerings
    FOR UPDATE USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY offerings_delete ON offerings
    FOR DELETE USING (public.is_admin() AND org_id = public.current_org_id());

-- schedule_entries
CREATE POLICY schedule_entries_select ON schedule_entries
    FOR SELECT USING (org_id = public.current_org_id());
CREATE POLICY schedule_entries_insert ON schedule_entries
    FOR INSERT WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY schedule_entries_update ON schedule_entries
    FOR UPDATE USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY schedule_entries_delete ON schedule_entries
    FOR DELETE USING (public.is_admin() AND org_id = public.current_org_id());

-- lecturer_availability
CREATE POLICY availability_select ON lecturer_availability
    FOR SELECT USING (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id = public.current_lecturer_id()
        )
    );
CREATE POLICY availability_insert ON lecturer_availability
    FOR INSERT WITH CHECK (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id = public.current_lecturer_id()
        )
    );
CREATE POLICY availability_update ON lecturer_availability
    FOR UPDATE USING (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id = public.current_lecturer_id()
        )
    )
    WITH CHECK (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id = public.current_lecturer_id()
        )
    );
CREATE POLICY availability_delete ON lecturer_availability
    FOR DELETE USING (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id = public.current_lecturer_id()
        )
    );

-- client_error_logs
-- INSERT: mobile clients write with their own org_id; panel logs (source='panel')
-- come through service_role (RLS bypass) so don't need a policy match.
CREATE POLICY error_logs_insert ON client_error_logs
    FOR INSERT WITH CHECK (
        org_id IS NULL OR org_id = public.current_org_id()
    );
-- SELECT: org admins see only mobile logs from their own org. Panel logs
-- (org_id IS NULL, source='panel') are super-admin only and read via service_role.
CREATE POLICY error_logs_select ON client_error_logs
    FOR SELECT USING (
        public.is_admin() AND source = 'mobile' AND org_id = public.current_org_id()
    );

-- ── Server-side overlap guard (race condition fix) ─────────────────────────-
-- SECURITY DEFINER so the cross-row scan runs with consistent permissions
-- regardless of caller. Self-update exemption (id <> NEW.id) lets admins edit
-- an existing entry without it conflicting with itself.
CREATE OR REPLACE FUNCTION public.prevent_schedule_overlap()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM schedule_entries
        WHERE org_id = NEW.org_id
          AND day = NEW.day
          AND id <> COALESCE(NEW.id, -1)
          AND (
                lecturer_id = NEW.lecturer_id
                OR classroom_id = NEW.classroom_id
              )
          AND NOT (NEW.end_time <= start_time OR NEW.start_time >= end_time)
    ) THEN
        RAISE EXCEPTION 'Schedule conflict for lecturer or classroom.';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_schedule_overlap ON schedule_entries;
CREATE TRIGGER trg_schedule_overlap
    BEFORE INSERT OR UPDATE ON schedule_entries
    FOR EACH ROW EXECUTE FUNCTION public.prevent_schedule_overlap();

-- ── Performance indexes added by 004 ───────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_users_org_role ON users(org_id, role);
CREATE INDEX IF NOT EXISTS idx_lecturers_user ON lecturers(user_id);

-- ╔══════════════════════════════════════════════════════════════════╗
-- ║  SEED: Example organization + admin user                        ║
-- ║  Password: Admin123 (must change on first login)                ║
-- ╚══════════════════════════════════════════════════════════════════╝

-- Seed removed for Auth-based setup. Create the first admin via Supabase Auth
-- and insert a profile row in public.users referencing auth.users.id.
