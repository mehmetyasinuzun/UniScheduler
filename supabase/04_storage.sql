-- ============================================================
-- UniScheduler — 04: Storage Buckets
-- 03_functions_triggers.sql ÇALIŞTIRILMADAN ÖNCE BU DOSYA ÇALIŞTIRILMAMALIDIR
-- ============================================================

-- ============================================================
-- 1. Excel Uploads Bucket (import edilen dosyalar)
-- ============================================================
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'excel-uploads',
    'excel-uploads',
    false,
    10485760, -- 10 MB limit
    ARRAY[
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/vnd.ms-excel',
        'application/octet-stream'
    ]
) ON CONFLICT (id) DO NOTHING;

-- Admin: excel-uploads bucket'ında tam yetki
CREATE POLICY "excel_uploads_admin_all"
    ON storage.objects FOR ALL
    USING (
        bucket_id = 'excel-uploads'
        AND public.get_user_role() = 'ADMIN'
    );

-- Dept Head: kendi bölüm klasörüne yükleme
CREATE POLICY "excel_uploads_dept_head_insert"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'excel-uploads'
        AND public.get_user_role() = 'DEPT_HEAD'
        AND (storage.foldername(name))[1] = public.get_user_department_id()::text
    );

-- Dept Head: kendi bölüm klasöründen okuma
CREATE POLICY "excel_uploads_dept_head_select"
    ON storage.objects FOR SELECT
    USING (
        bucket_id = 'excel-uploads'
        AND public.get_user_role() = 'DEPT_HEAD'
        AND (storage.foldername(name))[1] = public.get_user_department_id()::text
    );

-- Dept Head: kendi bölüm klasöründen silme
CREATE POLICY "excel_uploads_dept_head_delete"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'excel-uploads'
        AND public.get_user_role() = 'DEPT_HEAD'
        AND (storage.foldername(name))[1] = public.get_user_department_id()::text
    );

-- ============================================================
-- 2. Exports Bucket (Excel/PDF export dosyaları)
-- ============================================================
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'exports',
    'exports',
    false,
    20971520, -- 20 MB limit
    ARRAY[
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/pdf',
        'application/octet-stream'
    ]
) ON CONFLICT (id) DO NOTHING;

-- Kullanıcı kendi export klasöründen okuma
CREATE POLICY "exports_select_own"
    ON storage.objects FOR SELECT
    USING (
        bucket_id = 'exports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- Kullanıcı kendi export klasörüne yükleme
CREATE POLICY "exports_insert_own"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'exports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- Kullanıcı kendi export klasöründen silme
CREATE POLICY "exports_delete_own"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'exports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );
