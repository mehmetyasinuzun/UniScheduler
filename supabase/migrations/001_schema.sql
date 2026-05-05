-- UniScheduler — Supabase Schema (Multi-tenant, Free-time Scheduling)
-- Run this entire file in Supabase → SQL Editor to reset the schema.
--
-- Key decisions:
--   - start_time / end_time stored as TEXT ("HH:MM") to avoid Postgres TIME → "HH:MM:SS" round-trip.
--   - term values match the Android spinner: Fall | Spring | Summer
--   - RLS disabled for simplicity; enforce data isolation at app layer (org_id filter on every query).

-- ─── Extensions ───────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── Drop existing tables (safe re-run) ───────────────────────────────────────
DROP TABLE IF EXISTS lecturer_unavailability CASCADE;
DROP TABLE IF EXISTS schedule_entries        CASCADE;
DROP TABLE IF EXISTS client_error_logs       CASCADE;
DROP TABLE IF EXISTS classrooms              CASCADE;
DROP TABLE IF EXISTS offerings               CASCADE;
DROP TABLE IF EXISTS courses                 CASCADE;
DROP TABLE IF EXISTS lecturers               CASCADE;
DROP TABLE IF EXISTS departments             CASCADE;
DROP TABLE IF EXISTS users                   CASCADE;
DROP TABLE IF EXISTS org_settings            CASCADE;
DROP TABLE IF EXISTS organizations           CASCADE;

-- ─── organizations ────────────────────────────────────────────────────────────
CREATE TABLE organizations (
    id         SERIAL      PRIMARY KEY,
    name       TEXT        NOT NULL,
    code       TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE organizations DISABLE ROW LEVEL SECURITY;

-- ─── org_settings ─────────────────────────────────────────────────────────────
CREATE TABLE org_settings (
    org_id            INTEGER PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    time_step_minutes INTEGER NOT NULL DEFAULT 10,
    active_days       TEXT[]  NOT NULL DEFAULT ARRAY['Monday','Tuesday','Wednesday','Thursday','Friday'],
    day_start         TEXT    NOT NULL DEFAULT '08:00',
    day_end           TEXT    NOT NULL DEFAULT '18:00'
);
ALTER TABLE org_settings DISABLE ROW LEVEL SECURITY;

-- ─── users ────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id               INTEGER     NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    username             TEXT        NOT NULL UNIQUE,
    password_hash        TEXT        NOT NULL,
    role                 TEXT        NOT NULL CHECK (role IN ('admin', 'lecturer')),
    must_change_password BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE users DISABLE ROW LEVEL SECURITY;

-- ─── departments ──────────────────────────────────────────────────────────────
CREATE TABLE departments (
    id     SERIAL  PRIMARY KEY,
    org_id INTEGER NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name   TEXT    NOT NULL,
    CONSTRAINT uq_departments_org_name UNIQUE (org_id, name)
);
ALTER TABLE departments DISABLE ROW LEVEL SECURITY;

-- ─── lecturers ────────────────────────────────────────────────────────────────
CREATE TABLE lecturers (
    id            SERIAL  PRIMARY KEY,
    org_id        INTEGER NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id       UUID    NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    title         TEXT    NOT NULL,
    first_name    TEXT    NOT NULL,
    last_name     TEXT    NOT NULL,
    email         TEXT,
    department_id INTEGER REFERENCES departments(id) ON DELETE SET NULL
);
ALTER TABLE lecturers DISABLE ROW LEVEL SECURITY;

-- ─── courses ──────────────────────────────────────────────────────────────────
CREATE TABLE courses (
    id            SERIAL  PRIMARY KEY,
    org_id        INTEGER NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code          TEXT    NOT NULL,
    name          TEXT    NOT NULL,
    theory_hours  INTEGER NOT NULL DEFAULT 0,
    lab_hours     INTEGER NOT NULL DEFAULT 0,
    credits       INTEGER NOT NULL DEFAULT 0,
    department_id INTEGER REFERENCES departments(id) ON DELETE SET NULL,
    CONSTRAINT uq_courses_org_code UNIQUE (org_id, code)
);
ALTER TABLE courses DISABLE ROW LEVEL SECURITY;

-- ─── offerings (opened course sections) ───────────────────────────────────────
CREATE TABLE offerings (
    id            SERIAL  PRIMARY KEY,
    org_id        INTEGER NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    course_id     INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    academic_year TEXT    NOT NULL,                                              -- e.g. 2025-2026
    term          TEXT    NOT NULL CHECK (term IN ('Fall', 'Spring', 'Summer')),
    class_year    INTEGER NOT NULL CHECK (class_year BETWEEN 1 AND 4),
    section       TEXT    NOT NULL DEFAULT 'A',
    capacity      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_offerings UNIQUE (org_id, course_id, academic_year, term, class_year, section)
);
ALTER TABLE offerings DISABLE ROW LEVEL SECURITY;

-- ─── classrooms ───────────────────────────────────────────────────────────────
CREATE TABLE classrooms (
    id            SERIAL  PRIMARY KEY,
    org_id        INTEGER NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    room_code     TEXT    NOT NULL,
    capacity      INTEGER NOT NULL CHECK (capacity > 0),
    type          TEXT    NOT NULL DEFAULT 'theory' CHECK (type IN ('theory', 'lab')),
    department_id INTEGER REFERENCES departments(id) ON DELETE SET NULL,
    CONSTRAINT uq_classrooms_org_code UNIQUE (org_id, room_code)
);
ALTER TABLE classrooms DISABLE ROW LEVEL SECURITY;

-- ─── schedule_entries ─────────────────────────────────────────────────────────
-- start_time / end_time stored as TEXT "HH:MM" — overlap detection done in app.
CREATE TABLE schedule_entries (
    id           SERIAL  PRIMARY KEY,
    org_id       INTEGER NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    offering_id  INTEGER NOT NULL REFERENCES offerings(id)     ON DELETE CASCADE,
    lecturer_id  INTEGER NOT NULL REFERENCES lecturers(id)     ON DELETE CASCADE,
    classroom_id INTEGER NOT NULL REFERENCES classrooms(id)    ON DELETE CASCADE,
    day          TEXT    NOT NULL CHECK (day IN ('Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday')),
    start_time   TEXT    NOT NULL,  -- "HH:MM"
    end_time     TEXT    NOT NULL,  -- "HH:MM"
    CONSTRAINT chk_time_order CHECK (start_time < end_time)
);
ALTER TABLE schedule_entries DISABLE ROW LEVEL SECURITY;

-- ─── client_error_logs ──────────────────────────────────────────────────────
CREATE TABLE client_error_logs (
    id           BIGSERIAL PRIMARY KEY,
    org_id       INTEGER REFERENCES organizations(id) ON DELETE SET NULL,
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
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE client_error_logs DISABLE ROW LEVEL SECURITY;

-- ─── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX idx_users_org           ON users(org_id);
CREATE INDEX idx_departments_org     ON departments(org_id);
CREATE INDEX idx_lecturers_org       ON lecturers(org_id);
CREATE INDEX idx_courses_org         ON courses(org_id);
CREATE INDEX idx_classrooms_org      ON classrooms(org_id);
CREATE INDEX idx_offerings_org       ON offerings(org_id);
CREATE INDEX idx_offerings_course    ON offerings(course_id);
CREATE INDEX idx_schedule_org        ON schedule_entries(org_id);
CREATE INDEX idx_schedule_lecturer   ON schedule_entries(lecturer_id);
CREATE INDEX idx_schedule_classroom  ON schedule_entries(classroom_id);
CREATE INDEX idx_error_logs_org      ON client_error_logs(org_id);
CREATE INDEX idx_error_logs_created  ON client_error_logs(created_at DESC);
