-- UniScheduler SQL Migration: Row Level Security Policies
-- Run this AFTER 001_create_tables.sql

-- Enable RLS on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.departments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lecturers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.courses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.course_lecturers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedule_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedule_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.availability_slots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedule_drafts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.change_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.import_logs ENABLE ROW LEVEL SECURITY;

-- ============================================
-- Helper function: Get current user's role
-- ============================================
CREATE OR REPLACE FUNCTION public.get_user_role()
RETURNS TEXT AS $$
    SELECT role FROM public.profiles WHERE id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- Helper function: Get current user's department_id
CREATE OR REPLACE FUNCTION public.get_user_department_id()
RETURNS INT AS $$
    SELECT department_id FROM public.profiles WHERE id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- ============================================
-- PROFILES
-- ============================================
CREATE POLICY "Users can view their own profile"
    ON public.profiles FOR SELECT
    USING (id = auth.uid());

CREATE POLICY "Admins can view all profiles"
    ON public.profiles FOR SELECT
    USING (public.get_user_role() = 'ADMIN');

CREATE POLICY "Dept heads can view dept profiles"
    ON public.profiles FOR SELECT
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

CREATE POLICY "Users can update their own profile"
    ON public.profiles FOR UPDATE
    USING (id = auth.uid());

CREATE POLICY "Admins can manage all profiles"
    ON public.profiles FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- ============================================
-- DEPARTMENTS
-- ============================================
CREATE POLICY "Anyone authenticated can view departments"
    ON public.departments FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Admins can manage departments"
    ON public.departments FOR ALL
    USING (public.get_user_role() = 'ADMIN');

-- ============================================
-- LECTURERS
-- ============================================
CREATE POLICY "Lecturers can view their own record"
    ON public.lecturers FOR SELECT
    USING (profile_id = auth.uid());

CREATE POLICY "Admins can view all lecturers"
    ON public.lecturers FOR SELECT
    USING (public.get_user_role() = 'ADMIN');

CREATE POLICY "Dept heads can view dept lecturers"
    ON public.lecturers FOR SELECT
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

CREATE POLICY "Students can view lecturers in their dept"
    ON public.lecturers FOR SELECT
    USING (
        public.get_user_role() = 'STUDENT'
        AND department_id = public.get_user_department_id()
    );

CREATE POLICY "Admins can manage all lecturers"
    ON public.lecturers FOR ALL
    USING (public.get_user_role() = 'ADMIN');

CREATE POLICY "Dept heads can manage dept lecturers"
    ON public.lecturers FOR INSERT
    WITH CHECK (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

CREATE POLICY "Dept heads can update dept lecturers"
    ON public.lecturers FOR UPDATE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- ============================================
-- COURSES
-- ============================================
CREATE POLICY "Authenticated users can view courses"
    ON public.courses FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Admins can manage all courses"
    ON public.courses FOR ALL
    USING (public.get_user_role() = 'ADMIN');

CREATE POLICY "Dept heads can manage dept courses"
    ON public.courses FOR INSERT
    WITH CHECK (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

CREATE POLICY "Dept heads can update dept courses"
    ON public.courses FOR UPDATE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

CREATE POLICY "Dept heads can delete dept courses"
    ON public.courses FOR DELETE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- ============================================
-- COURSE_LECTURERS
-- ============================================
CREATE POLICY "Authenticated users can view course_lecturers"
    ON public.course_lecturers FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Admins can manage course_lecturers"
    ON public.course_lecturers FOR ALL
    USING (public.get_user_role() = 'ADMIN');

CREATE POLICY "Dept heads can manage course_lecturers"
    ON public.course_lecturers FOR INSERT
    WITH CHECK (public.get_user_role() = 'DEPT_HEAD');

CREATE POLICY "Dept heads can delete course_lecturers"
    ON public.course_lecturers FOR DELETE
    USING (public.get_user_role() = 'DEPT_HEAD');

-- ============================================
-- SCHEDULE_CONFIG
-- ============================================
CREATE POLICY "Authenticated users can view config"
    ON public.schedule_config FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Admins can manage config"
    ON public.schedule_config FOR ALL
    USING (public.get_user_role() = 'ADMIN');

CREATE POLICY "Dept heads can manage dept config"
    ON public.schedule_config FOR INSERT
    WITH CHECK (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

CREATE POLICY "Dept heads can update dept config"
    ON public.schedule_config FOR UPDATE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

-- ============================================
-- SCHEDULE_ASSIGNMENTS
-- ============================================
CREATE POLICY "Authenticated users can view assignments"
    ON public.schedule_assignments FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Admins can manage all assignments"
    ON public.schedule_assignments FOR ALL
    USING (public.get_user_role() = 'ADMIN');

CREATE POLICY "Dept heads can insert assignments"
    ON public.schedule_assignments FOR INSERT
    WITH CHECK (public.get_user_role() = 'DEPT_HEAD');

CREATE POLICY "Dept heads can update assignments"
    ON public.schedule_assignments FOR UPDATE
    USING (public.get_user_role() = 'DEPT_HEAD');

CREATE POLICY "Dept heads can delete assignments"
    ON public.schedule_assignments FOR DELETE
    USING (public.get_user_role() = 'DEPT_HEAD');

-- ============================================
-- AVAILABILITY_SLOTS
-- ============================================
CREATE POLICY "Lecturers can view their own availability"
    ON public.availability_slots FOR SELECT
    USING (
        lecturer_id IN (
            SELECT id FROM public.lecturers WHERE profile_id = auth.uid()
        )
    );

CREATE POLICY "Admins can view all availability"
    ON public.availability_slots FOR SELECT
    USING (public.get_user_role() = 'ADMIN');

CREATE POLICY "Dept heads can view dept availability"
    ON public.availability_slots FOR SELECT
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND lecturer_id IN (
            SELECT id FROM public.lecturers
            WHERE department_id = public.get_user_department_id()
        )
    );

CREATE POLICY "Lecturers can manage their own availability"
    ON public.availability_slots FOR INSERT
    WITH CHECK (
        lecturer_id IN (
            SELECT id FROM public.lecturers WHERE profile_id = auth.uid()
        )
    );

CREATE POLICY "Lecturers can update their own availability"
    ON public.availability_slots FOR UPDATE
    USING (
        lecturer_id IN (
            SELECT id FROM public.lecturers WHERE profile_id = auth.uid()
        )
    );

CREATE POLICY "Lecturers can delete their own availability"
    ON public.availability_slots FOR DELETE
    USING (
        lecturer_id IN (
            SELECT id FROM public.lecturers WHERE profile_id = auth.uid()
        )
    );

-- ============================================
-- SCHEDULE_DRAFTS
-- ============================================
CREATE POLICY "Admins can manage all drafts"
    ON public.schedule_drafts FOR ALL
    USING (public.get_user_role() = 'ADMIN');

CREATE POLICY "Dept heads can view their dept drafts"
    ON public.schedule_drafts FOR SELECT
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

CREATE POLICY "Dept heads can create drafts"
    ON public.schedule_drafts FOR INSERT
    WITH CHECK (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
        AND created_by = auth.uid()
    );

CREATE POLICY "Dept heads can update their drafts"
    ON public.schedule_drafts FOR UPDATE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
        AND created_by = auth.uid()
    );

-- ============================================
-- CHANGE_REQUESTS
-- ============================================
CREATE POLICY "Admins can manage all change requests"
    ON public.change_requests FOR ALL
    USING (public.get_user_role() = 'ADMIN');

CREATE POLICY "Dept heads can view dept change requests"
    ON public.change_requests FOR SELECT
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND lecturer_id IN (
            SELECT id FROM public.lecturers
            WHERE department_id = public.get_user_department_id()
        )
    );

CREATE POLICY "Dept heads can update dept change requests"
    ON public.change_requests FOR UPDATE
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND lecturer_id IN (
            SELECT id FROM public.lecturers
            WHERE department_id = public.get_user_department_id()
        )
    );

CREATE POLICY "Lecturers can view their own requests"
    ON public.change_requests FOR SELECT
    USING (
        lecturer_id IN (
            SELECT id FROM public.lecturers WHERE profile_id = auth.uid()
        )
    );

CREATE POLICY "Lecturers can create requests"
    ON public.change_requests FOR INSERT
    WITH CHECK (
        lecturer_id IN (
            SELECT id FROM public.lecturers WHERE profile_id = auth.uid()
        )
    );

-- ============================================
-- IMPORT_LOGS
-- ============================================
CREATE POLICY "Admins can manage all import logs"
    ON public.import_logs FOR ALL
    USING (public.get_user_role() = 'ADMIN');

CREATE POLICY "Dept heads can view dept import logs"
    ON public.import_logs FOR SELECT
    USING (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );

CREATE POLICY "Dept heads can create import logs"
    ON public.import_logs FOR INSERT
    WITH CHECK (
        public.get_user_role() = 'DEPT_HEAD'
        AND department_id = public.get_user_department_id()
    );
