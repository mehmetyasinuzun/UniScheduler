-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║ Fix: login_attempts anon INSERT policy                                   ║
-- ║                                                                          ║
-- ║ KÖK NEDEN                                                                 ║
-- ║ ─────────                                                                 ║
-- ║ Canlı projede login_attempts tablosunda RLS aktif, ancak anon-role        ║
-- ║ INSERT policy'si EKSİK. Sonuç: mobil uygulamadan başarısız login          ║
-- ║ denemeleri (JWT henüz alınmadığı için anon-role) 401 ile reddediliyor —   ║
-- ║ CTI dashboard'a hiç ulaşmıyor. Test:                                      ║
-- ║   POST /rest/v1/login_attempts   →  HTTP 401                              ║
-- ║   "new row violates row-level security policy for table login_attempts"  ║
-- ║                                                                          ║
-- ║ ÇÖZÜM                                                                     ║
-- ║ ─────                                                                     ║
-- ║ schema.sql:709 satırında zaten tanımlı olan policy'i live DB'ye           ║
-- ║ yansıtıyoruz. WITH CHECK (true) — başarısız login bile loglanabilsin      ║
-- ║ (spam riski: Supabase Auth katmanı 5/15dk IP throttle yapıyor + 90 gün    ║
-- ║ retention cron'u storage'ı bağlıyor).                                     ║
-- ║                                                                          ║
-- ║ NEDEN AUTH POLICY'Sİ DE EKLENİYOR                                         ║
-- ║ ────────────────────────────────                                          ║
-- ║ Başarılı login path'inde mobil JWT ile postgrest'e gidiyor (authenticated ║
-- ║ rolü). Aynı policy schema.sql:712'de tanımlı; live DB'de varsa CREATE     ║
-- ║ IF NOT EXISTS ile no-op olur.                                            ║
-- ║                                                                          ║
-- ║ NASIL ÇALIŞTIRILIR                                                        ║
-- ║ ─────────────────                                                         ║
-- ║   A) Supabase Dashboard → SQL Editor → bu dosyanın içeriğini yapıştır →   ║
-- ║      Run                                                                  ║
-- ║   B) dbmate ile:                                                         ║
-- ║      DATABASE_URL="postgresql://..." dbmate up                            ║
-- ║   C) psql:                                                               ║
-- ║      psql "$DATABASE_URL" -f 20260522000000_fix_login_attempts_rls.sql    ║
-- ║                                                                          ║
-- ║ DOĞRULAMA                                                                 ║
-- ║ ─────────                                                                 ║
-- ║   SELECT polname FROM pg_policy                                          ║
-- ║   WHERE polrelid = 'public.login_attempts'::regclass;                    ║
-- ║   → login_attempts_self_select                                            ║
-- ║   → login_attempts_anon_insert    ← OLMASI GEREKEN                       ║
-- ║   → login_attempts_auth_insert    ← OLMASI GEREKEN                       ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- migrate:up

-- Eksikse ekle, varsa dokunma — re-run safe.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policy
        WHERE polrelid = 'public.login_attempts'::regclass
          AND polname  = 'login_attempts_anon_insert'
    ) THEN
        CREATE POLICY login_attempts_anon_insert ON public.login_attempts
            FOR INSERT TO anon
            WITH CHECK (true);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policy
        WHERE polrelid = 'public.login_attempts'::regclass
          AND polname  = 'login_attempts_auth_insert'
    ) THEN
        CREATE POLICY login_attempts_auth_insert ON public.login_attempts
            FOR INSERT TO authenticated
            WITH CHECK (true);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policy
        WHERE polrelid = 'public.login_attempts'::regclass
          AND polname  = 'login_attempts_self_select'
    ) THEN
        CREATE POLICY login_attempts_self_select ON public.login_attempts
            FOR SELECT TO authenticated
            USING (
                username = (SELECT username FROM public.users WHERE id = auth.uid())
            );
    END IF;
END
$$;

-- migrate:down

-- Geri alınamaz: bu policy'ler kaldırılırsa mobil CTI tekrar sessizleşir.
-- Manuel rollback gerekirse:
--   DROP POLICY IF EXISTS login_attempts_anon_insert ON public.login_attempts;
--   DROP POLICY IF EXISTS login_attempts_auth_insert ON public.login_attempts;
--   DROP POLICY IF EXISTS login_attempts_self_select ON public.login_attempts;
