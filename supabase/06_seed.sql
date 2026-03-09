-- ============================================================
-- UniScheduler — 06: İlk Admin Kullanıcı Oluşturma (Seed)
-- TÜM önceki dosyalar çalıştırıldıktan SONRA kullanılır
-- ============================================================

-- ╔════════════════════════════════════════════════════════════╗
-- ║  BU DOSYAYI DOĞRUDAN ÇALIŞTIRMAYIN!                      ║
-- ║                                                            ║
-- ║  Aşağıdaki adımları TAKİP EDİN:                           ║
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
