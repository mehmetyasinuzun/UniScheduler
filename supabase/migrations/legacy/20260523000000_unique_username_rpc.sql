-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  Global Unique Username RPC                                              ║
-- ║                                                                          ║
-- ║  KÖK NEDEN — neden bu RPC gerekli?                                       ║
-- ║  ────────────────────────────────                                        ║
-- ║  Multi-tenant uygulamada lecturer kayıt akışı 3 katmanı kullanıyor:      ║
-- ║                                                                          ║
-- ║    public.users (RLS active, org-scoped)                                 ║
-- ║          ↓ username unique constraint                                    ║
-- ║    auth.users (global, no RLS)                                           ║
-- ║          ↓ email unique constraint                                       ║
-- ║                                                                          ║
-- ║  Eski mobile generateUniqueUsername() public.users'a anon_key ile        ║
-- ║  bakıyordu → RLS yüzünden başka org'un kullanıcılarını GÖRMÜYORDU.       ║
-- ║  "ahmet_yilmaz" Org 1'de varsa, Cumhuriyet admin'i bunu unique sanıp     ║
-- ║  signUpWith atınca Auth global email collision → "User already           ║
-- ║  registered" hatası → 32 satırlık import'tan 0 başarılı.                 ║
-- ║                                                                          ║
-- ║  ÇÖZÜM YAKLAŞIMI                                                          ║
-- ║  ────────────────                                                         ║
-- ║  SECURITY DEFINER fonksiyon → RLS bypass eder, postgres role'üyle        ║
-- ║  çalışır, hem public.users hem auth.users tablosunda kontrol yapar.      ║
-- ║  Mobile + panel her ikisi aynı RPC'yi çağırır → tek hakikat kaynağı.     ║
-- ║                                                                          ║
-- ║  Format: "Farhan Adl" → "farhan_adl" (ilk denemede)                      ║
-- ║          ikinci Farhan Adl → "farhan_adl2"                               ║
-- ║          üçüncü → "farhan_adl3" (sıralı, temiz, okunabilir)              ║
-- ║                                                                          ║
-- ║  Türkçe karakter normalizasyonu da fonksiyon içinde (ç→c, ğ→g, vb.).    ║
-- ║                                                                          ║
-- ║  GÜVENLİK NOTU                                                            ║
-- ║  ─────────────                                                            ║
-- ║  Fonksiyon SADECE okuma yapar — yazma yok. Anon role bile çağırsa         ║
-- ║  zarar veremez. Çıktı bir TEXT — gizli bilgi sızdırmıyor. Yine de         ║
-- ║  brute-force enumeration'a karşı: tek tek isim deneyerek "var/yok"       ║
-- ║  öğrenilebilir. Bu trade-off kabul edilebilir çünkü:                     ║
-- ║    (a) Username zaten public bilgi (login ekranında girilir)             ║
-- ║    (b) Brute-force yapan zaten kayıt deneyebilir                         ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- migrate:up

CREATE OR REPLACE FUNCTION public.generate_unique_lecturer_username(
    p_first_name TEXT,
    p_last_name  TEXT
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER          -- postgres role'üyle çalış, RLS atla
SET search_path = public, auth
AS $$
DECLARE
    base            TEXT;
    candidate       TEXT;
    candidate_email TEXT;
    suffix          INT := 2;
BEGIN
    -- ── Girdi doğrulama ────────────────────────────────────────────────
    IF p_first_name IS NULL OR p_last_name IS NULL THEN
        RAISE EXCEPTION 'First name and last name cannot be null';
    END IF;

    -- ── Türkçe karakter normalizasyonu + lowercase ────────────────────
    --    Farhan Âdl  →  farhan adl
    --    Şükran      →  sukran
    --    Çağrı Öz    →  cagri oz
    base := lower(translate(
        trim(p_first_name) || '_' || trim(p_last_name),
        'çğıöşüâîûçğıöşüÇĞIİÖŞÜÂÎÛ',
        'cgiosuaiucgiosuCGIIOSUAIU'
    ));

    -- Whitespace → underscore
    base := regexp_replace(base, '\s+', '_', 'g');
    -- Geriye sadece [a-z0-9_] kalsın
    base := regexp_replace(base, '[^a-z0-9_]', '', 'g');
    -- Ardışık underscore'ları teke indir
    base := regexp_replace(base, '_+', '_', 'g');
    -- Başındaki/sonundaki underscore'u temizle
    base := regexp_replace(base, '^_+|_+$', '', 'g');

    -- Boş/sadece underscore geliyorsa hata
    IF base = '' OR base IS NULL THEN
        RAISE EXCEPTION 'Cannot derive a valid username from "%" "%"', p_first_name, p_last_name;
    END IF;

    -- ── Unique kontrol döngüsü ────────────────────────────────────────
    -- Hem public.users (uygulama tablosu) hem auth.users (Supabase Auth)
    -- tablolarını kontrol eder. usernameToEmail mapping: '_' → '.'
    candidate       := base;
    candidate_email := replace(candidate, '_', '.') || '@unischeduler.app';

    WHILE EXISTS (SELECT 1 FROM public.users WHERE username = candidate)
       OR EXISTS (SELECT 1 FROM auth.users   WHERE lower(email) = candidate_email)
    LOOP
        candidate       := base || suffix::TEXT;
        candidate_email := replace(candidate, '_', '.') || '@unischeduler.app';
        suffix          := suffix + 1;

        -- Sonsuz döngü güvenlik kapısı — pratikte buraya kadar gelmek
        -- için 9998 aynı isimli kullanıcı olması lazım.
        IF suffix > 9999 THEN
            RAISE EXCEPTION 'Cannot generate unique username for "%_%" after 9999 attempts',
                p_first_name, p_last_name;
        END IF;
    END LOOP;

    RETURN candidate;
END;
$$;

-- Hem anon hem authenticated rol çağırabilir. SECURITY DEFINER zaten
-- postgres yetkisiyle çalıştığı için RLS atlanır; GRANT EXECUTE sadece
-- "kim çağırabilir" sorusunu cevaplıyor.
GRANT EXECUTE ON FUNCTION public.generate_unique_lecturer_username(TEXT, TEXT) TO anon, authenticated, service_role;

-- migrate:down

DROP FUNCTION IF EXISTS public.generate_unique_lecturer_username(TEXT, TEXT);
