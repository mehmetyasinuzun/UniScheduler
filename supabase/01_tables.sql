-- ============================================================
-- UniScheduler — 01: Tablo Oluşturma
-- Supabase SQL Editor'de ILCE SIRA ile çalıştırın
-- ============================================================

-- UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- 1. profiles (auth.users ile 1:1 ilişki)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.profiles (
    id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name        TEXT NOT NULL DEFAULT '',
    surname     TEXT NOT NULL DEFAULT '',
    role        TEXT NOT NULL DEFAULT 'STUDENT'
                    CHECK (role IN ('ADMIN', 'DEPT_HEAD', 'LECTURER', 'STUDENT')),
    department_id INT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.profiles IS 'Kullanıcı profilleri — auth.users tablosunu genişletir';

-- ============================================================
-- 2. departments
-- ============================================================
CREATE TABLE IF NOT EXISTS public.departments (
    id                   SERIAL PRIMARY KEY,
    name                 TEXT NOT NULL,
    code                 TEXT NOT NULL UNIQUE,
    dept_head_permission TEXT NOT NULL DEFAULT 'APPROVAL_REQUIRED'
                            CHECK (dept_head_permission IN ('FULL_ACCESS', 'APPROVAL_REQUIRED', 'READ_ONLY')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.departments IS 'Bölümler — her birinin kendi schedule config ayarı olabilir';

-- profiles → departments FK (deferred — çünkü departments sonra oluşturuluyor)
ALTER TABLE public.profiles
    ADD CONSTRAINT fk_profiles_department
    FOREIGN KEY (department_id) REFERENCES public.departments(id)
    ON DELETE SET NULL;

-- ============================================================
-- 3. lecturers
-- ============================================================
CREATE TABLE IF NOT EXISTS public.lecturers (
    id            SERIAL PRIMARY KEY,
    profile_id    UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    full_name     TEXT NOT NULL,
    title         TEXT NOT NULL DEFAULT '',
    department_id INT  NOT NULL REFERENCES public.departments(id) ON DELETE CASCADE,
    username      TEXT NOT NULL DEFAULT '',
    password      TEXT NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.lecturers IS 'Öğretim üyeleri — profile_id ile auth bağlantısı';
COMMENT ON COLUMN public.lecturers.username IS 'Auto-generated login username';
COMMENT ON COLUMN public.lecturers.password IS 'Auto-generated login password (plain — sadece admin görebilir)';

-- ============================================================
-- 4. courses
-- ============================================================
CREATE TABLE IF NOT EXISTS public.courses (
    id            SERIAL PRIMARY KEY,
    code          TEXT NOT NULL,
    name          TEXT NOT NULL,
    department_id INT  NOT NULL REFERENCES public.departments(id) ON DELETE CASCADE,
    credit        INT  NOT NULL DEFAULT 0,
    color_hex     TEXT NOT NULL DEFAULT '#4285F4',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(code, department_id)
);

COMMENT ON TABLE public.courses IS 'Dersler — her ders bir bölüme ait';

-- ============================================================
-- 5. course_lecturers (many-to-many)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.course_lecturers (
    id          SERIAL PRIMARY KEY,
    course_id   INT NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    lecturer_id INT NOT NULL REFERENCES public.lecturers(id) ON DELETE CASCADE,
    UNIQUE(course_id, lecturer_id)
);

COMMENT ON TABLE public.course_lecturers IS 'Ders-Hoca M:N ilişki tablosu';

-- ============================================================
-- 6. schedule_configs (her bölümün zaman dilimi ayarı)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.schedule_configs (
    id                    SERIAL PRIMARY KEY,
    department_id         INT  NOT NULL REFERENCES public.departments(id) ON DELETE CASCADE UNIQUE,
    slot_duration_minutes INT  NOT NULL DEFAULT 60,
    day_start_time        TEXT NOT NULL DEFAULT '08:00',
    day_end_time          TEXT NOT NULL DEFAULT '17:00',
    active_days           JSONB NOT NULL DEFAULT '[1,2,3,4,5]'::jsonb,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.schedule_configs IS 'Bölüm bazlı zaman dilimi konfigürasyonu';

-- ============================================================
-- 7. schedule_assignments (canlı program atamaları)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.schedule_assignments (
    id            SERIAL PRIMARY KEY,
    course_id     INT     NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    course_code   TEXT    NOT NULL DEFAULT '',
    course_name   TEXT    NOT NULL DEFAULT '',
    lecturer_id   INT     NOT NULL REFERENCES public.lecturers(id) ON DELETE CASCADE,
    lecturer_name TEXT    NOT NULL DEFAULT '',
    day_of_week   INT     NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    slot_index    INT     NOT NULL CHECK (slot_index >= 0),
    classroom     TEXT    NOT NULL DEFAULT '',
    semester      TEXT    NOT NULL DEFAULT '',
    is_locked     BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.schedule_assignments IS 'Canlı ders programı atamaları';
COMMENT ON COLUMN public.schedule_assignments.course_code IS 'Denormalize — export kolaylığı için';
COMMENT ON COLUMN public.schedule_assignments.is_locked IS 'true ise CSP algoritması bu dersi taşıyamaz';

-- ============================================================
-- 8. availability_slots (hoca müsaitlik grid'i)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.availability_slots (
    id           SERIAL PRIMARY KEY,
    lecturer_id  INT     NOT NULL REFERENCES public.lecturers(id) ON DELETE CASCADE,
    day_of_week  INT     NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    slot_index   INT     NOT NULL CHECK (slot_index >= 0),
    is_available BOOLEAN NOT NULL DEFAULT true,
    UNIQUE(lecturer_id, day_of_week, slot_index)
);

COMMENT ON TABLE public.availability_slots IS 'When2Meet-style hoca müsaitlik verileri';

-- ============================================================
-- 9. schedule_drafts (bölüm başkanı taslakları)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.schedule_drafts (
    id            SERIAL PRIMARY KEY,
    department_id INT     NOT NULL REFERENCES public.departments(id) ON DELETE CASCADE,
    created_by    UUID    NOT NULL REFERENCES auth.users(id),
    title         TEXT    NOT NULL DEFAULT '',
    assignments   JSONB   NOT NULL DEFAULT '[]'::jsonb,
    soft_score    REAL    NOT NULL DEFAULT 0,
    status        TEXT    NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
    admin_note    TEXT,
    reviewed_by   UUID    REFERENCES auth.users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at   TIMESTAMPTZ
);

COMMENT ON TABLE public.schedule_drafts IS 'Dept Head taslak programları — admin onayı gerektirir';

-- ============================================================
-- 10. change_requests (dual-routing istek sistemi)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.change_requests (
    id                    SERIAL PRIMARY KEY,
    lecturer_id           INT  NOT NULL REFERENCES public.lecturers(id) ON DELETE CASCADE,
    request_type          TEXT NOT NULL,
    current_data          TEXT NOT NULL DEFAULT '',
    requested_data        TEXT NOT NULL DEFAULT '',
    reason                TEXT NOT NULL DEFAULT '',
    status                TEXT NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    approval_mode         TEXT NOT NULL DEFAULT 'DUAL_APPROVAL'
                             CHECK (approval_mode IN ('DUAL_APPROVAL', 'ADMIN_ONLY', 'DEPT_HEAD_ONLY')),
    dept_head_status      TEXT NOT NULL DEFAULT 'PENDING'
                             CHECK (dept_head_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    dept_head_reviewed_by UUID REFERENCES auth.users(id),
    dept_head_note        TEXT,
    dept_head_reviewed_at TIMESTAMPTZ,
    admin_status          TEXT NOT NULL DEFAULT 'PENDING'
                             CHECK (admin_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    admin_reviewed_by     UUID REFERENCES auth.users(id),
    admin_note            TEXT,
    admin_reviewed_at     TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.change_requests IS 'Lecturer istekleri — dual-routing (Dept Head + Admin)';

-- ============================================================
-- 11. import_logs (Excel import geçmişi)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.import_logs (
    id         SERIAL PRIMARY KEY,
    admin_id   UUID    NOT NULL REFERENCES auth.users(id),
    file_name  TEXT    NOT NULL,
    file_url   TEXT    NOT NULL DEFAULT '',
    row_count  INT     NOT NULL DEFAULT 0,
    status     TEXT    NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.import_logs IS 'Excel import işlem kayıtları';

-- ============================================================
-- INDEXES — Performans için
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_profiles_dept       ON public.profiles(department_id);
CREATE INDEX IF NOT EXISTS idx_courses_dept         ON public.courses(department_id);
CREATE INDEX IF NOT EXISTS idx_lecturers_dept       ON public.lecturers(department_id);
CREATE INDEX IF NOT EXISTS idx_lecturers_profile    ON public.lecturers(profile_id);
CREATE INDEX IF NOT EXISTS idx_assignments_course   ON public.schedule_assignments(course_id);
CREATE INDEX IF NOT EXISTS idx_assignments_lecturer ON public.schedule_assignments(lecturer_id);
CREATE INDEX IF NOT EXISTS idx_assignments_day_slot ON public.schedule_assignments(day_of_week, slot_index);
CREATE INDEX IF NOT EXISTS idx_availability_lect    ON public.availability_slots(lecturer_id);
CREATE INDEX IF NOT EXISTS idx_drafts_dept          ON public.schedule_drafts(department_id);
CREATE INDEX IF NOT EXISTS idx_drafts_status        ON public.schedule_drafts(status);
CREATE INDEX IF NOT EXISTS idx_requests_lecturer    ON public.change_requests(lecturer_id);
CREATE INDEX IF NOT EXISTS idx_requests_status      ON public.change_requests(status);
CREATE INDEX IF NOT EXISTS idx_requests_admin_st    ON public.change_requests(admin_status);
CREATE INDEX IF NOT EXISTS idx_requests_dh_st       ON public.change_requests(dept_head_status);
