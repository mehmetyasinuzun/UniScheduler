-- UniScheduler — Seed: Default Organization + Admin Account
-- Run AFTER 001_schema.sql
--
-- IMPORTANT: With Supabase Auth (GoTrue), admin users must be created via:
--   1. Supabase Dashboard → Authentication → Users → Create User
--   2. Admin Panel API (POST /api/admins)
--
-- The admin user's UUID from auth.users is then inserted into public.users.
-- This seed creates the org and departments only. Create the admin via the admin panel.
--
-- After creating the auth user (email: admin@unischeduler.app, password: Admin123),
-- get the UUID and run:
--   INSERT INTO users (id, org_id, username, role, must_change_password)
--   VALUES ('<auth-user-uuid>', 1, 'admin', 'admin', FALSE);

-- 1. Default organization
INSERT INTO organizations (name, code)
VALUES ('Default University', 'default')
ON CONFLICT (code) DO NOTHING;

-- 2. Org settings (defaults)
INSERT INTO org_settings (org_id)
SELECT id FROM organizations WHERE code = 'default'
ON CONFLICT (org_id) DO NOTHING;

-- ─── Sample departments (optional — remove in production) ─────────────────────
INSERT INTO departments (org_id, name) VALUES
    ((SELECT id FROM organizations WHERE code = 'default'), 'Computer Engineering'),
    ((SELECT id FROM organizations WHERE code = 'default'), 'Electrical Engineering'),
    ((SELECT id FROM organizations WHERE code = 'default'), 'Mathematics')
ON CONFLICT (org_id, name) DO NOTHING;
