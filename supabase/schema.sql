-- ╔══════════════════════════════════════════════════════════════════╗
-- ║          UniScheduler — Complete Supabase Schema               ║
-- ║          Multi-tenant, free-time scheduling                     ║
-- ╚══════════════════════════════════════════════════════════════════╝

-- Run this in Supabase SQL Editor to reset & create all tables.
-- WARNING: This drops all existing data!

-- Requires Supabase Auth. Profiles are stored in public.users and reference auth.users.

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
    academic_year TEXT NOT NULL,
    term          TEXT NOT NULL CHECK (term IN ('Fall', 'Spring', 'Summer')),
    class_year    INT NOT NULL CHECK (class_year BETWEEN 1 AND 4),
    section       TEXT NOT NULL DEFAULT 'A',
    capacity      INT DEFAULT 0,
    UNIQUE(org_id, course_id, academic_year, term, section)
);

CREATE INDEX idx_offerings_org ON offerings(org_id);

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
    created_at   TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_error_logs_org     ON client_error_logs(org_id);
CREATE INDEX idx_error_logs_created ON client_error_logs(created_at DESC);

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
CREATE OR REPLACE FUNCTION public.current_org_id()
RETURNS INT
LANGUAGE SQL
STABLE
AS $$
    SELECT org_id FROM public.users WHERE id = auth.uid();
$$;

CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.users
        WHERE id = auth.uid() AND role = 'admin'
    );
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
CREATE POLICY users_select ON users
    FOR SELECT USING (
        id = auth.uid() OR (public.is_admin() AND org_id = public.current_org_id())
    );
CREATE POLICY users_update_self ON users
    FOR UPDATE USING (id = auth.uid())
    WITH CHECK (id = auth.uid());

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
            public.is_admin() OR lecturer_id IN (
                SELECT id FROM lecturers WHERE user_id = auth.uid()
            )
        )
    );
CREATE POLICY availability_insert ON lecturer_availability
    FOR INSERT WITH CHECK (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id IN (
                SELECT id FROM lecturers WHERE user_id = auth.uid()
            )
        )
    );
CREATE POLICY availability_update ON lecturer_availability
    FOR UPDATE USING (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id IN (
                SELECT id FROM lecturers WHERE user_id = auth.uid()
            )
        )
    )
    WITH CHECK (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id IN (
                SELECT id FROM lecturers WHERE user_id = auth.uid()
            )
        )
    );
CREATE POLICY availability_delete ON lecturer_availability
    FOR DELETE USING (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id IN (
                SELECT id FROM lecturers WHERE user_id = auth.uid()
            )
        )
    );

-- client_error_logs
CREATE POLICY error_logs_insert ON client_error_logs
    FOR INSERT WITH CHECK (org_id = public.current_org_id());
CREATE POLICY error_logs_select ON client_error_logs
    FOR SELECT USING (public.is_admin() AND org_id = public.current_org_id());

-- ── Server-side overlap guard (race condition fix) ─────────────────────────-
CREATE OR REPLACE FUNCTION public.prevent_schedule_overlap()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM schedule_entries
        WHERE org_id = NEW.org_id
          AND day = NEW.day
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

-- ╔══════════════════════════════════════════════════════════════════╗
-- ║  SEED: Example organization + admin user                        ║
-- ║  Password: Admin123 (must change on first login)                ║
-- ╚══════════════════════════════════════════════════════════════════╝

-- Seed removed for Auth-based setup. Create the first admin via Supabase Auth
-- and insert a profile row in public.users referencing auth.users.id.
