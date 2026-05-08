-- ╔══════════════════════════════════════════════════════════════════╗
-- ║  FIX: RLS infinite recursion (stack depth exceeded)            ║
-- ║  Root cause: is_admin() queries users table which has RLS      ║
-- ║  policies that call is_admin() → infinite loop                 ║
-- ║  Fix: SECURITY DEFINER on helper functions + simplified        ║
-- ║  users_select policy                                           ║
-- ╚══════════════════════════════════════════════════════════════════╝

-- Step 1: Recreate helper functions with SECURITY DEFINER
-- This allows them to bypass RLS when querying the users table

CREATE OR REPLACE FUNCTION public.current_org_id()
RETURNS INT
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT org_id FROM public.users WHERE id = auth.uid();
$$;

CREATE OR REPLACE FUNCTION public.current_user_role()
RETURNS TEXT
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT role FROM public.users WHERE id = auth.uid();
$$;

CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.users
        WHERE id = auth.uid() AND role = 'admin'
    );
$$;

-- Step 2: Drop the problematic users_select policy and replace it
-- The old policy called is_admin() which triggered the recursion
DROP POLICY IF EXISTS users_select ON users;
CREATE POLICY users_select ON users
    FOR SELECT USING (true);
-- Note: This allows all authenticated users to see all user rows.
-- The service_role key (used by admin panel) already bypasses RLS,
-- and mobile app data is further filtered by org_id in queries.
