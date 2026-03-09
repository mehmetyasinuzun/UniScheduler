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
CREATE POLICY "profiles_select_own"
    ON public.profiles FOR SELECT
    USING (id = auth.uid());

-- Admin tüm profilleri görebilir
CREATE POLICY "profiles_select_admin"
    ON public.profiles FOR SELECT
    USING (public.get_user_role() = 'ADMIN');

-- Dept Head kendi bölümündeki profilleri görebilir
CREATE POLICY "profiles_select_dept_head"
    ON public.profiles FOR SELECT
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- Kullanıcı kendi profilini güncelleyebilir
CREATE POLICY "profiles_update_own"
    ON public.profiles FOR UPDATE
    USING (id = auth.uid());

-- Kullanıcı kendi profilini oluşturabilir (signup sonrası)
CREATE POLICY "profiles_insert_own"
    ON public.profiles FOR INSERT
    WITH CHECK (id = auth.uid());

-- Admin tüm profilleri yönetebilir
CREATE POLICY "profiles_all_admin"
    ON public.profiles FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- ============================================================
-- DEPARTMENTS Policies
-- ============================================================

-- Giriş yapmış herkes bölümleri görebilir
CREATE POLICY "departments_select_auth"
    ON public.departments FOR SELECT
    TO authenticated
    USING (true);

-- Admin bölüm yönetimi (insert/update/delete)
CREATE POLICY "departments_all_admin"
    ON public.departments FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- ============================================================
-- LECTURERS Policies
-- ============================================================

-- Lecturer kendi kaydını görebilir
CREATE POLICY "lecturers_select_own"
    ON public.lecturers FOR SELECT
    USING (profile_id = auth.uid());

-- Admin tüm hocaları görebilir
CREATE POLICY "lecturers_select_admin"
    ON public.lecturers FOR SELECT
    USING (public.get_user_role() = 'ADMIN');

-- Dept Head kendi bölümündeki hocaları görebilir
CREATE POLICY "lecturers_select_dept_head"
    ON public.lecturers FOR SELECT
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- Student kendi bölümündeki hocaları görebilir
CREATE POLICY "lecturers_select_student"
    ON public.lecturers FOR SELECT
    USING (
        public.get_user_role() = 'STUDENT'
        AND department_id = public.get_user_department_id()
    );

-- Admin tüm hocaları yönetebilir
CREATE POLICY "lecturers_all_admin"
    ON public.lecturers FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- Dept Head kendi bölümüne hoca ekleyebilir
CREATE POLICY "lecturers_insert_dept_head"
    ON public.lecturers FOR INSERT
    WITH CHECK (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- Dept Head kendi bölümündeki hocaları güncelleyebilir
CREATE POLICY "lecturers_update_dept_head"
    ON public.lecturers FOR UPDATE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- ============================================================
-- COURSES Policies
-- ============================================================

-- Giriş yapmış herkes dersleri görebilir
CREATE POLICY "courses_select_auth"
    ON public.courses FOR SELECT
    TO authenticated
    USING (true);

-- Admin tüm dersleri yönetebilir
CREATE POLICY "courses_all_admin"
    ON public.courses FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- Dept Head kendi bölümüne ders ekleyebilir
CREATE POLICY "courses_insert_dept_head"
    ON public.courses FOR INSERT
    WITH CHECK (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- Dept Head kendi bölümündeki dersleri güncelleyebilir
CREATE POLICY "courses_update_dept_head"
    ON public.courses FOR UPDATE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- Dept Head kendi bölümündeki dersleri silebilir
CREATE POLICY "courses_delete_dept_head"
    ON public.courses FOR DELETE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- ============================================================
-- COURSE_LECTURERS Policies
-- ============================================================

-- Giriş yapmış herkes görebilir
CREATE POLICY "course_lecturers_select_auth"
    ON public.course_lecturers FOR SELECT
    TO authenticated
    USING (true);

-- Admin tam yetki
CREATE POLICY "course_lecturers_all_admin"
    ON public.course_lecturers FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- Dept Head ekleyebilir
CREATE POLICY "course_lecturers_insert_dept_head"
    ON public.course_lecturers FOR INSERT
    WITH CHECK (public.get_user_role() = 'DEPT_HEAD');

-- Dept Head silebilir
CREATE POLICY "course_lecturers_delete_dept_head"
    ON public.course_lecturers FOR DELETE
    USING (public.get_user_role() = 'DEPT_HEAD');

-- ============================================================
-- SCHEDULE_CONFIGS Policies
-- ============================================================

-- Giriş yapmış herkes görebilir
CREATE POLICY "schedule_configs_select_auth"
    ON public.schedule_configs FOR SELECT
    TO authenticated
    USING (true);

-- Admin tam yetki
CREATE POLICY "schedule_configs_all_admin"
    ON public.schedule_configs FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- Dept Head kendi bölümünün config'ini ekleyebilir
CREATE POLICY "schedule_configs_insert_dept_head"
    ON public.schedule_configs FOR INSERT
    WITH CHECK (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- Dept Head kendi bölümünün config'ini güncelleyebilir
CREATE POLICY "schedule_configs_update_dept_head"
    ON public.schedule_configs FOR UPDATE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- ============================================================
-- SCHEDULE_ASSIGNMENTS Policies
-- ============================================================

-- Giriş yapmış herkes atamaları görebilir
CREATE POLICY "assignments_select_auth"
    ON public.schedule_assignments FOR SELECT
    TO authenticated
    USING (true);

-- Admin tam yetki
CREATE POLICY "assignments_all_admin"
    ON public.schedule_assignments FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- Dept Head (FULL_ACCESS izni varsa) kendi bölümüne atama yapabilir
CREATE POLICY "assignments_insert_dept_head"
    ON public.schedule_assignments FOR INSERT
    WITH CHECK (
        public.get_user_role() = 'DEPT_HEAD'
        AND EXISTS (
            SELECT 1 FROM public.departments d
            WHERE d.id = public.get_user_department_id()
            AND d.dept_head_permission = 'FULL_ACCESS'
        )
    );

CREATE POLICY "assignments_update_dept_head"
    ON public.schedule_assignments FOR UPDATE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND EXISTS (
            SELECT 1 FROM public.departments d
            WHERE d.id = public.get_user_department_id()
            AND d.dept_head_permission = 'FULL_ACCESS'
        )
    );

CREATE POLICY "assignments_delete_dept_head"
    ON public.schedule_assignments FOR DELETE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND EXISTS (
            SELECT 1 FROM public.departments d
            WHERE d.id = public.get_user_department_id()
            AND d.dept_head_permission = 'FULL_ACCESS'
        )
    );

-- ============================================================
-- AVAILABILITY_SLOTS Policies
-- ============================================================

-- Giriş yapmış herkes görebilir
CREATE POLICY "availability_select_auth"
    ON public.availability_slots FOR SELECT
    TO authenticated
    USING (true);

-- Admin tam yetki
CREATE POLICY "availability_all_admin"
    ON public.availability_slots FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- Lecturer kendi müsaitliğini yönetebilir
CREATE POLICY "availability_insert_lecturer"
    ON public.availability_slots FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id AND profile_id = auth.uid()
        )
    );

CREATE POLICY "availability_update_lecturer"
    ON public.availability_slots FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id AND profile_id = auth.uid()
        )
    );

CREATE POLICY "availability_delete_lecturer"
    ON public.availability_slots FOR DELETE
    USING (
        EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id AND profile_id = auth.uid()
        )
    );

-- Dept Head kendi bölümündeki müsaitlikleri yönetebilir
CREATE POLICY "availability_manage_dept_head"
    ON public.availability_slots FOR ALL
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id
            AND department_id = public.get_user_department_id()
        )
    );

-- ============================================================
-- SCHEDULE_DRAFTS Policies
-- ============================================================

-- Admin tüm draft'ları görebilir
CREATE POLICY "drafts_select_admin"
    ON public.schedule_drafts FOR SELECT
    USING (public.get_user_role() = 'ADMIN');

-- Admin tam yetki (onay/red)
CREATE POLICY "drafts_all_admin"
    ON public.schedule_drafts FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- Dept Head kendi bölümünün draft'larını görebilir
CREATE POLICY "drafts_select_dept_head"
    ON public.schedule_drafts FOR SELECT
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- Dept Head kendi bölümü için draft oluşturabilir
CREATE POLICY "drafts_insert_dept_head"
    ON public.schedule_drafts FOR INSERT
    WITH CHECK (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
        AND created_by = auth.uid()
    );

-- Dept Head kendi draft'larını güncelleyebilir (sadece DRAFT durumunda)
CREATE POLICY "drafts_update_dept_head"
    ON public.schedule_drafts FOR UPDATE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND created_by = auth.uid()
        AND status = 'DRAFT'
    );

-- Dept Head kendi DRAFT durumundaki taslakları silebilir
CREATE POLICY "drafts_delete_dept_head"
    ON public.schedule_drafts FOR DELETE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND created_by = auth.uid()
        AND status = 'DRAFT'
    );

-- ============================================================
-- CHANGE_REQUESTS Policies
-- ============================================================

-- Lecturer kendi isteklerini görebilir
CREATE POLICY "requests_select_own"
    ON public.change_requests FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id AND profile_id = auth.uid()
        )
    );

-- Lecturer istek oluşturabilir
CREATE POLICY "requests_insert_lecturer"
    ON public.change_requests FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id AND profile_id = auth.uid()
        )
    );

-- Admin tüm istekleri görebilir ve yönetebilir
CREATE POLICY "requests_all_admin"
    ON public.change_requests FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- Dept Head kendi bölümündeki istekleri görebilir
CREATE POLICY "requests_select_dept_head"
    ON public.change_requests FOR SELECT
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id
            AND department_id = public.get_user_department_id()
        )
    );

-- Dept Head kendi bölümündeki istekleri onaylayabilir/reddedebilir
CREATE POLICY "requests_update_dept_head"
    ON public.change_requests FOR UPDATE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND EXISTS (
            SELECT 1 FROM public.lecturers
            WHERE id = lecturer_id
            AND department_id = public.get_user_department_id()
        )
    );

-- ============================================================
-- IMPORT_LOGS Policies
-- ============================================================

-- Admin tüm import log'ları görebilir ve yönetebilir
CREATE POLICY "import_logs_all_admin"
    ON public.import_logs FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- Dept Head kendi bölümünün log'larını görebilir
CREATE POLICY "import_logs_select_dept_head"
    ON public.import_logs FOR SELECT
    USING (public.get_user_role() = 'DEPT_HEAD');
