-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║ UniScheduler Baseline Migration                                          ║
-- ║                                                                          ║
-- ║ Bu migration "marker" niteliğindedir — gerçek tablo/RLS tanımları        ║
-- ║ supabase/schema.sql dosyasında bulunur. dbmate sisteminin migration       ║
-- ║ zincirine "buradan başla" damgası vurmak için var.                       ║
-- ║                                                                          ║
-- ║ Yeni Supabase projesi kurulumu:                                          ║
-- ║   1. SQL Editor → schema.sql tamamını yapıştır + Run                     ║
-- ║   2. dbmate kurduktan sonra:                                             ║
-- ║        dbmate up                                                         ║
-- ║      ilk satır olarak bu baseline'ı applied olarak işaretler             ║
-- ║      (idempotent — schema.sql tablolarına dokunmaz).                     ║
-- ║                                                                          ║
-- ║ Mevcut canlı projeyi dbmate'e geçirme:                                    ║
-- ║   psql ile bir kez şunu çalıştırın (manuel baseline pin):                 ║
-- ║      CREATE TABLE IF NOT EXISTS schema_migrations (                       ║
-- ║         version VARCHAR(255) PRIMARY KEY                                  ║
-- ║      );                                                                   ║
-- ║      INSERT INTO schema_migrations (version)                              ║
-- ║         VALUES ('20260521000000') ON CONFLICT DO NOTHING;                ║
-- ║                                                                          ║
-- ║ Sonraki migration'lar normal dbmate akışıyla yazılır:                    ║
-- ║      dbmate new add_audit_request_id                                     ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- migrate:up

-- Schema'nın gerçekten kurulu olduğunu garanti et (sanity check).
-- Eğer schema.sql çalıştırılmadıysa bu sorgu hata verir; uyarı verir
-- ve migration başarısız olur — kullanıcı önce schema.sql'i kurmalıdır.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'organizations'
    ) THEN
        RAISE EXCEPTION 'UniScheduler baseline missing: ''organizations'' table not found. '
                        'Run supabase/schema.sql in Supabase SQL Editor before applying migrations.';
    END IF;
END $$;

-- Baseline'ı işaretle — schema_migrations'a versiyon zaten dbmate
-- tarafından insert edilir; burada ek bir log satırı bırakıyoruz.
COMMENT ON DATABASE postgres IS 'UniScheduler baseline 20260521000000';

-- migrate:down

-- Baseline rollback YAPILAMAZ — schema.sql ile kurulan tablolar
-- elle düşürülmelidir (`DROP TABLE ... CASCADE`). Tehlikeli işlemdir,
-- production'da çalıştırmayın. Detaylar için schema.sql üst kısmındaki
-- DROP listesine bakın.

DO $$
BEGIN
    RAISE NOTICE 'Baseline rollback is a no-op. '
                 'Drop schema manually via schema.sql DROP block if needed.';
END $$;
