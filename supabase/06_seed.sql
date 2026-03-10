-- ============================================================
-- UniScheduler — 06: İlk Admin + Öğrenci Kullanıcı Oluşturma (Seed)
-- TÜM önceki dosyalar çalıştırıldıktan SONRA kullanılır
-- ============================================================

-- ╔════════════════════════════════════════════════════════════╗
-- ║  ADMIN HESABI OLUŞTURMA                                    ║
-- ║                                                            ║
-- ║  1. Supabase Dashboard → Authentication → Users            ║
-- ║  2. "Add User" → Manual butonuna tıklayın                 ║
-- ║  3. Email: admin@unischeduler.local                        ║
-- ║     Password: (güçlü bir şifre belirleyin)                ║
-- ║     ✅ Auto Confirm User işaretli olsun                   ║
-- ║  4. Create User'a tıklayın                                ║
-- ║  5. Oluşan kullanıcının UUID'sini kopyalayın              ║
-- ║  6. Aşağıdaki SQL'de <ADMIN_USER_UUID> yerine yapıştırın  ║
-- ║  7. SQL Editor'de çalıştırın                              ║
-- ╚════════════════════════════════════════════════════════════╝

-- Admin profilini oluştur/güncelle
-- NOT: <ADMIN_USER_UUID> kısmını gerçek UUID ile değiştirin!
/*
INSERT INTO public.profiles (id, name, surname, role)
VALUES (
    '<ADMIN_USER_UUID>',  -- ← Supabase Auth'tan kopyaladığınız UUID
    'Admin',
    'User',
    'ADMIN'
)
ON CONFLICT (id) DO UPDATE SET role = 'ADMIN';
*/

-- ALTERNATIF: Email ile admin yapma (handle_new_user trigger'ı
-- otomatik profil oluşturduysa sadece rolü güncelle)
/*
SELECT public.make_admin('admin@unischeduler.local');
*/

-- ============================================================
-- ÖĞRENCİ DEMO HESABI OLUŞTURMA
-- ============================================================
-- Öğrenciler sistemi görüntülemek için bu hesabı kullanır.
-- Tek bir demo hesabı: tüm öğrenciler bu hesapla giriş yapar.
--
-- ADIMLAR:
-- 1. Supabase Dashboard → Authentication → Users
-- 2. "Add User" → Manual butonuna tıklayın
-- 3. Email:    ogrenci@unischeduler.local
--    Password: ogrenci123
--    ✅ Auto Confirm User işaretli olsun
-- 4. Create User'a tıklayın — trigger otomatik STUDENT profili yaratır
-- 5. Öğrencinin bölüm ID'sini atamak için aşağıdaki SQL'i çalıştırın:
/*
UPDATE public.profiles
SET
    name          = 'Öğrenci',
    surname       = 'Demo',
    role          = 'STUDENT',
    department_id = 1   -- ← Doğru bölüm ID'sini girin
WHERE id = (
    SELECT id FROM auth.users WHERE email = 'ogrenci@unischeduler.local'
);
*/

-- ============================================================
-- Opsiyonel: Test bölümü oluşturma
-- ============================================================
/*
INSERT INTO public.departments (name, code, dept_head_permission)
VALUES ('Bilgisayar Mühendisliği', 'CNG', 'APPROVAL_REQUIRED')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.departments (name, code, dept_head_permission)
VALUES ('Elektrik-Elektronik Mühendisliği', 'EEE', 'APPROVAL_REQUIRED')
ON CONFLICT (code) DO NOTHING;
*/

