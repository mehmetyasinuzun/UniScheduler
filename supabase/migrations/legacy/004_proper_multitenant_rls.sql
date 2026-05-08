-- ╔══════════════════════════════════════════════════════════════════╗
-- ║  Migration 004: Proper Multi-Tenant Row Level Security         ║
-- ║                                                                ║
-- ║  ⚠️  LEGACY — for installations that pre-date the consolidated  ║
-- ║       schema.sql. New installations only need schema.sql; this  ║
-- ║       file is idempotent (safe to re-run) but unnecessary.     ║
-- ║                                                                ║
-- ║  Why: Migration 003 left users_select as USING(true), allowing ║
-- ║  any authenticated user to read every user row across orgs.    ║
-- ║  This caused cross-org data leakage when users switched accts. ║
-- ║                                                                ║
-- ║  Fix: Use the existing SECURITY DEFINER helper (current_org_id)║
-- ║  which bypasses RLS — no recursion. Restrict reads to own row  ║
-- ║  or same-org rows. Tighten lecturer_id ownership check via a   ║
-- ║  new SECURITY DEFINER helper to avoid leaky subqueries.        ║
-- ╚══════════════════════════════════════════════════════════════════╝

-- ── Helper: returns lecturer.id for the current auth user (or NULL) ───────────
CREATE OR REPLACE FUNCTION public.current_lecturer_id()
RETURNS INT
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT id FROM public.lecturers WHERE user_id = auth.uid() LIMIT 1;
$$;

-- ── users: tighten SELECT — own row OR same-org rows only ─────────────────────
DROP POLICY IF EXISTS users_select ON users;
CREATE POLICY users_select ON users
    FOR SELECT USING (
        id = auth.uid()
        OR org_id = public.current_org_id()
    );

-- Allow admins to delete users within their org (for "delete lecturer" flows).
-- Previously there was no users_delete policy; mobile delete calls silently failed.
DROP POLICY IF EXISTS users_delete ON users;
CREATE POLICY users_delete ON users
    FOR DELETE USING (
        public.is_admin() AND org_id = public.current_org_id()
    );

-- Allow admins to update users in their org (e.g. role/profile flags).
-- Existing users_update_self stays for self-service password flow.
DROP POLICY IF EXISTS users_update_admin ON users;
CREATE POLICY users_update_admin ON users
    FOR UPDATE USING (
        public.is_admin() AND org_id = public.current_org_id()
    )
    WITH CHECK (
        public.is_admin() AND org_id = public.current_org_id()
    );

-- ── lecturer_availability: replace leaky subqueries with helper ───────────────
DROP POLICY IF EXISTS availability_select ON lecturer_availability;
CREATE POLICY availability_select ON lecturer_availability
    FOR SELECT USING (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id = public.current_lecturer_id()
        )
    );

DROP POLICY IF EXISTS availability_insert ON lecturer_availability;
CREATE POLICY availability_insert ON lecturer_availability
    FOR INSERT WITH CHECK (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id = public.current_lecturer_id()
        )
    );

DROP POLICY IF EXISTS availability_update ON lecturer_availability;
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

DROP POLICY IF EXISTS availability_delete ON lecturer_availability;
CREATE POLICY availability_delete ON lecturer_availability
    FOR DELETE USING (
        org_id = public.current_org_id() AND (
            public.is_admin() OR lecturer_id = public.current_lecturer_id()
        )
    );

-- ── Harden the schedule overlap trigger ───────────────────────────────────────
-- Mark it SECURITY DEFINER so the cross-row scan is consistent regardless of
-- the caller's RLS context. The check itself is already filtered by NEW.org_id.
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
          AND (
                lecturer_id = NEW.lecturer_id
                OR classroom_id = NEW.classroom_id
              )
          AND id <> COALESCE(NEW.id, -1)
          AND NOT (NEW.end_time <= start_time OR NEW.start_time >= end_time)
    ) THEN
        RAISE EXCEPTION 'Schedule conflict for lecturer or classroom.';
    END IF;
    RETURN NEW;
END;
$$;

-- ── Composite index to speed up cross-table org filters ───────────────────────
CREATE INDEX IF NOT EXISTS idx_users_org_role ON users(org_id, role);
CREATE INDEX IF NOT EXISTS idx_lecturers_user ON lecturers(user_id);
CREATE INDEX IF NOT EXISTS idx_offerings_course ON offerings(course_id);
CREATE INDEX IF NOT EXISTS idx_offerings_lecturer ON offerings(lecturer_id);

-- ── Sanity: ensure offerings has lecturer_id column referenced above ──────────
-- (Schema 001 declares it on schedule_entries; offerings carries lecturer_id
-- only via assignment flows. Guard against missing column on older DBs.)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'offerings'
          AND column_name = 'lecturer_id'
    ) THEN
        ALTER TABLE public.offerings
            ADD COLUMN lecturer_id INT REFERENCES public.lecturers(id) ON DELETE SET NULL;
        CREATE INDEX IF NOT EXISTS idx_offerings_lecturer
            ON public.offerings(lecturer_id);
    END IF;
END$$;
