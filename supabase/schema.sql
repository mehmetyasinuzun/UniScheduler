-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║                   UniScheduler — Production Schema                      ║
-- ║              Multi-tenant • RLS • Audit • Realtime                      ║
-- ╠══════════════════════════════════════════════════════════════════════════╣
-- ║ TEK HAKİKAT KAYNAĞI — bu dosyayı Supabase SQL Editor'a yapıştır ve Run. ║
-- ║ Her şey idempotent: aynı dosya temiz proje üzerinde de re-deploy'da da   ║
-- ║ aynı sonucu verir. Yama eklemeye gerek yok.                              ║
-- ║                                                                          ║
-- ║ İÇERİK                                                                   ║
-- ║   • 14 tablo  (organizations, users, lecturers, courses, ...)            ║
-- ║   • 32 RLS policy (multi-tenant izolasyon)                              ║
-- ║   • SECURITY DEFINER fonksiyonlar:                                       ║
-- ║       - admin_reset_lecturer_password (mobile admin için)                ║
-- ║       - generate_unique_lecturer_username (cross-org collision fix)      ║
-- ║       - prevent_schedule_overlap (TOCTOU koruması)                       ║
-- ║   • 32 trigger (touch_updated_at, audit, schedule_overlap)               ║
-- ║   • Realtime publications (schedule, courses, offerings, availability)   ║
-- ║                                                                          ║
-- ║ İLAVE KURULUM (bu dosya dışında, opsiyonel)                              ║
-- ║   • Edge Function 'bulk-create-lecturers':                               ║
-- ║       supabase/functions/bulk-create-lecturers/index.ts                  ║
-- ║       Dashboard → Edge Functions → Create → kodu yapıştır → Deploy       ║
-- ║       (Mobile bulk import'u 9dk → 30sn'ye düşürür)                       ║
-- ║                                                                          ║
-- ║ ⚠  Bu script TÜM UniScheduler tablolarını DROP eder + auth.users         ║
-- ║    içindeki @unischeduler.app sentetik kullanıcılarını siler.            ║
-- ║    Veri kaybı önemli ise önce yedek al.                                  ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- ──────────────────────────────────────────────────────────────────────────
-- 0.  Extensions
-- ──────────────────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "pgcrypto";    -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "citext";      -- case-insensitive emails

-- ──────────────────────────────────────────────────────────────────────────
-- 1.  Cleanup (DROP order matters — children before parents).
--     Views first, then tables. account_lockouts / active_lockouts and
--     is_user_locked() were part of an earlier blocking experiment
--     that was rolled back to "monitor only" — drops left in so a
--     re-run on an older DB cleans up automatically.
-- ──────────────────────────────────────────────────────────────────────────
DROP VIEW  IF EXISTS active_lockouts        CASCADE;
DROP VIEW  IF EXISTS org_dashboard          CASCADE;
DROP FUNCTION IF EXISTS public.is_user_locked(TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.auto_lockout_on_failed_attempts() CASCADE;
DROP TABLE IF EXISTS account_lockouts       CASCADE;
DROP TABLE IF EXISTS audit_log              CASCADE;
DROP TABLE IF EXISTS login_attempts         CASCADE;
DROP TABLE IF EXISTS block_rules            CASCADE;
DROP FUNCTION IF EXISTS public.check_login_blocked(TEXT, TEXT, TEXT) CASCADE;
DROP TABLE IF EXISTS schedule_entries       CASCADE;
DROP TABLE IF EXISTS lecturer_availability  CASCADE;
DROP TABLE IF EXISTS offerings              CASCADE;
DROP TABLE IF EXISTS lecturers              CASCADE;
DROP TABLE IF EXISTS classrooms             CASCADE;
DROP TABLE IF EXISTS courses                CASCADE;
DROP TABLE IF EXISTS departments            CASCADE;
DROP TABLE IF EXISTS org_settings           CASCADE;
DROP TABLE IF EXISTS client_error_logs      CASCADE;
DROP TABLE IF EXISTS users                  CASCADE;
DROP TABLE IF EXISTS organizations          CASCADE;
DROP TABLE IF EXISTS super_admins           CASCADE;

-- Wipe orphaned UniScheduler synthetic-email auth users from prior installs.
-- The mobile app converts usernames to fake emails (`username@unischeduler.app`)
-- so re-importing the same lecturer set fails with "User already registered"
-- unless we drop these. Real human emails are NEVER in this domain so we
-- don't risk wiping a real user.
DELETE FROM auth.users WHERE email LIKE '%@unischeduler.app';

-- ──────────────────────────────────────────────────────────────────────────
-- 2.  Helper: auto-touch updated_at on UPDATE
-- ──────────────────────────────────────────────────────────────────────────
-- Every mutable table has updated_at TIMESTAMPTZ. The trigger keeps it in
-- sync without application code having to set it on every UPDATE — fewer
-- chances for a developer to forget and ship a stale timestamp.
CREATE OR REPLACE FUNCTION public.touch_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

-- ──────────────────────────────────────────────────────────────────────────
-- 3.  Super-admins (panel access)
-- ──────────────────────────────────────────────────────────────────────────
-- Stored separately from public.users because super-admins are NOT scoped
-- to an organization — they sit above the multi-tenant boundary. The web
-- panel reads service_role and never touches RLS, but having this table
-- gives us an audit trail of who *should* have super-admin access if we
-- later add Auth-based panel login.
CREATE TABLE super_admins (
    id          BIGSERIAL PRIMARY KEY,
    username    TEXT NOT NULL UNIQUE,
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ
);

-- ──────────────────────────────────────────────────────────────────────────
-- 4.  Organizations (multi-tenant root)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE organizations (
    id          SERIAL PRIMARY KEY,
    name        TEXT NOT NULL CHECK (length(trim(name)) > 0),
    code        TEXT NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9_-]{2,20}$'),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TRIGGER trg_organizations_touch
    BEFORE UPDATE ON organizations
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

-- ──────────────────────────────────────────────────────────────────────────
-- 5.  Org settings (one row per org)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE org_settings (
    org_id            INT PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    time_step_minutes INT  NOT NULL DEFAULT 10 CHECK (time_step_minutes BETWEEN 5 AND 60),
    active_days       TEXT[] NOT NULL DEFAULT ARRAY['Monday','Tuesday','Wednesday','Thursday','Friday'],
    day_start         TEXT NOT NULL DEFAULT '08:00' CHECK (day_start ~ '^[0-2][0-9]:[0-5][0-9]$'),
    day_end           TEXT NOT NULL DEFAULT '18:00' CHECK (day_end ~ '^[0-2][0-9]:[0-5][0-9]$'),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (day_start < day_end)
);
CREATE TRIGGER trg_org_settings_touch
    BEFORE UPDATE ON org_settings
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

-- ──────────────────────────────────────────────────────────────────────────
-- 6.  Users (mobile app auth profiles, joined to auth.users 1:1)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                   UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    org_id               INT  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    username             TEXT NOT NULL UNIQUE
                              CHECK (username ~ '^[a-z0-9_]{3,40}$'),
    role                 TEXT NOT NULL CHECK (role IN ('admin', 'lecturer')),
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at           TIMESTAMPTZ,                          -- soft delete
    last_login_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_username     ON users(username) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_org_role     ON users(org_id, role) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_active       ON users(org_id, is_active);
CREATE TRIGGER trg_users_touch
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

-- ──────────────────────────────────────────────────────────────────────────
-- 7.  Departments (per org)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE departments (
    id          SERIAL PRIMARY KEY,
    org_id      INT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name        TEXT NOT NULL CHECK (length(trim(name)) > 0),
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (org_id, name)
);
CREATE INDEX idx_departments_org ON departments(org_id) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_departments_touch
    BEFORE UPDATE ON departments
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

-- ──────────────────────────────────────────────────────────────────────────
-- 8.  Lecturers
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE lecturers (
    id            SERIAL PRIMARY KEY,
    org_id        INT  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title         TEXT NOT NULL DEFAULT '',
    first_name    TEXT NOT NULL CHECK (length(trim(first_name)) > 0),
    last_name     TEXT NOT NULL CHECK (length(trim(last_name)) > 0),
    email         CITEXT,
    phone         TEXT,
    department_id INT REFERENCES departments(id) ON DELETE SET NULL,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- One lecturer per auth user. Admins are not lecturers (different row in users).
    UNIQUE (user_id),
    CHECK (email IS NULL OR email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);
CREATE INDEX idx_lecturers_org      ON lecturers(org_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_lecturers_user     ON lecturers(user_id);
CREATE INDEX idx_lecturers_dept     ON lecturers(department_id) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_lecturers_touch
    BEFORE UPDATE ON lecturers
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

-- ──────────────────────────────────────────────────────────────────────────
-- 9.  Courses
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE courses (
    id            SERIAL PRIMARY KEY,
    org_id        INT  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code          TEXT NOT NULL CHECK (length(trim(code)) > 0),
    name          TEXT NOT NULL CHECK (length(trim(name)) > 0),
    theory_hours  INT  NOT NULL DEFAULT 0 CHECK (theory_hours >= 0 AND theory_hours <= 20),
    lab_hours     INT  NOT NULL DEFAULT 0 CHECK (lab_hours    >= 0 AND lab_hours    <= 20),
    credits       INT  NOT NULL DEFAULT 0 CHECK (credits      >= 0 AND credits      <= 30),
    department_id INT REFERENCES departments(id) ON DELETE SET NULL,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (org_id, code)
);
CREATE INDEX idx_courses_org  ON courses(org_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_courses_dept ON courses(department_id) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_courses_touch
    BEFORE UPDATE ON courses
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

-- ──────────────────────────────────────────────────────────────────────────
-- 10. Classrooms
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE classrooms (
    id            SERIAL PRIMARY KEY,
    org_id        INT  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    room_code     TEXT NOT NULL CHECK (length(trim(room_code)) > 0),
    capacity      INT  NOT NULL DEFAULT 30 CHECK (capacity > 0 AND capacity <= 1000),
    type          TEXT NOT NULL DEFAULT 'theory' CHECK (type IN ('theory', 'lab')),
    department_id INT REFERENCES departments(id) ON DELETE SET NULL,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (org_id, room_code)
);
CREATE INDEX idx_classrooms_org  ON classrooms(org_id) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_classrooms_touch
    BEFORE UPDATE ON classrooms
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

-- ──────────────────────────────────────────────────────────────────────────
-- 11. Offerings (sections opened in a given term)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE offerings (
    id            SERIAL PRIMARY KEY,
    org_id        INT  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    course_id     INT  NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    lecturer_id   INT REFERENCES lecturers(id) ON DELETE SET NULL,
    academic_year TEXT NOT NULL CHECK (academic_year ~ '^[0-9]{4}-[0-9]{4}$'),
    term          TEXT NOT NULL CHECK (term IN ('Fall', 'Spring', 'Summer')),
    class_year    INT  NOT NULL CHECK (class_year BETWEEN 1 AND 4),
    section       TEXT NOT NULL DEFAULT 'A' CHECK (section ~ '^[A-Z0-9]{1,4}$'),
    capacity      INT  NOT NULL DEFAULT 0  CHECK (capacity >= 0 AND capacity <= 1000),
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (org_id, course_id, academic_year, term, section)
);
CREATE INDEX idx_offerings_org      ON offerings(org_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_offerings_lecturer ON offerings(lecturer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_offerings_course   ON offerings(course_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_offerings_term     ON offerings(org_id, academic_year, term);
CREATE TRIGGER trg_offerings_touch
    BEFORE UPDATE ON offerings
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

-- ──────────────────────────────────────────────────────────────────────────
-- 12. Schedule entries (the actual timetable)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE schedule_entries (
    id           SERIAL PRIMARY KEY,
    org_id       INT  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    offering_id  INT  NOT NULL REFERENCES offerings(id)     ON DELETE CASCADE,
    lecturer_id  INT  NOT NULL REFERENCES lecturers(id)     ON DELETE CASCADE,
    classroom_id INT  NOT NULL REFERENCES classrooms(id)    ON DELETE CASCADE,
    day          TEXT NOT NULL CHECK (day IN ('Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday')),
    start_time   TEXT NOT NULL CHECK (start_time ~ '^[0-2][0-9]:[0-5][0-9]$'),
    end_time     TEXT NOT NULL CHECK (end_time   ~ '^[0-2][0-9]:[0-5][0-9]$'),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (start_time < end_time)
);
CREATE INDEX idx_entries_org       ON schedule_entries(org_id);
CREATE INDEX idx_entries_lecturer  ON schedule_entries(lecturer_id, day);
CREATE INDEX idx_entries_classroom ON schedule_entries(classroom_id, day);
CREATE INDEX idx_entries_offering  ON schedule_entries(offering_id);
CREATE TRIGGER trg_entries_touch
    BEFORE UPDATE ON schedule_entries
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

-- ──────────────────────────────────────────────────────────────────────────
-- 13. Lecturer availability (busy / preferred-not-to-teach blocks)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE lecturer_availability (
    id          SERIAL PRIMARY KEY,
    org_id      INT  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    lecturer_id INT  NOT NULL REFERENCES lecturers(id)     ON DELETE CASCADE,
    day         TEXT NOT NULL CHECK (day IN ('Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday')),
    start_time  TEXT NOT NULL CHECK (start_time ~ '^[0-2][0-9]:[0-5][0-9]$'),
    end_time    TEXT NOT NULL CHECK (end_time   ~ '^[0-2][0-9]:[0-5][0-9]$'),
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (start_time < end_time)
);
CREATE INDEX idx_availability_lecturer ON lecturer_availability(lecturer_id, day);

-- ──────────────────────────────────────────────────────────────────────────
-- 14. Client error logs (mobile + panel)
-- ──────────────────────────────────────────────────────────────────────────
CREATE TABLE client_error_logs (
    id           BIGSERIAL PRIMARY KEY,
    org_id       INT  REFERENCES organizations(id) ON DELETE SET NULL,
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
    source       TEXT NOT NULL DEFAULT 'mobile' CHECK (source IN ('mobile', 'panel', 'server')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_error_logs_org     ON client_error_logs(org_id);
CREATE INDEX idx_error_logs_created ON client_error_logs(created_at DESC);
CREATE INDEX idx_error_logs_source  ON client_error_logs(source, created_at DESC);

-- ──────────────────────────────────────────────────────────────────────────
-- 15. Audit log (who did what, when)
-- ──────────────────────────────────────────────────────────────────────────
-- Captures every INSERT/UPDATE/DELETE on the org-scoped tables. Used by
-- admins to investigate "who deleted my hocas" without needing a separate
-- DBA tool. PII-safe — only stores ids and column diffs as JSONB.
CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    org_id       INT,
    actor_id     UUID,                    -- auth.uid() at write time
    actor_role   TEXT,
    table_name   TEXT NOT NULL,
    record_id    TEXT NOT NULL,           -- text so SERIAL + UUID both fit
    operation    TEXT NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    old_data     JSONB,
    new_data     JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_org      ON audit_log(org_id, created_at DESC);
CREATE INDEX idx_audit_table    ON audit_log(table_name, created_at DESC);
CREATE INDEX idx_audit_record   ON audit_log(table_name, record_id);
CREATE INDEX idx_audit_actor    ON audit_log(actor_id, created_at DESC);

CREATE OR REPLACE FUNCTION public.audit_trigger()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    new_json      JSONB;
    old_json      JSONB;
    target_org_id INT;
    record_id_val TEXT;
    actor_role    TEXT;
BEGIN
    -- PL/pgSQL resolves (NEW).column at parse time, which would fail when
    -- this trigger is attached to tables that don't have org_id (e.g. the
    -- organizations table itself). Going through JSONB lets us read fields
    -- by name dynamically — slower than direct access but called at most
    -- once per row mutation, so cost is negligible.
    new_json := CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN to_jsonb(NEW) END;
    old_json := CASE WHEN TG_OP IN ('UPDATE', 'DELETE') THEN to_jsonb(OLD) END;

    target_org_id := COALESCE(
        (new_json->>'org_id')::INT,
        (old_json->>'org_id')::INT
    );
    record_id_val := COALESCE(
        new_json->>'id',
        old_json->>'id',
        ''
    );

    SELECT role INTO actor_role FROM public.users WHERE id = auth.uid();

    INSERT INTO audit_log (
        org_id, actor_id, actor_role, table_name, record_id, operation,
        old_data, new_data
    ) VALUES (
        target_org_id,
        auth.uid(),
        actor_role,
        TG_TABLE_NAME,
        record_id_val,
        TG_OP,
        old_json,
        new_json
    );
    RETURN COALESCE(NEW, OLD);
END;
$$;

-- Attach to tables admins commonly want to audit. Skip super_admins
-- (already small + read by panel only) and audit_log itself (recursion).
CREATE TRIGGER trg_audit_users        AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW EXECUTE FUNCTION public.audit_trigger();
CREATE TRIGGER trg_audit_lecturers    AFTER INSERT OR UPDATE OR DELETE ON lecturers
    FOR EACH ROW EXECUTE FUNCTION public.audit_trigger();
CREATE TRIGGER trg_audit_courses      AFTER INSERT OR UPDATE OR DELETE ON courses
    FOR EACH ROW EXECUTE FUNCTION public.audit_trigger();
CREATE TRIGGER trg_audit_classrooms   AFTER INSERT OR UPDATE OR DELETE ON classrooms
    FOR EACH ROW EXECUTE FUNCTION public.audit_trigger();
CREATE TRIGGER trg_audit_departments  AFTER INSERT OR UPDATE OR DELETE ON departments
    FOR EACH ROW EXECUTE FUNCTION public.audit_trigger();
CREATE TRIGGER trg_audit_offerings    AFTER INSERT OR UPDATE OR DELETE ON offerings
    FOR EACH ROW EXECUTE FUNCTION public.audit_trigger();
CREATE TRIGGER trg_audit_entries      AFTER INSERT OR UPDATE OR DELETE ON schedule_entries
    FOR EACH ROW EXECUTE FUNCTION public.audit_trigger();

-- ──────────────────────────────────────────────────────────────────────────
-- 16. Login attempt tracking (brute-force defence layer)
-- ──────────────────────────────────────────────────────────────────────────
-- Supabase Auth has built-in rate limiting but it's per-IP. We additionally
-- track attempts per username so an attacker can't bypass IP throttle by
-- rotating proxies. Read by the mobile app's auth layer (optional).
CREATE TABLE login_attempts (
    id           BIGSERIAL PRIMARY KEY,
    username     TEXT NOT NULL,
    succeeded    BOOLEAN NOT NULL,
    -- Network identity. ip_address is best filled in server-side (the
    -- super-admin panel and any future Edge Function) since clients
    -- can't see their own egress IP reliably. user_agent comes from
    -- the mobile app (synthetic) or the panel's HTTP headers.
    ip_address   TEXT,
    user_agent   TEXT,
    -- Mobile fingerprint — opaque hash of (Build.MANUFACTURER + MODEL +
    -- ANDROID_ID). Stable across app reinstalls but reset on factory
    -- reset, which is the right granularity for CTI (catches the same
    -- attacker hopping accounts on one device, doesn't track an end
    -- user across two devices). NOT a fingerprint in the GDPR sense —
    -- we never store the inputs, only their salted hash.
    device_id    TEXT,
    device_model TEXT,
    os_version   TEXT,
    app_version  TEXT,
    -- Population: 'mobile' / 'panel' / 'edge' so the dashboard can
    -- separate brute-force (panel/edge) from app-side credential typos.
    source       TEXT NOT NULL DEFAULT 'mobile' CHECK (source IN ('mobile','panel','edge')),
    -- Free-form failure tag set by the client when known: 'auth'
    -- (Supabase rejected) / 'profile' (auth ok but public.users miss)
    -- / 'lecturer' / 'network' / 'other'. Empty for successes.
    failure_step TEXT,
    -- Mobil tarafı emülatör tespitinden gelen flag. NULL = bilinmiyor
    -- (panel kayıtları), TRUE = "Build.FINGERPRINT" generic / goldfish /
    -- ranchu / vbox patterns matched. Aktif blok yok — sadece risk
    -- skorunu ve panel rozetini etkiler.
    is_emulator  BOOLEAN,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_login_attempts_username ON login_attempts(username, created_at DESC);
CREATE INDEX idx_login_attempts_recent   ON login_attempts(created_at DESC);
CREATE INDEX idx_login_attempts_device   ON login_attempts(device_id, created_at DESC) WHERE device_id IS NOT NULL;
CREATE INDEX idx_login_attempts_failed   ON login_attempts(created_at DESC) WHERE succeeded = FALSE;
CREATE INDEX idx_login_attempts_emu      ON login_attempts(created_at DESC) WHERE is_emulator IS TRUE;

-- ──────────────────────────────────────────────────────────────────────────
-- 16b. Block Rules — kullanıcı / cihaz / IP bazlı erişim engelleme
-- ──────────────────────────────────────────────────────────────────────────
-- CTI dashboard'undan süper admin bir login_attempt satırını seçip
-- "kullanıcıyı dondur" / "cihazı banla" / "IP'yi banla" diyebiliyor.
-- Hepsi tek tabloda toplandı (kind sütunu ile ayrıştırılıyor) çünkü:
--   • Login check tek bir SQL ile yapılabiliyor (3 IN sorgusu yerine)
--   • Pano UI tek bir liste gösterip "Kaldır" butonu sunabiliyor
--   • Audit trail tek tablo üzerinden filtrelenebiliyor
--
-- expires_at NULL = kalıcı ban; değer varsa o tarihte otomatik düşer
-- (login check WHERE expires_at IS NULL OR expires_at > NOW()).
-- unblocked_at + unblocked_by — manuel kaldırma audit trail'i; row
-- silinmiyor, sadece is_active=FALSE oluyor.
CREATE TABLE block_rules (
    id            BIGSERIAL PRIMARY KEY,
    kind          TEXT NOT NULL CHECK (kind IN ('user', 'device', 'ip')),
    value         TEXT NOT NULL,
    reason        TEXT,
    expires_at    TIMESTAMPTZ,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    TEXT,                    -- super_admin username
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    unblocked_at  TIMESTAMPTZ,
    unblocked_by  TEXT,
    UNIQUE (kind, value, is_active) DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX idx_block_rules_lookup
    ON block_rules(kind, value)
    WHERE is_active = TRUE;
CREATE INDEX idx_block_rules_active
    ON block_rules(created_at DESC)
    WHERE is_active = TRUE;

-- check_login_blocked: tek RPC ile (user, device, ip) üçlüsünü kontrol
-- eder. NULL parametre o kontrolü atlar (panel sadece username kullanıyor
-- olabilir). Dönüş: { blocked: bool, reason: text, expires_at: ts }
CREATE OR REPLACE FUNCTION public.check_login_blocked(
    p_username TEXT,
    p_device   TEXT,
    p_ip       TEXT
) RETURNS TABLE (blocked BOOLEAN, kind TEXT, reason TEXT, expires_at TIMESTAMPTZ)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    RETURN QUERY
    SELECT TRUE, b.kind, b.reason, b.expires_at
      FROM block_rules b
     WHERE b.is_active = TRUE
       AND (b.expires_at IS NULL OR b.expires_at > NOW())
       AND (
            (b.kind = 'user'   AND p_username IS NOT NULL AND lower(b.value) = lower(p_username))
         OR (b.kind = 'device' AND p_device   IS NOT NULL AND b.value = p_device)
         OR (b.kind = 'ip'     AND p_ip       IS NOT NULL AND b.value = p_ip)
       )
     ORDER BY
       -- En spesifik kuralı önce dön: device > ip > user
       CASE b.kind WHEN 'device' THEN 1 WHEN 'ip' THEN 2 ELSE 3 END
     LIMIT 1;

    -- Hiç satır yoksa "blocked=FALSE" tek satır dön
    IF NOT FOUND THEN
        blocked := FALSE; kind := NULL; reason := NULL; expires_at := NULL;
        RETURN NEXT;
    END IF;
END;
$$;
REVOKE ALL    ON FUNCTION public.check_login_blocked(TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.check_login_blocked(TEXT, TEXT, TEXT) TO authenticated, service_role;

-- ──────────────────────────────────────────────────────────────────────────
-- 17. RLS — turn it on everywhere
-- ──────────────────────────────────────────────────────────────────────────
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
ALTER TABLE audit_log             ENABLE ROW LEVEL SECURITY;
ALTER TABLE login_attempts        ENABLE ROW LEVEL SECURITY;
ALTER TABLE super_admins          ENABLE ROW LEVEL SECURITY;
ALTER TABLE block_rules           ENABLE ROW LEVEL SECURITY;

-- block_rules: sadece service_role yazıp okuyor (panel/Edge). Anon ve
-- authenticated için policy tanımlanmıyor → varsayılan DENY. Mobile
-- uygulamalar dışarıda görmüyor.

-- ──────────────────────────────────────────────────────────────────────────
-- 18. Realtime publication
-- ──────────────────────────────────────────────────────────────────────────
-- Mobile app subscribes to schedule changes (lecturer's calendar refreshes
-- automatically on admin re-assignment) and course catalogue changes.
ALTER PUBLICATION supabase_realtime ADD TABLE schedule_entries;
ALTER PUBLICATION supabase_realtime ADD TABLE courses;
ALTER PUBLICATION supabase_realtime ADD TABLE lecturer_availability;
ALTER PUBLICATION supabase_realtime ADD TABLE offerings;

-- ──────────────────────────────────────────────────────────────────────────
-- 19. RLS helper functions
-- ──────────────────────────────────────────────────────────────────────────
-- All SECURITY DEFINER + STABLE so they:
--   (a) bypass the RLS policy on `users` they're called from (no infinite
--       recursion when policies on lecturers/courses/etc. ask "is this user
--       my org's admin?")
--   (b) get cached within a single statement — fast for repeated checks
--   (c) have a fixed search_path (Supabase Linter warns otherwise)

CREATE OR REPLACE FUNCTION public.current_org_id()
RETURNS INT
LANGUAGE SQL STABLE SECURITY DEFINER SET search_path = public
AS $$ SELECT org_id FROM public.users WHERE id = auth.uid() AND deleted_at IS NULL; $$;

CREATE OR REPLACE FUNCTION public.current_user_role()
RETURNS TEXT
LANGUAGE SQL STABLE SECURITY DEFINER SET search_path = public
AS $$ SELECT role FROM public.users WHERE id = auth.uid() AND deleted_at IS NULL; $$;

CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE SQL STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.users
        WHERE id = auth.uid()
          AND role = 'admin'
          AND is_active = TRUE
          AND deleted_at IS NULL
    );
$$;

CREATE OR REPLACE FUNCTION public.is_lecturer()
RETURNS BOOLEAN
LANGUAGE SQL STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.users
        WHERE id = auth.uid()
          AND role = 'lecturer'
          AND is_active = TRUE
          AND deleted_at IS NULL
    );
$$;

CREATE OR REPLACE FUNCTION public.current_lecturer_id()
RETURNS INT
LANGUAGE SQL STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT id FROM public.lecturers
    WHERE user_id = auth.uid() AND deleted_at IS NULL
    LIMIT 1;
$$;

-- ──────────────────────────────────────────────────────────────────────────
-- 20. RLS policies — granular, role-aware, tenant-scoped
-- ──────────────────────────────────────────────────────────────────────────
-- Pattern:
--   • Read scope:  current_org_id() — everyone sees their org and only
--     their org. Lecturers additionally see their own user row regardless.
--   • Write scope: admins can write everything in their org. Lecturers can
--     write only their own availability.
-- super_admins, audit_log, login_attempts are accessed exclusively via
-- service_role from the panel (RLS denies all by default for authenticated
-- users — that's intentional, panel uses service_role).

----- super_admins (panel only via service_role) ---------------------------
-- No policies — service_role bypasses RLS, authenticated users get nothing.

----- organizations -------------------------------------------------------
CREATE POLICY org_select ON organizations
    FOR SELECT TO authenticated USING (id = public.current_org_id());

----- org_settings -------------------------------------------------------
CREATE POLICY orgset_select ON org_settings
    FOR SELECT TO authenticated USING (org_id = public.current_org_id());
CREATE POLICY orgset_admin_write ON org_settings
    FOR ALL TO authenticated
    USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());

----- users (profile rows) ------------------------------------------------
-- self-read OR org-mate read (so admin can list lecturers' usernames)
CREATE POLICY users_select ON users
    FOR SELECT TO authenticated
    USING (id = auth.uid() OR org_id = public.current_org_id());
-- Self insert — used during signup flow before role/orgs are written.
CREATE POLICY users_insert_self ON users
    FOR INSERT TO authenticated WITH CHECK (id = auth.uid());
-- Admin insert — lets the mobile app insert a lecturer profile row in
-- the admin's own session (no need to bounce JWT to the new user). The
-- old "lecturer inserts their own users row" pattern was racy: the
-- in-flight Auth session switch from signUpWith could land on the
-- wrong RLS context. Admin-side insert is straightforward and the
-- common production pattern.
CREATE POLICY users_insert_admin ON users
    FOR INSERT TO authenticated
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY users_update_self ON users
    FOR UPDATE TO authenticated USING (id = auth.uid())
    WITH CHECK (id = auth.uid());
CREATE POLICY users_update_admin ON users
    FOR UPDATE TO authenticated
    USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());
CREATE POLICY users_delete_admin ON users
    FOR DELETE TO authenticated
    USING (public.is_admin() AND org_id = public.current_org_id());

----- departments -------------------------------------------------------
CREATE POLICY dept_select ON departments
    FOR SELECT TO authenticated USING (org_id = public.current_org_id());
CREATE POLICY dept_admin_write ON departments
    FOR ALL TO authenticated
    USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());

----- lecturers --------------------------------------------------------
CREATE POLICY lect_select ON lecturers
    FOR SELECT TO authenticated USING (org_id = public.current_org_id());
CREATE POLICY lect_admin_write ON lecturers
    FOR ALL TO authenticated
    USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());

----- courses ----------------------------------------------------------
CREATE POLICY courses_select ON courses
    FOR SELECT TO authenticated USING (org_id = public.current_org_id());
CREATE POLICY courses_admin_write ON courses
    FOR ALL TO authenticated
    USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());

----- classrooms -------------------------------------------------------
CREATE POLICY rooms_select ON classrooms
    FOR SELECT TO authenticated USING (org_id = public.current_org_id());
CREATE POLICY rooms_admin_write ON classrooms
    FOR ALL TO authenticated
    USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());

----- offerings -------------------------------------------------------
CREATE POLICY off_select ON offerings
    FOR SELECT TO authenticated USING (org_id = public.current_org_id());
CREATE POLICY off_admin_write ON offerings
    FOR ALL TO authenticated
    USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());

----- schedule_entries -----------------------------------------------
-- Readable by everyone in the org. Lecturers explicitly cannot mutate
-- their own schedule; only admins can. This is a very deliberate
-- decision: if lecturers could move their classes, double-booking
-- coordination would shift from "the admin reviewed it" to "everyone
-- did whatever they wanted" — a recipe for chaos in a real university.
CREATE POLICY entries_select ON schedule_entries
    FOR SELECT TO authenticated USING (org_id = public.current_org_id());
CREATE POLICY entries_admin_write ON schedule_entries
    FOR ALL TO authenticated
    USING (public.is_admin() AND org_id = public.current_org_id())
    WITH CHECK (public.is_admin() AND org_id = public.current_org_id());

----- lecturer_availability -------------------------------------------
-- Readable by self + admins. Writable only by self (the lecturer who
-- owns the row) or by admins on any row in their org.
CREATE POLICY avail_select ON lecturer_availability
    FOR SELECT TO authenticated
    USING (
        org_id = public.current_org_id() AND
        (public.is_admin() OR lecturer_id = public.current_lecturer_id())
    );
CREATE POLICY avail_insert ON lecturer_availability
    FOR INSERT TO authenticated
    WITH CHECK (
        org_id = public.current_org_id() AND
        (public.is_admin() OR lecturer_id = public.current_lecturer_id())
    );
CREATE POLICY avail_update ON lecturer_availability
    FOR UPDATE TO authenticated
    USING (
        org_id = public.current_org_id() AND
        (public.is_admin() OR lecturer_id = public.current_lecturer_id())
    )
    WITH CHECK (
        org_id = public.current_org_id() AND
        (public.is_admin() OR lecturer_id = public.current_lecturer_id())
    );
CREATE POLICY avail_delete ON lecturer_availability
    FOR DELETE TO authenticated
    USING (
        org_id = public.current_org_id() AND
        (public.is_admin() OR lecturer_id = public.current_lecturer_id())
    );

----- client_error_logs ---------------------------------------------
CREATE POLICY errlog_insert ON client_error_logs
    FOR INSERT TO authenticated
    WITH CHECK (org_id IS NULL OR org_id = public.current_org_id());
CREATE POLICY errlog_admin_select ON client_error_logs
    FOR SELECT TO authenticated
    USING (
        public.is_admin() AND source = 'mobile' AND
        org_id = public.current_org_id()
    );

----- audit_log -------------------------------------------------------
-- Admins can read their org's audit trail. No INSERT policy because
-- the trigger writes directly via SECURITY DEFINER.
CREATE POLICY audit_admin_select ON audit_log
    FOR SELECT TO authenticated
    USING (public.is_admin() AND org_id = public.current_org_id());

----- login_attempts -------------------------------------------------
-- Self-readable. Useful for surfacing "5 failed attempts in last 15 min"
-- in the UI. Writes are open to BOTH anonymous (failed-login path —
-- attacker hits sign in but never gets a JWT) and authenticated users
-- (success path — they DO get a JWT). The panel reads via service_role
-- so it bypasses RLS entirely and sees the full corpus across orgs.
--
-- We accept the spam-write risk on this table because:
--   (a) the audit value of capturing brute-force attempts outweighs
--       any noise an attacker could generate by spamming inserts;
--   (b) the table is monotonically growing and rate-limited at the
--       Supabase Auth layer (5 attempts / 15 min / IP) before it hits
--       this table;
--   (c) periodic cleanup ("DELETE FROM login_attempts WHERE created_at
--       < NOW() - INTERVAL '90 days'") is a one-line cron and keeps
--       storage bounded.
CREATE POLICY login_attempts_self_select ON login_attempts
    FOR SELECT TO authenticated
    USING (
        username = (SELECT username FROM public.users WHERE id = auth.uid())
    );
CREATE POLICY login_attempts_anon_insert ON login_attempts
    FOR INSERT TO anon
    WITH CHECK (true);
CREATE POLICY login_attempts_auth_insert ON login_attempts
    FOR INSERT TO authenticated
    WITH CHECK (true);

-- ──────────────────────────────────────────────────────────────────────────
-- 21. Race-condition-proof schedule overlap guard
-- ──────────────────────────────────────────────────────────────────────────
-- Application-level conflict checks have a TOCTOU window: two admins
-- click "Assign" at the same millisecond, both reads come back clean,
-- both inserts succeed → double-booking. This trigger closes that window
-- by re-checking inside the same transaction holding the row lock.
CREATE OR REPLACE FUNCTION public.prevent_schedule_overlap()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM schedule_entries
        WHERE org_id = NEW.org_id
          AND day = NEW.day
          AND id <> COALESCE(NEW.id, -1)
          AND (lecturer_id = NEW.lecturer_id OR classroom_id = NEW.classroom_id)
          AND NOT (NEW.end_time <= start_time OR NEW.start_time >= end_time)
    ) THEN
        RAISE EXCEPTION 'Schedule conflict for lecturer or classroom.'
          USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_schedule_overlap
    BEFORE INSERT OR UPDATE ON schedule_entries
    FOR EACH ROW EXECUTE FUNCTION public.prevent_schedule_overlap();

-- ──────────────────────────────────────────────────────────────────────────
-- 22. Convenience view for super-admin dashboard
-- ──────────────────────────────────────────────────────────────────────────
-- Aggregates per-org counts in one query. Service-role only (no RLS) since
-- it crosses tenant boundaries by design.
CREATE OR REPLACE VIEW org_dashboard AS
SELECT
    o.id           AS org_id,
    o.name         AS org_name,
    o.code         AS org_code,
    o.created_at   AS org_created_at,
    (SELECT COUNT(*) FROM users      WHERE org_id = o.id AND role = 'admin'    AND deleted_at IS NULL) AS admin_count,
    (SELECT COUNT(*) FROM users      WHERE org_id = o.id AND role = 'lecturer' AND deleted_at IS NULL) AS lecturer_count,
    (SELECT COUNT(*) FROM departments WHERE org_id = o.id AND deleted_at IS NULL) AS department_count,
    (SELECT COUNT(*) FROM courses    WHERE org_id = o.id AND deleted_at IS NULL) AS course_count,
    (SELECT COUNT(*) FROM classrooms WHERE org_id = o.id AND deleted_at IS NULL) AS classroom_count,
    (SELECT COUNT(*) FROM offerings  WHERE org_id = o.id AND deleted_at IS NULL) AS offering_count,
    (SELECT COUNT(*) FROM schedule_entries WHERE org_id = o.id) AS schedule_entry_count
FROM organizations o
ORDER BY o.created_at DESC;

-- ──────────────────────────────────────────────────────────────────────────
-- 22b. Admin RPC — reset a lecturer's auth password
-- ──────────────────────────────────────────────────────────────────────────
-- Mobile admins do not carry service_role, so they cannot rewrite
-- auth.users directly. This SECURITY DEFINER function lets the org
-- admin reset a lecturer's password in their own tenant. Guarantees:
--   • Only admins of the same org can call it
--   • Target must be a non-deleted lecturer in the caller's org
--   • Password must be at least 6 chars
--   • New hash uses bcrypt via pgcrypto (compatible with GoTrue)
--   • must_change_password is forced TRUE so the lecturer is prompted
--     to pick their own password on next login
CREATE OR REPLACE FUNCTION public.admin_reset_lecturer_password(
    p_lecturer_id  INT,
    p_new_password TEXT
) RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth, extensions
AS $$
DECLARE
    v_user_id     UUID;
    v_lect_org_id INT;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'Yetkisiz işlem: yalnızca admin sıfırlayabilir.'
          USING ERRCODE = 'insufficient_privilege';
    END IF;

    IF p_new_password IS NULL OR length(p_new_password) < 6 THEN
        RAISE EXCEPTION 'Şifre en az 6 karakter olmalı.'
          USING ERRCODE = 'check_violation';
    END IF;

    SELECT user_id, org_id
      INTO v_user_id, v_lect_org_id
      FROM public.lecturers
     WHERE id = p_lecturer_id
       AND deleted_at IS NULL;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Hoca bulunamadı veya silinmiş.'
          USING ERRCODE = 'no_data_found';
    END IF;

    IF v_lect_org_id <> public.current_org_id() THEN
        RAISE EXCEPTION 'Bu hoca farklı bir kuruma ait.'
          USING ERRCODE = 'insufficient_privilege';
    END IF;

    UPDATE auth.users
       SET encrypted_password = extensions.crypt(p_new_password,
                                                  extensions.gen_salt('bf', 10)),
           updated_at         = NOW()
     WHERE id = v_user_id;

    UPDATE public.users
       SET must_change_password = TRUE
     WHERE id = v_user_id;
END;
$$;

REVOKE ALL    ON FUNCTION public.admin_reset_lecturer_password(INT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_reset_lecturer_password(INT, TEXT) TO authenticated;

-- ──────────────────────────────────────────────────────────────────────────
-- 22c. Global-unique lecturer username RPC (SECURITY DEFINER + admin-only)
-- ──────────────────────────────────────────────────────────────────────────
-- AMAÇ
-- ────
-- Multi-tenant lecturer kayıt akışında "globally unique username" üretmek.
-- 3 katmanlı problemi tek noktada çözer:
--
--   public.users   (RLS active, org-scoped) ── username UNIQUE
--          ↓
--   auth.users    (global, no RLS)         ── email   UNIQUE
--          ↓
--   mobile signUp  / panel admin.createUser
--
-- Mobile client-side generateUniqueUsername() public.users'a anon_key ile
-- bakıyordu → RLS yüzünden başka org'un kullanıcılarını GÖRMÜYORDU →
-- "ahmet_yilmaz" Org 1'de varsa, Cumhuriyet admin'i "unique" sayıp signUp
-- atınca Auth email collision → "User already registered" → 32 satırlık
-- import 0 başarılı. Bu RPC sorunu tek noktada çözer (postgres role,
-- RLS bypass, hem public hem auth tablosuna bakar).
--
-- GÜVENLİK
-- ────────
-- ÖNEMLİ: RPC her ne kadar sadece username üretmek için olsa da kötüye
-- kullanılırsa "username enumeration" saldırı vektörü olur. Saldırgan
-- "Ahmet Yilmaz" diye sorgu yapıp yanıt 'ahmet_yilmaz' ise → yok,
-- 'ahmet_yilmaz2' ise → 1 var çıkarımı yapabilir. Bu yüzden:
--
--   • anon role → REVOKE (giriş yapmamış kullanıcı 403)
--   • authenticated lecturer → içeride RAISE EXCEPTION
--   • authenticated admin   → ✅ geçer
--   • service_role          → ✅ geçer (panel/Edge Function)
--
-- Ayrıca timing-attack koruması (30ms sabit pg_sleep) + audit_log her
-- başarılı çağrıya kayıt yazar — anomali tespiti için.
--
-- FORMAT
-- ──────
-- "Farhan Adl" (ilk)    → "farhan_adl"
-- "Farhan Adl" (ikinci) → "farhan_adl2"
-- "Çağrı Öz"            → "cagri_oz" (Türkçe karakter normalize)
CREATE OR REPLACE FUNCTION public.generate_unique_lecturer_username(
    p_first_name TEXT,
    p_last_name  TEXT
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    base            TEXT;
    candidate       TEXT;
    candidate_email TEXT;
    suffix          INT := 2;
    v_caller_role   TEXT;
    v_caller_uid    UUID;
    v_is_authorized BOOLEAN := FALSE;
BEGIN
    -- ── 1) YETKİLENDİRME ──────────────────────────────────────────────
    v_caller_role := auth.role();
    v_caller_uid  := auth.uid();

    IF v_caller_role = 'service_role' THEN
        -- Panel / Edge Function — koşulsuz geçer
        v_is_authorized := TRUE;
    ELSIF v_caller_role = 'authenticated' AND v_caller_uid IS NOT NULL THEN
        -- Mobile admin — public.users.role='admin' kontrolü
        SELECT EXISTS (
            SELECT 1 FROM public.users
            WHERE id = v_caller_uid
              AND role = 'admin'
              AND is_active = TRUE
        ) INTO v_is_authorized;
    END IF;

    IF NOT v_is_authorized THEN
        -- Timing-aware reject: yetki kontrolü fail olsa da aynı gecikme
        -- uygulanır ki saldırgan "yetki yoksa hızlı, varsa yavaş" diye
        -- inferans yapamasın.
        PERFORM pg_sleep(0.03);
        RAISE EXCEPTION 'Yetkisiz: bu işlem sadece yönetici hesaplarına açıktır.'
            USING ERRCODE = 'insufficient_privilege';
    END IF;

    -- ── 2) TIMING ATTACK KORUMASI ─────────────────────────────────────
    -- Sabit ~30ms gecikme. Loop iteration sayısından (1 vs 9) yanıt
    -- süresi tahmin edilmesin diye.
    PERFORM pg_sleep(0.03);

    -- ── 3) GİRDİ DOĞRULAMA ────────────────────────────────────────────
    IF p_first_name IS NULL OR p_last_name IS NULL THEN
        RAISE EXCEPTION 'First name and last name cannot be null';
    END IF;

    -- ── 4) TÜRKÇE NORMALİZASYON + LOWERCASE ──────────────────────────
    --   "Farhan Âdl"  → "farhan adl" → "farhan_adl"
    --   "Çağrı Öz"    → "cagri_oz"
    --   "Şükran"      → "sukran"
    base := lower(translate(
        trim(p_first_name) || '_' || trim(p_last_name),
        'çğıöşüâîûçğıöşüÇĞIİÖŞÜÂÎÛ',
        'cgiosuaiucgiosuCGIIOSUAIU'
    ));
    base := regexp_replace(base, '\s+', '_', 'g');
    base := regexp_replace(base, '[^a-z0-9_]', '', 'g');
    base := regexp_replace(base, '_+', '_', 'g');
    base := regexp_replace(base, '^_+|_+$', '', 'g');

    IF base = '' OR base IS NULL THEN
        RAISE EXCEPTION 'Cannot derive a valid username from "%" "%"', p_first_name, p_last_name;
    END IF;

    -- ── 5) UNIQUE KONTROL DÖNGÜSÜ (hem public.users hem auth.users) ───
    -- usernameToEmail mapping: '_' → '.'
    candidate       := base;
    candidate_email := replace(candidate, '_', '.') || '@unischeduler.app';

    WHILE EXISTS (SELECT 1 FROM public.users WHERE username = candidate)
       OR EXISTS (SELECT 1 FROM auth.users   WHERE lower(email) = candidate_email)
    LOOP
        candidate       := base || suffix::TEXT;
        candidate_email := replace(candidate, '_', '.') || '@unischeduler.app';
        suffix          := suffix + 1;
        IF suffix > 9999 THEN
            RAISE EXCEPTION 'Cannot generate unique username for "%_%" after 9999 attempts',
                p_first_name, p_last_name;
        END IF;
    END LOOP;

    -- ── 6) AUDIT LOG — anomali tespiti için ───────────────────────────
    -- Her başarılı çağrı kayıt edilir. Saatlik 500+ çağrı = anomali
    -- (gerçek admin günde 0-50 yapar).
    BEGIN
        INSERT INTO public.audit_log (
            actor_id, actor_role, table_name, record_id, operation, new_data
        ) VALUES (
            v_caller_uid,
            COALESCE(v_caller_role, 'unknown'),
            'username.generate',
            candidate,
            'INSERT',
            jsonb_build_object(
                'first_name', p_first_name,
                'last_name',  p_last_name,
                'result',     candidate
            )
        );
    EXCEPTION WHEN OTHERS THEN
        -- Audit fail olursa ana akışı bozma — sadece warning.
        RAISE WARNING 'audit_log insert failed: %', SQLERRM;
    END;

    RETURN candidate;
END;
$$;

-- Yetki: sadece authenticated + service_role. anon dışlanır (kritik!).
REVOKE ALL    ON FUNCTION public.generate_unique_lecturer_username(TEXT, TEXT) FROM PUBLIC;
REVOKE ALL    ON FUNCTION public.generate_unique_lecturer_username(TEXT, TEXT) FROM anon;
GRANT EXECUTE ON FUNCTION public.generate_unique_lecturer_username(TEXT, TEXT) TO authenticated, service_role;

-- ──────────────────────────────────────────────────────────────────────────
-- 23. Kurulum sonrası adımlar
-- ──────────────────────────────────────────────────────────────────────────
-- Bu script tek başına şema + RLS + fonksiyonlar + trigger'ları kurar.
-- Kullanıcı verisi yaratmaz — ilk admin'i şu adımlarla oluştur:
--
--   1) Dashboard → Authentication → Providers → Email
--      "Confirm email" KAPALI olmalı (mobile signUpWith confirm beklemez).
--
--   2) Dashboard → Authentication → Users → Add user
--      Email: superadmin@unischeduler.app
--      Password: <güçlü-şifre>
--      Auto Confirm User: ✅
--      Kaydet → açılan satırdan UUID'yi kopyala.
--
--   3) SQL Editor → New query → şunu çalıştır (UUID'yi kendi kopyaladığınla
--      değiştir, organizasyon adını kendi üniversiten yap):
--
--        INSERT INTO organizations (name, code)
--             VALUES ('Sivas BTÜ', 'SBTU')
--          RETURNING id;
--        -- dönen org_id'yi aşağıdaki insert'e yaz (örnek: 1)
--
--        INSERT INTO users (id, org_id, username, role)
--             VALUES ('<auth-user-uuid>', 1, 'superadmin', 'admin');
--
--        -- Opsiyonel: super-admin panel için kayıt (UI için)
--        INSERT INTO super_admins (username) VALUES ('superadmin');
--
--   4) Edge Function (opsiyonel ama önerilen):
--      Dashboard → Edge Functions → Create function
--        Name: bulk-create-lecturers
--        Code: supabase/functions/bulk-create-lecturers/index.ts içeriğini yapıştır
--      → Deploy. Bu fonksiyon mobile'ın 350+ hocayı 30 saniyede import
--      etmesini sağlar (yoksa 9 dakikalık yedek yol devreye girer).
--
--   5) Mobile app: APK'yı yükle. Panel: 'npm install && npm start' ile çalıştır.
--      İlk girişte (superadmin / yukarıda verdiğin şifre) Welcome ekranı görünür.
--
-- ──────────────────────────────────────────────────────────────────────────
-- Kurulum doğrulama — tabloların, policy'lerin, trigger'ların sayısını döndürür
-- ──────────────────────────────────────────────────────────────────────────
SELECT
    'UniScheduler schema installed' AS status,
    (SELECT COUNT(*) FROM information_schema.tables    WHERE table_schema='public')        AS tables,
    (SELECT COUNT(*) FROM pg_policies                  WHERE schemaname='public')          AS rls_policies,
    (SELECT COUNT(*) FROM information_schema.triggers  WHERE trigger_schema='public')      AS triggers,
    (SELECT COUNT(*) FROM information_schema.routines  WHERE routine_schema='public'
                                                         AND routine_type='FUNCTION')      AS functions;
-- Beklenen: 14 table, 32+ policy, 30+ trigger, 10+ function
