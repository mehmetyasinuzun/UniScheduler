-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  Block Rules — kullanıcı / cihaz / IP bazlı ban tablosu                 ║
-- ║                                                                          ║
-- ║  CTI dashboard'dan süper admin bir login_attempt satırını seçip         ║
-- ║  "kullanıcıyı dondur" / "cihazı banla" / "IP'yi banla" diyebilsin.      ║
-- ║                                                                          ║
-- ║  Hepsi tek tabloda toplandı (kind sütunu) çünkü:                        ║
-- ║    • Login check tek bir SQL ile (3 IN sorgusu yerine)                  ║
-- ║    • UI tek liste + "Kaldır" butonu                                     ║
-- ║    • Audit trail tek tablodan filtrelenebilir                           ║
-- ║                                                                          ║
-- ║  expires_at NULL = kalıcı ban. is_active=FALSE manuel kaldırma audit    ║
-- ║  trail'i; satır silinmiyor, sadece pasif oluyor.                        ║
-- ║                                                                          ║
-- ║  KURULUM (yeni proje):                                                   ║
-- ║   schema.sql zaten bu tabloyu içeriyor. Bu migration mevcut canlı       ║
-- ║   projeye block_rules eklemek için (data kaybı olmadan).                ║
-- ║                                                                          ║
-- ║  ÇALIŞTIRMA:                                                             ║
-- ║   Supabase Dashboard → SQL Editor → bu dosyanın içeriği → Run           ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- migrate:up

-- Eski versiyon temizliği (idempotent re-run safe)
DROP FUNCTION IF EXISTS public.check_login_blocked(TEXT, TEXT, TEXT) CASCADE;

CREATE TABLE IF NOT EXISTS public.block_rules (
    id            BIGSERIAL PRIMARY KEY,
    kind          TEXT NOT NULL CHECK (kind IN ('user', 'device', 'ip')),
    value         TEXT NOT NULL,
    reason        TEXT,
    expires_at    TIMESTAMPTZ,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    unblocked_at  TIMESTAMPTZ,
    unblocked_by  TEXT,
    UNIQUE (kind, value, is_active) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE INDEX IF NOT EXISTS idx_block_rules_lookup
    ON public.block_rules(kind, value)
    WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_block_rules_active
    ON public.block_rules(created_at DESC)
    WHERE is_active = TRUE;

-- check_login_blocked: tek RPC ile (user, device, ip) üçlüsünü kontrol
-- eder. NULL parametre o kontrolü atlar. Dönüş: 1 satır.
CREATE OR REPLACE FUNCTION public.check_login_blocked(
    p_username TEXT,
    p_device   TEXT,
    p_ip       TEXT
) RETURNS TABLE (blocked BOOLEAN, kind TEXT, reason TEXT, expires_at TIMESTAMPTZ)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    RETURN QUERY
    SELECT TRUE, b.kind, b.reason, b.expires_at
      FROM public.block_rules b
     WHERE b.is_active = TRUE
       AND (b.expires_at IS NULL OR b.expires_at > NOW())
       AND (
            (b.kind = 'user'   AND p_username IS NOT NULL AND lower(b.value) = lower(p_username))
         OR (b.kind = 'device' AND p_device   IS NOT NULL AND b.value = p_device)
         OR (b.kind = 'ip'     AND p_ip       IS NOT NULL AND b.value = p_ip)
       )
     ORDER BY
       CASE b.kind WHEN 'device' THEN 1 WHEN 'ip' THEN 2 ELSE 3 END
     LIMIT 1;

    IF NOT FOUND THEN
        blocked := FALSE; kind := NULL; reason := NULL; expires_at := NULL;
        RETURN NEXT;
    END IF;
END;
$$;

ALTER TABLE public.block_rules ENABLE ROW LEVEL SECURITY;
-- Anon ve authenticated için policy YOK → varsayılan DENY.
-- Sadece service_role (panel/Edge) okuyup yazıyor.

REVOKE ALL    ON FUNCTION public.check_login_blocked(TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.check_login_blocked(TEXT, TEXT, TEXT) TO authenticated, service_role;

-- migrate:down

DROP FUNCTION IF EXISTS public.check_login_blocked(TEXT, TEXT, TEXT);
DROP TABLE IF EXISTS public.block_rules;
