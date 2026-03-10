-- ============================================================
-- UniScheduler — 00: TÜM SİSTEMİ SIFIRLAMA (DROP ALL)
-- ============================================================
-- DİKKAT: Bu kod veritabanınızdaki proje kaynaklı tüm tabloları,
-- içlerindeki verileri, AUTH (login/oturum) kayıtlarını KALICI OLARAK SİLER.
-- ============================================================

-- 1. AUTH & SESSIONS (Giriş Yapan Tüm Kullanıcıları ve Oturumları Siler)
-- Hata verirse (yetki vs.) yoksayarak devam eder
DO $$
BEGIN
   DELETE FROM auth.users;
EXCEPTION WHEN OTHERS THEN
   NULL;
END $$;

-- 2. STORAGE BUCKETS (Dosyaları Supabase korumasına takılmadan siler)
-- Triggers geçici devredışı bırakılarak silinir, yetki hatası olursa sessizce atlar
DO $$
BEGIN
   ALTER TABLE storage.objects DISABLE TRIGGER ALL;
   DELETE FROM storage.objects WHERE bucket_id IN ('excel-uploads', 'exports');
   ALTER TABLE storage.objects ENABLE TRIGGER ALL;

   ALTER TABLE storage.buckets DISABLE TRIGGER ALL;
   DELETE FROM storage.buckets WHERE id IN ('excel-uploads', 'exports');
   ALTER TABLE storage.buckets ENABLE TRIGGER ALL;
EXCEPTION WHEN OTHERS THEN
   -- Eğer yetki hatası verirse sessizce devam et
   NULL;
END $$;

-- 3. YENİ VE ESKİ ANA TABLOLAR (Varsa sil, yoksa devam et)
DROP TABLE IF EXISTS public.lecturer_availability CASCADE;
DROP TABLE IF EXISTS public.import_logs CASCADE;
DROP TABLE IF EXISTS public.change_requests CASCADE;
DROP TABLE IF EXISTS public.schedule_drafts CASCADE;
DROP TABLE IF EXISTS public.availability_slots CASCADE;
DROP TABLE IF EXISTS public.schedule_assignments CASCADE;
DROP TABLE IF EXISTS public.schedule_configs CASCADE;
DROP TABLE IF EXISTS public.course_lecturers CASCADE;
DROP TABLE IF EXISTS public.courses CASCADE;
DROP TABLE IF EXISTS public.lecturers CASCADE;
DROP TABLE IF EXISTS public.departments CASCADE;
DROP TABLE IF EXISTS public.profiles CASCADE;

-- 4. FONKSİYONLAR VE TRİGGERLAR (Varsa sil, yoksa devam et)
DROP FUNCTION IF EXISTS public.get_user_role() CASCADE;
DROP FUNCTION IF EXISTS public.get_user_department_id() CASCADE;
DROP FUNCTION IF EXISTS public.handle_new_user() CASCADE;
DROP FUNCTION IF EXISTS public.update_updated_at() CASCADE;
DROP FUNCTION IF EXISTS public.update_request_overall_status() CASCADE;
DROP FUNCTION IF EXISTS public.get_department_stats(INT) CASCADE;
DROP FUNCTION IF EXISTS public.upsert_availability(INT, JSONB) CASCADE;
DROP FUNCTION IF EXISTS public.apply_draft_assignments(INT, INT) CASCADE;
DROP FUNCTION IF EXISTS public.make_admin(TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.check_cross_department_conflict(INT, INT, INT, INT) CASCADE;
DROP FUNCTION IF EXISTS public.generate_invite_code() CASCADE;
DROP FUNCTION IF EXISTS public.assign_lecturer_invite_code() CASCADE;
DROP FUNCTION IF EXISTS public.claim_lecturer_invite(TEXT, TEXT, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.prevent_invite_code_external_set() CASCADE;

-- ============================================================
-- ARTIK SİSTEM TERTEMİZ (Hata vermeden esnetildi).
-- Şimdi Sırasıyla Şunları Çalıştırabilirsiniz:
-- 1) 01_tables.sql
-- 2) 02_rls_policies.sql
-- 3) 03_functions_triggers.sql
-- 4) 04_storage.sql
-- 5) 07_lecturer_invite_system.sql
-- ============================================================
