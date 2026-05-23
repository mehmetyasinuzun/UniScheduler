-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  Secure username RPC — anon enumeration kapanır                          ║
-- ║                                                                          ║
-- ║  ÖNCEKİ DURUM (20260523000000)                                           ║
-- ║  ─────────────────────────────                                            ║
-- ║  RPC anon role'e açıktı. Hiçbir hesabı olmayan biri (sadece public       ║
-- ║  anon_key'i bilen) şu istekle sistemin kullanıcılarını numaralayabilirdi: ║
-- ║                                                                          ║
-- ║    curl -X POST -H "apikey: ANON_KEY" \                                  ║
-- ║      -d '{"p_first_name":"Mehmet","p_last_name":"Yılmaz"}' \             ║
-- ║      $URL/rest/v1/rpc/generate_unique_lecturer_username                  ║
-- ║                                                                          ║
-- ║    Yanıt 'mehmet_yilmaz'   → "Sistemde yok"                              ║
-- ║    Yanıt 'mehmet_yilmaz2'  → "Sistemde 1 var"                            ║
-- ║                                                                          ║
-- ║  Türkçe yaygın ad+soyad sözlüğüyle birkaç dakikada **uygulamayı          ║
-- ║  kimlerin kullandığı çıkarılabilirdi**.                                  ║
-- ║                                                                          ║
-- ║  BU MİGRATİON                                                             ║
-- ║  ───────────                                                              ║
-- ║  1. anon GRANT'ı geri çekilir → giriş yapmamış kullanıcılar 403 alır     ║
-- ║  2. Fonksiyon içinde iki yollu yetki kontrolü:                           ║
-- ║       (a) auth.role() = 'service_role'  → panel/backend, geçer            ║
-- ║       (b) authenticated + users.role='admin' → mobile admin, geçer       ║
-- ║       (c) diğer her şey                  → EXCEPTION                     ║
-- ║  3. 30ms sabit gecikme — timing attack engelleme                         ║
-- ║       (50ms önerilmişti, 30ms'e indirildi: 350 hoca import için ek       ║
-- ║       sadece +10.5 saniye, admin'i yavaşlatmaz)                          ║
-- ║  4. Başarılı her çağrı audit_log'a yazılır — anomali tespiti için.       ║
-- ║       Rate-limit ENGELLEMESI yok (Auth zaten 30 req/dk limitliyor;       ║
-- ║       çift kısıtlama anlamsız ve sadece admin'i yavaşlatır).             ║
-- ║                                                                          ║
-- ║  NEDEN RATE-LIMIT YOK?                                                    ║
-- ║  ─────────────────────                                                    ║
-- ║  Saldırgan zaten RPC'yi çağıramaz (anon GRANT yok). Geriye sadece şu     ║
-- ║  vektör kalır: admin hesabı çalmış saldırgan. Bu durumda zaten oyun      ║
-- ║  bitmiş — RPC rate-limit'i kurtarmaz, ama her gün 350+ hoca yöneten      ║
-- ║  gerçek admin'i yavaşlatır. audit_log ile anomali tespit ederiz.         ║
-- ║                                                                          ║
-- ║  ÖZET                                                                    ║
-- ║  ────                                                                    ║
-- ║  • Mobile admin              → ✅ aynen çalışır (authenticated + admin)  ║
-- ║  • Panel super-admin         → ✅ aynen çalışır (service_role bypass)    ║
-- ║  • Mobile lecturer JWT       → ❌ RAISE EXCEPTION                        ║
-- ║  • Anon (giriş yapmamış)     → ❌ HTTP 403 permission denied             ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- migrate:up

-- 1) anon yetkisini kaldır. service_role + authenticated kalır.
REVOKE EXECUTE ON FUNCTION public.generate_unique_lecturer_username(TEXT, TEXT) FROM anon;

-- 2) Fonksiyonu güvenlik kontrolleriyle yeniden yarat.
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
    v_caller_role   TEXT;
    v_caller_uid    UUID;
    v_is_authorized BOOLEAN := FALSE;
BEGIN
    -- ── 1) YETKİLENDİRME ──────────────────────────────────────────────
    --
    -- İki yollu giriş:
    --   (a) service_role (panel/backend) — koşulsuz geçer
    --   (b) authenticated + public.users.role = 'admin'
    --
    -- Lecturer rol'üyle gelen giriş veya anon (zaten GRANT yok) reddedilir.
    v_caller_role := auth.role();
    v_caller_uid  := auth.uid();

    IF v_caller_role = 'service_role' THEN
        v_is_authorized := TRUE;
    ELSIF v_caller_role = 'authenticated' AND v_caller_uid IS NOT NULL THEN
        SELECT EXISTS (
            SELECT 1 FROM public.users
            WHERE id = v_caller_uid
              AND role = 'admin'
              AND is_active = TRUE
        ) INTO v_is_authorized;
    END IF;

    IF NOT v_is_authorized THEN
        -- Timing-aware reject: yetki denetiminden GEÇMEDİYSE de aynı süre
        -- gecikmesi uygula. Saldırgan "yetki vermedi" ile "yetki verdi ama
        -- sonuç bulunamadı" arasında zaman farkı göremesin.
        PERFORM pg_sleep(0.03);
        RAISE EXCEPTION 'Yetkisiz: bu işlem sadece yönetici hesaplarına açıktır.'
            USING ERRCODE = 'insufficient_privilege';
    END IF;

    -- ── 2) TIMING ATTACK KORUMASI ─────────────────────────────────────
    --
    -- Yanıt süresi sabit ~30ms kalsın diye. Loop iterations 1 vs 9 olunca
    -- yanıt 5ms → 15ms değişirdi; saldırgan (eğer yetki çalmış olsa bile)
    -- bu farktan "kaç kullanıcı var" çıkarımı yapabilirdi. Sabit gecikme
    -- ile bu kanal kapatılır.
    PERFORM pg_sleep(0.03);

    -- ── 3) GİRDİ DOĞRULAMA ────────────────────────────────────────────
    IF p_first_name IS NULL OR p_last_name IS NULL THEN
        RAISE EXCEPTION 'First name and last name cannot be null';
    END IF;

    -- ── 4) TÜRKÇE NORMALİZASYON + lowercase ──────────────────────────
    --    "Farhan Âdl"     → "farhan adl"     → "farhan_adl"
    --    "Şükran Çağrı"   → "sukran cagri"   → "sukran_cagri"
    --    "Öztürk"         → "ozturk"
    base := lower(translate(
        trim(p_first_name) || '_' || trim(p_last_name),
        'çğıöşüâîûçğıöşüÇĞIİÖŞÜÂÎÛ',
        'cgiosuaiucgiosuCGIIOSUAIU'
    ));
    base := regexp_replace(base, '\s+', '_', 'g');
    base := regexp_replace(base, '[^a-z0-9_]', '', 'g');
    base := regexp_replace(base, '_+', '_', 'g');
    base := regexp_replace(base, '^_+|_+$', '', 'g');

    IF base = '' OR base IS NULL THEN
        RAISE EXCEPTION 'Cannot derive a valid username from "%" "%"', p_first_name, p_last_name;
    END IF;

    -- ── 5) UNIQUE KONTROL DÖNGÜSÜ ─────────────────────────────────────
    -- Hem public.users hem auth.users tablosunu kontrol eder.
    -- usernameToEmail mapping: '_' → '.'
    candidate       := base;
    candidate_email := replace(candidate, '_', '.') || '@unischeduler.app';

    WHILE EXISTS (SELECT 1 FROM public.users WHERE username = candidate)
       OR EXISTS (SELECT 1 FROM auth.users   WHERE lower(email) = candidate_email)
    LOOP
        candidate       := base || suffix::TEXT;
        candidate_email := replace(candidate, '_', '.') || '@unischeduler.app';
        suffix          := suffix + 1;
        IF suffix > 9999 THEN
            RAISE EXCEPTION 'Cannot generate unique username for "%_%" after 9999 attempts',
                p_first_name, p_last_name;
        END IF;
    END LOOP;

    -- ── 6) AUDIT LOG — anomali tespiti ────────────────────────────────
    -- Her başarılı çağrı yazılır; saatlik 500+ çağrı = anomali sinyali.
    -- audit_log tablosu zaten schema'da var (audit_trigger fonksiyonu
    -- tarafından da kullanılıyor). new_data jsonb ile minimal payload.
    BEGIN
        INSERT INTO public.audit_log (
            actor_id, actor_role, table_name, record_id, operation, new_data
        ) VALUES (
            v_caller_uid,
            COALESCE(v_caller_role, 'unknown'),
            'username.generate',
            candidate,
            'INSERT',
            jsonb_build_object(
                'first_name', p_first_name,
                'last_name',  p_last_name,
                'result',     candidate
            )
        );
    EXCEPTION WHEN OTHERS THEN
        -- Audit write fail OLURSA ana akışı bozma. Loglanır ama RPC yine
        -- başarılı döner — kullanıcının lecturer eklemesini engellemez.
        RAISE WARNING 'audit_log insert failed: %', SQLERRM;
    END;

    RETURN candidate;
END;
$$;

-- 3) Yetki: sadece authenticated + service_role. anon dışlanır.
-- (CREATE OR REPLACE GRANT'ları korur ama emniyet için tekrar belirtiyoruz.)
GRANT EXECUTE ON FUNCTION public.generate_unique_lecturer_username(TEXT, TEXT) TO authenticated, service_role;
-- anon yetkisi: yukarıdaki REVOKE ile zaten kaldırıldı.

-- migrate:down

-- Önceki sürüme dön: anon dahil herkese aç (önerilmez — yalnızca
-- migration sisteminin tersine çevrilebilir olması için).
GRANT EXECUTE ON FUNCTION public.generate_unique_lecturer_username(TEXT, TEXT) TO anon, authenticated, service_role;
-- Fonksiyon body'sinin önceki sürümünü 20260523000000_unique_username_rpc.sql
-- dosyasından yeniden uygulamak gerekirse, o dosyayı yeniden çalıştırın.
