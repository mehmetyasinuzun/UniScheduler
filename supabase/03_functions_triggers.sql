-- ============================================================
-- UniScheduler — 03: Functions & Triggers
-- 02_rls_policies.sql ÇALIŞTIRILMADAN ÖNCE BU DOSYA ÇALIŞTIRILMAMALIDIR
-- ============================================================

-- ============================================================
-- 1. Auth Trigger: Yeni kullanıcı kayıt olunca otomatik profil oluştur
-- ============================================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, name, surname, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'name', ''),
        COALESCE(NEW.raw_user_meta_data->>'surname', ''),
        COALESCE(NEW.raw_user_meta_data->>'role', 'STUDENT')
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger'ı bağla (varsa öncekini sil)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user();

-- ============================================================
-- 2. Auto-update timestamp for schedule_configs
-- ============================================================
CREATE OR REPLACE FUNCTION public.update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_schedule_configs_updated ON public.schedule_configs;
CREATE TRIGGER trigger_schedule_configs_updated
    BEFORE UPDATE ON public.schedule_configs
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at();

-- ============================================================
-- 3. Dual-routing: change_requests overall status otomatik hesaplama
-- ============================================================
CREATE OR REPLACE FUNCTION public.update_request_overall_status()
RETURNS TRIGGER AS $$
BEGIN
    -- Herhangi biri reddetmişse → RED
    IF NEW.admin_status = 'REJECTED' OR NEW.dept_head_status = 'REJECTED' THEN
        NEW.status = 'REJECTED';

    -- Dual approval: ikisi de onaylamalı
    ELSIF NEW.approval_mode = 'DUAL_APPROVAL' THEN
        IF NEW.admin_status = 'APPROVED' AND NEW.dept_head_status = 'APPROVED' THEN
            NEW.status = 'APPROVED';
        END IF;

    -- Sadece Admin yeterli
    ELSIF NEW.approval_mode = 'ADMIN_ONLY' THEN
        IF NEW.admin_status = 'APPROVED' THEN
            NEW.status = 'APPROVED';
        END IF;

    -- Sadece Dept Head yeterli
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

-- ============================================================
-- 4. Bölüm istatistikleri (Home ekranında kullanılır)
-- ============================================================
CREATE OR REPLACE FUNCTION public.get_department_stats(dept_id INT)
RETURNS JSON AS $$
    SELECT json_build_object(
        'course_count',
            (SELECT COUNT(*) FROM public.courses WHERE department_id = dept_id),
        'lecturer_count',
            (SELECT COUNT(*) FROM public.lecturers WHERE department_id = dept_id),
        'assignment_count',
            (SELECT COUNT(*) FROM public.schedule_assignments sa
             JOIN public.courses c ON sa.course_id = c.id
             WHERE c.department_id = dept_id),
        'pending_draft_count',
            (SELECT COUNT(*) FROM public.schedule_drafts
             WHERE department_id = dept_id AND status = 'PENDING'),
        'pending_request_count',
            (SELECT COUNT(*) FROM public.change_requests cr
             JOIN public.lecturers l ON cr.lecturer_id = l.id
             WHERE l.department_id = dept_id AND cr.status = 'PENDING')
    );
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- ============================================================
-- 5. Toplu müsaitlik güncelleme (availability grid kaydetme)
-- ============================================================
CREATE OR REPLACE FUNCTION public.upsert_availability(
    p_lecturer_id INT,
    p_slots JSONB
)
RETURNS VOID AS $$
BEGIN
    -- Mevcut slot'ları temizle
    DELETE FROM public.availability_slots WHERE lecturer_id = p_lecturer_id;

    -- Yeni slot'ları ekle
    INSERT INTO public.availability_slots (lecturer_id, day_of_week, slot_index, is_available)
    SELECT
        p_lecturer_id,
        (slot->>'day_of_week')::INT,
        (slot->>'slot_index')::INT,
        (slot->>'is_available')::BOOLEAN
    FROM jsonb_array_elements(p_slots) AS slot;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================
-- 6. Draft atamaları canlı programa uygula (Admin onayı sonrası)
-- ============================================================
CREATE OR REPLACE FUNCTION public.apply_draft_assignments(
    p_draft_id INT,
    p_department_id INT
)
RETURNS VOID AS $$
DECLARE
    draft_data JSONB;
BEGIN
    -- Onaylanmış draft'ı al
    SELECT assignments INTO draft_data
    FROM public.schedule_drafts
    WHERE id = p_draft_id AND status = 'APPROVED';

    IF draft_data IS NULL THEN
        RAISE EXCEPTION 'Draft bulunamadı veya henüz onaylanmadı';
    END IF;

    -- Bölüme ait kilitli OLMAYAN atamaları temizle
    DELETE FROM public.schedule_assignments
    WHERE is_locked = false
      AND course_id IN (SELECT id FROM public.courses WHERE department_id = p_department_id);

    -- Draft'taki atamaları canlıya taşı
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

-- ============================================================
-- 7. Kullanıcıyı admin yap (ilk kurulum için)
-- ============================================================
CREATE OR REPLACE FUNCTION public.make_admin(user_email TEXT)
RETURNS VOID AS $$
    UPDATE public.profiles
    SET role = 'ADMIN'
    WHERE id = (SELECT id FROM auth.users WHERE email = user_email);
$$ LANGUAGE sql SECURITY DEFINER;

-- ============================================================
-- 8. Cross-department çakışma kontrolü
-- ============================================================
CREATE OR REPLACE FUNCTION public.check_cross_department_conflict(
    p_lecturer_id INT,
    p_day_of_week INT,
    p_slot_index INT,
    p_exclude_assignment_id INT DEFAULT NULL
)
RETURNS TABLE(
    conflict_course_code TEXT,
    conflict_course_name TEXT,
    conflict_department TEXT
) AS $$
    SELECT
        sa.course_code,
        sa.course_name,
        d.name AS department_name
    FROM public.schedule_assignments sa
    JOIN public.courses c ON sa.course_id = c.id
    JOIN public.departments d ON c.department_id = d.id
    WHERE sa.lecturer_id = p_lecturer_id
      AND sa.day_of_week = p_day_of_week
      AND sa.slot_index = p_slot_index
      AND (p_exclude_assignment_id IS NULL OR sa.id != p_exclude_assignment_id);
$$ LANGUAGE sql SECURITY DEFINER STABLE;
