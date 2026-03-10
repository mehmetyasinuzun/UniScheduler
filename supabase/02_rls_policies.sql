-- ============================================================
-- UniScheduler — 02: Row Level Security (RLS) Policies
-- 01_tables.sql ÇALIŞTIRILMADAN ÖNCE BU DOSYA ÇALIŞTIRILMAMALIDIR
-- ============================================================

-- ============================================================
-- RLS'yi TÜM tablolarda etkinleştir
-- ============================================================
ALTER TABLE public.profiles            ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.departments         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lecturers           ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.courses             ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.course_lecturers    ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedule_configs    ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedule_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.availability_slots  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedule_drafts     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.change_requests     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.import_logs         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lecturer_availability ENABLE ROW LEVEL SECURITY;

-- ============================================================
-- Helper Functions (RLS policy'lerinde kullanılacak)
-- ============================================================

-- Mevcut kullanıcının rolünü döndür
CREATE OR REPLACE FUNCTION public.get_user_role()
RETURNS TEXT AS $$
    SELECT role FROM public.profiles WHERE id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- Mevcut kullanıcının bölüm ID'sini döndür
CREATE OR REPLACE FUNCTION public.get_user_department_id()
RETURNS INT AS $$
    SELECT department_id FROM public.profiles WHERE id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- ============================================================
-- PROFILES Policies
-- ============================================================

-- Herkes kendi profilini görebilir
DROP POLICY IF EXISTS "profiles_select_own" ON public.profiles;
CREATE POLICY "profiles_select_own"
    ON public.profiles FOR SELECT
    USING (id = auth.uid());

-- Admin tüm profilleri görebilir
DROP POLICY IF EXISTS "profiles_select_admin" ON public.profiles;
CREATE POLICY "profiles_select_admin"
    ON public.profiles FOR SELECT
    USING (public.get_user_role() = 'ADMIN');



-- Kullanıcı kendi profilini güncelleyebilir
DROP POLICY IF EXISTS "profiles_update_own" ON public.profiles;
CREATE POLICY "profiles_update_own"
    ON public.profiles FOR UPDATE
    USING (id = auth.uid());

-- Kullanıcı kendi profilini oluşturabilir (signup sonrası)
DROP POLICY IF EXISTS "profiles_insert_own" ON public.profiles;
CREATE POLICY "profiles_insert_own"
    ON public.profiles FOR INSERT
    WITH CHECK (id = auth.uid());

-- Admin tüm profilleri yönetebilir
DROP POLICY IF EXISTS "profiles_all_admin" ON public.profiles;
CREATE POLICY "profiles_all_admin"
    ON public.profiles FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- ============================================================
-- DEPARTMENTS Policies
-- ============================================================

-- Giriş yapmış herkes bölümleri görebilir
DROP POLICY IF EXISTS "departments_select_auth" ON public.departments;
CREATE POLICY "departments_select_auth"
    ON public.departments FOR SELECT
    TO authenticated
    USING (true);

-- Admin bölüm yönetimi (insert/update/delete)
DROP POLICY IF EXISTS "departments_all_admin" ON public.departments;
CREATE POLICY "departments_all_admin"
    ON public.departments FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- ============================================================
-- LECTURERS Policies
-- ============================================================

-- Lecturer kendi kaydını görebilir
DROP POLICY IF EXISTS "lecturers_select_own" ON public.lecturers;
CREATE POLICY "lecturers_select_own"
    ON public.lecturers FOR SELECT
    USING (profile_id = auth.uid());

-- Admin tüm hocaları görebilir
DROP POLICY IF EXISTS "lecturers_select_admin" ON public.lecturers;
CREATE POLICY "lecturers_select_admin"
    ON public.lecturers FOR SELECT
    USING (public.get_user_role() = 'ADMIN');





-- Admin tüm hocaları yönetebilir
DROP POLICY IF EXISTS "lecturers_all_admin" ON public.lecturers;
CREATE POLICY "lecturers_all_admin"
    ON public.lecturers FOR ALL
    USING (public.get_user_role() = 'ADMIN');





-- ============================================================
-- COURSES Policies
-- ============================================================

-- Giriş yapmış herkes dersleri görebilir
DROP POLICY IF EXISTS "courses_select_auth" ON public.courses;
CREATE POLICY "courses_select_auth"
    ON public.courses FOR SELECT
    TO authenticated
    USING (true);

-- Admin tüm dersleri yönetebilir
DROP POLICY IF EXISTS "courses_all_admin" ON public.courses;
CREATE POLICY "courses_all_admin"
    ON public.courses FOR ALL
    USING (public.get_user_role() = 'ADMIN');







-- ============================================================
-- COURSE_LECTURERS Policies
-- ============================================================

-- Giriş yapmış herkes görebilir
DROP POLICY IF EXISTS "course_lecturers_select_auth" ON public.course_lecturers;
CREATE POLICY "course_lecturers_select_auth"
    ON public.course_lecturers FOR SELECT
    TO authenticated
    USING (true);

-- Admin tam yetki
DROP POLICY IF EXISTS "course_lecturers_all_admin" ON public.course_lecturers;
CREATE POLICY "course_lecturers_all_admin"
    ON public.course_lecturers FOR ALL
    USING (public.get_user_role() = 'ADMIN');





-- ============================================================
-- SCHEDULE_CONFIGS Policies
-- ============================================================

-- Giriş yapmış herkes görebilir
DROP POLICY IF EXISTS "schedule_configs_select_auth" ON public.schedule_configs;
CREATE POLICY "schedule_configs_select_auth"
    ON public.schedule_configs FOR SELECT
    TO authenticated
    USING (true);

-- Admin tam yetki
DROP POLICY IF EXISTS "schedule_configs_all_admin" ON public.schedule_configs;
CREATE POLICY "schedule_configs_all_admin"
    ON public.schedule_configs FOR ALL
    USING (public.get_user_role() = 'ADMIN');





-- ============================================================
-- SCHEDULE_ASSIGNMENTS Policies
-- ============================================================

-- Giriş yapmış herkes atamaları görebilir
DROP POLICY IF EXISTS "assignments_select_auth" ON public.schedule_assignments;
CREATE POLICY "assignments_select_auth"
    ON public.schedule_assignments FOR SELECT
    TO authenticated
    USING (true);

-- Admin tam yetki
DROP POLICY IF EXISTS "assignments_all_admin" ON public.schedule_assignments;
CREATE POLICY "assignments_all_admin"
    ON public.schedule_assignments FOR ALL
    USING (public.get_user_role() = 'ADMIN');





-- ============================================================
-- AVAILABILITY_SLOTS Policies
-- ============================================================

-- Giriş yapmış herkes görebilir
DROP POLICY IF EXISTS "availability_select_auth" ON public.availability_slots;
CREATE POLICY "availability_select_auth"
    ON public.availability_slots FOR SELECT
    TO authenticated
    USING (true);

-- Admin tam yetki
DROP POLICY IF EXISTS "availability_all_admin" ON public.availability_slots;
CREATE POLICY "availability_all_admin"
    ON public.availability_slots FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- Lecturer kendi müsaitliğini yönetebilir
DROP POLICY IF EXISTS "availability_insert_lecturer" ON public.availability_slots;
CREATE POLICY "availability_insert_lecturer"
    ON public.availability_slots FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id AND profile_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "availability_update_lecturer" ON public.availability_slots;
CREATE POLICY "availability_update_lecturer"
    ON public.availability_slots FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id AND profile_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "availability_delete_lecturer" ON public.availability_slots;
CREATE POLICY "availability_delete_lecturer"
    ON public.availability_slots FOR DELETE
    USING (
        EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id AND profile_id = auth.uid()
        )
    );



-- ============================================================
-- SCHEDULE_DRAFTS Policies
-- ============================================================

-- Admin tüm draft'ları görebilir
DROP POLICY IF EXISTS "drafts_select_admin" ON public.schedule_drafts;
CREATE POLICY "drafts_select_admin"
    ON public.schedule_drafts FOR SELECT
    USING (public.get_user_role() = 'ADMIN');

-- Admin tam yetki (onay/red)
DROP POLICY IF EXISTS "drafts_all_admin" ON public.schedule_drafts;
CREATE POLICY "drafts_all_admin"
    ON public.schedule_drafts FOR ALL
    USING (public.get_user_role() = 'ADMIN');









-- ============================================================
-- CHANGE_REQUESTS Policies
-- ============================================================

-- Lecturer kendi isteklerini görebilir
DROP POLICY IF EXISTS "requests_select_own" ON public.change_requests;
CREATE POLICY "requests_select_own"
    ON public.change_requests FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id AND profile_id = auth.uid()
        )
    );

-- Lecturer istek oluşturabilir
DROP POLICY IF EXISTS "requests_insert_lecturer" ON public.change_requests;
CREATE POLICY "requests_insert_lecturer"
    ON public.change_requests FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id AND profile_id = auth.uid()
        )
    );

-- Admin tüm istekleri görebilir ve yönetebilir
DROP POLICY IF EXISTS "requests_all_admin" ON public.change_requests;
CREATE POLICY "requests_all_admin"
    ON public.change_requests FOR ALL
    USING (public.get_user_role() = 'ADMIN');





-- ============================================================
-- IMPORT_LOGS Policies
-- ============================================================

-- Admin tüm import log'ları görebilir ve yönetebilir
DROP POLICY IF EXISTS "import_logs_all_admin" ON public.import_logs;
CREATE POLICY "import_logs_all_admin"
    ON public.import_logs FOR ALL
    USING (public.get_user_role() = 'ADMIN');


