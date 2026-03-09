-- UniScheduler SQL Migration: Storage Buckets
-- Run this AFTER 003_functions.sql

-- ============================================
-- 1. Create storage bucket for Excel files
-- ============================================
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'excel-files',
    'excel-files',
    false,
    10485760, -- 10MB limit
    ARRAY[
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/vnd.ms-excel',
        'application/octet-stream'
    ]
) ON CONFLICT (id) DO NOTHING;

-- ============================================
-- 2. Storage policies for excel-files bucket
-- ============================================

-- Admins can do anything
CREATE POLICY "Admins have full access to excel-files"
    ON storage.objects FOR ALL
    USING (
        bucket_id = 'excel-files'
        AND public.get_user_role() = 'ADMIN'
    );

-- Dept heads can upload files to their department folder
CREATE POLICY "Dept heads can upload excel files"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'excel-files'
        AND public.get_user_role() = 'DEPT_HEAD'
        AND (storage.foldername(name))[1] = public.get_user_department_id()::text
    );

-- Dept heads can view their department files
CREATE POLICY "Dept heads can view dept excel files"
    ON storage.objects FOR SELECT
    USING (
        bucket_id = 'excel-files'
        AND public.get_user_role() = 'DEPT_HEAD'
        AND (storage.foldername(name))[1] = public.get_user_department_id()::text
    );

-- Dept heads can delete their department files
CREATE POLICY "Dept heads can delete dept excel files"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'excel-files'
        AND public.get_user_role() = 'DEPT_HEAD'
        AND (storage.foldername(name))[1] = public.get_user_department_id()::text
    );

-- ============================================
-- 3. Create storage bucket for exports
-- ============================================
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'exports',
    'exports',
    false,
    20971520, -- 20MB limit
    ARRAY[
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/pdf',
        'application/octet-stream'
    ]
) ON CONFLICT (id) DO NOTHING;

-- Authenticated users can read their own exports
CREATE POLICY "Users can read own exports"
    ON storage.objects FOR SELECT
    USING (
        bucket_id = 'exports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- Authenticated users can upload their own exports
CREATE POLICY "Users can upload own exports"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'exports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- Authenticated users can delete their own exports
CREATE POLICY "Users can delete own exports"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'exports'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );
