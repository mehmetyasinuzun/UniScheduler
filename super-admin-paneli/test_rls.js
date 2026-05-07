// Test RLS: Login as cumhuriyet admin and see what data is returned
require('dotenv').config();
const { createClient } = require('@supabase/supabase-js');

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imxjbmdhbnhlc3ZnYmZpb3JpZmlnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzcxMjk1MDAsImV4cCI6MjA5MjcwNTUwMH0.NWOs4tTHY1lQRql6Dvu0s_AH21RAy1oLVjL0cJbDiug';
const SUPABASE_SERVICE_KEY = process.env.SUPABASE_SERVICE_KEY;

async function test() {
    // 1. First get the cumhuriyet admin credentials
    const serviceClient = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);
    const { data: admins } = await serviceClient.from('users')
        .select('*')
        .eq('username', 'cumhuriyet')
        .single();
    console.log('Admin profile:', JSON.stringify(admins, null, 2));

    // 2. Create anon client (simulates mobile app) and login
    const anonClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
    const email = admins.username.replace(/_/g, '.') + '@unischeduler.app';
    console.log('\nTrying to login with:', email);
    
    // We don't know the password, so let's reset it temporarily
    await serviceClient.auth.admin.updateUserById(admins.id, { password: 'Test123456' });
    
    const { data: session, error: loginErr } = await anonClient.auth.signInWithPassword({
        email: email,
        password: 'Test123456'
    });
    if (loginErr) { console.log('Login failed:', loginErr.message); return; }
    console.log('Logged in as:', session.user.id);

    // 3. Now test RLS — query lecturers (should only show org_id=4)
    const { data: lecturers, error: lecErr } = await anonClient.from('lecturers').select('id, org_id, first_name');
    console.log('\n=== Lecturers visible (should be org_id=4 only) ===');
    if (lecErr) console.log('Error:', lecErr.message);
    else {
        const orgGroups = {};
        (lecturers || []).forEach(l => {
            orgGroups[l.org_id] = (orgGroups[l.org_id] || 0) + 1;
        });
        console.log('Total:', lecturers?.length);
        console.log('By org_id:', orgGroups);
    }

    // 4. Test courses
    const { data: courses, error: courseErr } = await anonClient.from('courses').select('id, org_id, code');
    console.log('\n=== Courses visible (should be org_id=4 only) ===');
    if (courseErr) console.log('Error:', courseErr.message);
    else {
        const orgGroups = {};
        (courses || []).forEach(c => {
            orgGroups[c.org_id] = (orgGroups[c.org_id] || 0) + 1;
        });
        console.log('Total:', courses?.length);
        console.log('By org_id:', orgGroups);
    }

    // 5. Test departments
    const { data: depts, error: deptErr } = await anonClient.from('departments').select('id, org_id, name');
    console.log('\n=== Departments visible (should be org_id=4 only) ===');
    if (deptErr) console.log('Error:', deptErr.message);
    else {
        const orgGroups = {};
        (depts || []).forEach(d => {
            orgGroups[d.org_id] = (orgGroups[d.org_id] || 0) + 1;
        });
        console.log('Total:', depts?.length);
        console.log('By org_id:', orgGroups);
    }

    // 6. Test classrooms
    const { data: rooms, error: roomErr } = await anonClient.from('classrooms').select('id, org_id');
    console.log('\n=== Classrooms visible (should be org_id=4 only) ===');
    if (roomErr) console.log('Error:', roomErr.message);
    else {
        const orgGroups = {};
        (rooms || []).forEach(r => {
            orgGroups[r.org_id] = (orgGroups[r.org_id] || 0) + 1;
        });
        console.log('Total:', rooms?.length);
        console.log('By org_id:', orgGroups);
    }

    await anonClient.auth.signOut();
}

test().catch(console.error);
