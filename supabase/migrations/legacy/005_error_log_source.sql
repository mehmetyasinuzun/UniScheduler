-- ╔══════════════════════════════════════════════════════════════════╗
-- ║  Migration 005: Add `source` to client_error_logs              ║
-- ║                                                                ║
-- ║  ⚠️  LEGACY — for installations that pre-date the consolidated  ║
-- ║       schema.sql. New installations only need schema.sql; this  ║
-- ║       file is idempotent (ADD COLUMN IF NOT EXISTS, etc.).     ║
-- ║                                                                ║
-- ║  Why: super-admin web panel and the mobile app both write to   ║
-- ║  client_error_logs. Without a source flag we can't filter      ║
-- ║  panel-side errors from mobile-side crashes when triaging.     ║
-- ║                                                                ║
-- ║  Allowed values: 'mobile' | 'panel' | 'server'                 ║
-- ╚══════════════════════════════════════════════════════════════════╝

ALTER TABLE public.client_error_logs
    ADD COLUMN IF NOT EXISTS source TEXT
        CHECK (source IN ('mobile', 'panel', 'server'))
        DEFAULT 'mobile';

CREATE INDEX IF NOT EXISTS idx_error_logs_source
    ON public.client_error_logs(source, created_at DESC);

-- Allow panel inserts without an org_id (panel is super-admin scope).
ALTER TABLE public.client_error_logs
    ALTER COLUMN org_id DROP NOT NULL;

-- RLS: super_admin / panel inserts via service_role bypass RLS, no extra
-- policy needed. The existing `error_logs_select` for admins still requires
-- org_id match — but panel logs (org_id = NULL) are intentionally only
-- visible via service_role. Add a fallback select for super-admin context:
DROP POLICY IF EXISTS error_logs_select_super ON public.client_error_logs;
CREATE POLICY error_logs_select_super ON public.client_error_logs
    FOR SELECT USING (
        source = 'mobile' AND public.is_admin() AND org_id = public.current_org_id()
    );
