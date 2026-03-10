-- ============================================================
-- UniScheduler — 07: Lecturer Invite Code System
-- Hocalar sisteme unique davet koduyla kayıt olur
-- Bu dosyayı 06_seed.sql'den SONRA çalıştırın
-- ============================================================

-- ============================================================
-- 1. lecturers tablosuna invite_code sütunu ekle
-- ============================================================
ALTER TABLE public.lecturers
    ADD COLUMN IF NOT EXISTS invite_code TEXT UNIQUE;

COMMENT ON COLUMN public.lecturers.invite_code IS
    'Admin tarafından üretilen 8 haneli unique davet kodu — hoca bu kodla kayıt olur';

-- Unique index (büyük/küçük harf duyarsız arama için)
DROP INDEX IF EXISTS idx_lecturers_invite_code;
CREATE UNIQUE INDEX IF NOT EXISTS idx_lecturers_invite_code
    ON public.lecturers (UPPER(invite_code));

-- ============================================================
-- 2. Davet kodu üretici fonksiyon (8 haneli alfanümerik)
-- ============================================================
CREATE OR REPLACE FUNCTION public.generate_invite_code()
RETURNS TEXT AS $$
DECLARE
    chars TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; -- 0,1,I,O çıkarıldı (karıştırılmasın)
    code  TEXT := '';
    i     INT;
BEGIN
    FOR i IN 1..8 LOOP
        code := code || substr(chars, floor(random() * length(chars) + 1)::INT, 1);
    END LOOP;
    RETURN code;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- 3. Mevcut hocalar için otomatik kod üret (null olanlar)
-- ============================================================
DO $$
DECLARE
    rec RECORD;
    new_code TEXT;
    max_tries INT;
BEGIN
    FOR rec IN SELECT id FROM public.lecturers WHERE invite_code IS NULL LOOP
        max_tries := 0;
        LOOP
            new_code := public.generate_invite_code();
            BEGIN
                UPDATE public.lecturers SET invite_code = new_code WHERE id = rec.id;
                EXIT; -- başarılıysa çık
            EXCEPTION WHEN unique_violation THEN
                max_tries := max_tries + 1;
                IF max_tries > 10 THEN
                    RAISE EXCEPTION 'invite_code üretilemedi: id=%', rec.id;
                END IF;
            END;
        END LOOP;
    END LOOP;
END $$;

-- ============================================================
-- 4. Yeni hoça eklenince otomatik kod ata (trigger)
-- ============================================================
CREATE OR REPLACE FUNCTION public.assign_lecturer_invite_code()
RETURNS TRIGGER AS $$
DECLARE
    new_code TEXT;
    max_tries INT := 0;
BEGIN
    IF NEW.invite_code IS NOT NULL THEN
        RETURN NEW;
    END IF;

    LOOP
        new_code := public.generate_invite_code();
        BEGIN
            NEW.invite_code := new_code;
            RETURN NEW;
        EXCEPTION WHEN unique_violation THEN
            max_tries := max_tries + 1;
            IF max_tries > 10 THEN
                RAISE EXCEPTION 'invite_code üretilemedi';
            END IF;
        END;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_assign_invite_code ON public.lecturers;
CREATE TRIGGER trg_assign_invite_code
    BEFORE INSERT ON public.lecturers
    FOR EACH ROW
    EXECUTE FUNCTION public.assign_lecturer_invite_code();

-- ============================================================
-- 5. Davet koduyla hoca kaydı doğrulama + profil güncelleme
--    Client bu fonksiyonu SADECE KENDİ auth token'ıyla çağırır
--    Service role key GEREKMİYOR — pure DB işlemi
-- ============================================================
CREATE OR REPLACE FUNCTION public.claim_lecturer_invite(
    p_invite_code TEXT,
    p_name        TEXT,
    p_surname     TEXT
)
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_lecturer_id   INT;
    v_dept_id       INT;
    v_full_name     TEXT;
    v_profile_id    UUID;
BEGIN
    -- Mevcut oturum kontrolü
    IF auth.uid() IS NULL THEN
        RETURN json_build_object('success', false, 'error', 'Oturum açmanız gerekiyor');
    END IF;

    -- Profil zaten hoca mı?
    SELECT id INTO v_profile_id FROM public.profiles WHERE id = auth.uid() AND role = 'LECTURER';
    IF v_profile_id IS NOT NULL THEN
        RETURN json_build_object('success', false, 'error', 'Bu hesap zaten bir öğretim üyesi profiliyle ilişkilendirilmiş');
    END IF;

    -- Davet kodunu büyük harfe normalize ederek bul
    SELECT id, department_id, full_name
    INTO v_lecturer_id, v_dept_id, v_full_name
    FROM public.lecturers
    WHERE UPPER(invite_code) = UPPER(p_invite_code)
      AND profile_id IS NULL; -- Henüz claim edilmemiş

    IF v_lecturer_id IS NULL THEN
        RETURN json_build_object('success', false, 'error', 'Geçersiz veya kullanılmış davet kodu');
    END IF;

    -- Lecturer kaydına profile_id bağla
    UPDATE public.lecturers
    SET profile_id = auth.uid()
    WHERE id = v_lecturer_id;

    -- Profili LECTURER rolüyle güncelle
    UPDATE public.profiles
    SET
        name          = p_name,
        surname       = p_surname,
        role          = 'LECTURER',
        department_id = v_dept_id
    WHERE id = auth.uid();

    RETURN json_build_object(
        'success',      true,
        'lecturer_id',  v_lecturer_id,
        'department_id', v_dept_id,
        'full_name',    v_full_name
    );
END;
$$;

-- ============================================================
-- 5.1 Davet kodu doğrulama (signup oncesi on-kontrol)
-- ============================================================
CREATE OR REPLACE FUNCTION public.validate_lecturer_invite_code(
    p_invite_code TEXT
)
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_exists BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM public.lecturers
        WHERE UPPER(invite_code) = UPPER(TRIM(p_invite_code))
          AND profile_id IS NULL
    ) INTO v_exists;

    RETURN json_build_object('valid', v_exists);
END;
$$;

-- ============================================================
-- 6. Öğrenci sabit hesabı — email+şifre ile giriş
--    NOT: auth.users'a elle ekleme yapılamaz SQL'den.
--    Aşağıdaki komutları Supabase Dashboard → Auth → Users'dan
--    veya service role key ile yapın:
--
--    Email:    ogrenci@unischeduler.local
--    Şifre:    ogrenci123
--    Role:     STUDENT
--
--    Alternatif: Aşağıdaki helper'ı admin dashboard'dan çağırın.
-- ============================================================

-- Profil için hazır seed (auth.users ID'si bilindikten sonra çalıştırılacak)
-- REPLACE <STUDENT_AUTH_UUID> with actual UUID from auth.users after creating the account
-- INSERT INTO public.profiles (id, name, surname, role)
-- VALUES ('<STUDENT_AUTH_UUID>', 'Öğrenci', 'Demo', 'STUDENT')
-- ON CONFLICT (id) DO UPDATE SET role = 'STUDENT';

-- ============================================================
-- 7. invite_code için RLS — hoca kendi kodunu görebilmeli,
--    admin tüm kodları görebilmeli (mevcut lecturers policy'lerine ek)
-- ============================================================

-- Hoca kendi invite_code'unu görebilir (lecturers_select_own zaten var, extra policy gerekmez)
-- Admin zaten tam yetkili (lecturers_all_admin zaten var)

-- Güvenlik: invite_code client tarafından SET edilemesin
-- (Trigger zaten handle ediyor, ama ekstra güvence için)
CREATE OR REPLACE FUNCTION public.prevent_invite_code_external_set()
RETURNS TRIGGER AS $$
BEGIN
    -- Sadece sistem (SECURITY DEFINER fonksiyonları) değiştirebilir
    -- Client UPDATE ile invite_code'u değiştiremez
    IF TG_OP = 'UPDATE' AND OLD.invite_code IS NOT NULL AND NEW.invite_code != OLD.invite_code THEN
        IF public.get_user_role() != 'ADMIN' THEN
            NEW.invite_code := OLD.invite_code; -- değişikliği geri al
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_protect_invite_code ON public.lecturers;
CREATE TRIGGER trg_protect_invite_code
    BEFORE UPDATE ON public.lecturers
    FOR EACH ROW
    EXECUTE FUNCTION public.prevent_invite_code_external_set();

-- ============================================================
-- 8. RLS: Kimliği doğrulanmış kullanıcı, claim sırasında
--    kendi profilini LECTURER rolüne güncelleyebilmeli
--    (claim_lecturer_invite SECURITY DEFINER olduğu için bu politika
--     zaten gerekmiyor, ama ilerideki doğrudan update'ler için)
-- ============================================================

-- profiles_update_own zaten mevcut — claim_lecturer_invite SECURITY DEFINER
-- ile zaten bypass ediyor, ekstra policy gerekmez.

COMMENT ON FUNCTION public.claim_lecturer_invite IS
    'Davet koduyla hoca kaydını talep et — SECURITY DEFINER, client''dan güvenle çağrılabilir';

COMMENT ON FUNCTION public.validate_lecturer_invite_code IS
    'Signup oncesi davet kodunun claim edilebilir durumda olup olmadigini kontrol eder';
