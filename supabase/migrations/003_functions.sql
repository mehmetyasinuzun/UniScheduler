-- UniScheduler SQL Migration: Functions & Triggers
-- Run this AFTER 002_rls_policies.sql

-- ============================================
-- 1. Auto-create profile on signup
-- ============================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, name, surname, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'name', ''),
        COALESCE(NEW.raw_user_meta_data->>'surname', ''),
        COALESCE(NEW.raw_user_meta_data->>'role', 'STUDENT')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Drop existing trigger if exists, then create
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user();

-- ============================================
-- 2. Auto-update timestamp for schedule_config
-- ============================================
CREATE OR REPLACE FUNCTION public.update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_schedule_config_updated ON public.schedule_config;
CREATE TRIGGER trigger_schedule_config_updated
    BEFORE UPDATE ON public.schedule_config
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at();

-- ============================================
-- 3. Get department schedule stats
-- ============================================
CREATE OR REPLACE FUNCTION public.get_department_stats(dept_id INT)
RETURNS JSON AS $$
    SELECT json_build_object(
        'course_count', (SELECT COUNT(*) FROM public.courses WHERE department_id = dept_id),
        'lecturer_count', (SELECT COUNT(*) FROM public.lecturers WHERE department_id = dept_id),
        'assignment_count', (SELECT COUNT(*) FROM public.schedule_assignments sa
            JOIN public.courses c ON sa.course_id = c.id
            WHERE c.department_id = dept_id),
        'pending_draft_count', (SELECT COUNT(*) FROM public.schedule_drafts
            WHERE department_id = dept_id AND status = 'PENDING'),
        'pending_request_count', (SELECT COUNT(*) FROM public.change_requests cr
            JOIN public.lecturers l ON cr.lecturer_id = l.id
            WHERE l.department_id = dept_id AND cr.status = 'PENDING')
    );
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- ============================================
-- 4. Bulk upsert availability slots
-- ============================================
CREATE OR REPLACE FUNCTION public.upsert_availability(
    p_lecturer_id INT,
    p_slots JSONB
)
RETURNS VOID AS $$
BEGIN
    -- Delete existing slots for this lecturer
    DELETE FROM public.availability_slots WHERE lecturer_id = p_lecturer_id;

    -- Insert new slots
    INSERT INTO public.availability_slots (lecturer_id, day_of_week, slot_index, is_available)
    SELECT
        p_lecturer_id,
        (slot->>'day_of_week')::INT,
        (slot->>'slot_index')::INT,
        (slot->>'is_available')::BOOLEAN
    FROM jsonb_array_elements(p_slots) AS slot;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================
-- 5. Apply approved draft to assignments
-- ============================================
CREATE OR REPLACE FUNCTION public.apply_draft_assignments(
    p_draft_id INT,
    p_department_id INT
)
RETURNS VOID AS $$
DECLARE
    draft_data JSONB;
BEGIN
    -- Get draft assignments
    SELECT assignments INTO draft_data
    FROM public.schedule_drafts
    WHERE id = p_draft_id AND status = 'APPROVED';

    IF draft_data IS NULL THEN
        RAISE EXCEPTION 'Draft not found or not approved';
    END IF;

    -- Delete existing non-locked assignments for the department
    DELETE FROM public.schedule_assignments
    WHERE is_locked = false
    AND course_id IN (SELECT id FROM public.courses WHERE department_id = p_department_id);

    -- Insert new assignments from draft
    INSERT INTO public.schedule_assignments (
        course_id, course_code, course_name,
        lecturer_id, lecturer_name,
        day_of_week, slot_index, classroom, semester, is_locked
    )
    SELECT
        (a->>'course_id')::INT,
        COALESCE(a->>'course_code', ''),
        COALESCE(a->>'course_name', ''),
        (a->>'lecturer_id')::INT,
        COALESCE(a->>'lecturer_name', ''),
        (a->>'day_of_week')::INT,
        (a->>'slot_index')::INT,
        COALESCE(a->>'classroom', ''),
        COALESCE(a->>'semester', ''),
        COALESCE((a->>'is_locked')::BOOLEAN, false)
    FROM jsonb_array_elements(draft_data) AS a;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================
-- 6. Update request status with dual-approval logic
-- ============================================
CREATE OR REPLACE FUNCTION public.update_request_overall_status()
RETURNS TRIGGER AS $$
BEGIN
    -- If either rejected, overall is REJECTED
    IF NEW.admin_status = 'REJECTED' OR NEW.dept_head_status = 'REJECTED' THEN
        NEW.status = 'REJECTED';
    -- Dual approval: both must approve
    ELSIF NEW.approval_mode = 'DUAL_APPROVAL' THEN
        IF NEW.admin_status = 'APPROVED' AND NEW.dept_head_status = 'APPROVED' THEN
            NEW.status = 'APPROVED';
        END IF;
    -- Admin only
    ELSIF NEW.approval_mode = 'ADMIN_ONLY' THEN
        IF NEW.admin_status = 'APPROVED' THEN
            NEW.status = 'APPROVED';
        END IF;
    -- Dept head only
    ELSIF NEW.approval_mode = 'DEPT_HEAD_ONLY' THEN
        IF NEW.dept_head_status = 'APPROVED' THEN
            NEW.status = 'APPROVED';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_request_status ON public.change_requests;
CREATE TRIGGER trigger_request_status
    BEFORE UPDATE ON public.change_requests
    FOR EACH ROW
    EXECUTE FUNCTION public.update_request_overall_status();

-- ============================================
-- 7. Seed initial admin user function
-- ============================================
CREATE OR REPLACE FUNCTION public.make_admin(user_email TEXT)
RETURNS VOID AS $$
    UPDATE public.profiles
    SET role = 'ADMIN'
    WHERE id = (SELECT id FROM auth.users WHERE email = user_email);
$$ LANGUAGE sql SECURITY DEFINER;
