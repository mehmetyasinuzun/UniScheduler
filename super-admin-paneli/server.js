// UniScheduler — Super Admin Panel
// ─────────────────────────────────────────────────────────────────────
// Manages organizations, admin users, and data viewing.
// Uses Supabase service_role key for full DB access + Auth Admin API.
// Protected by session-based authentication.
// ─────────────────────────────────────────────────────────────────────

require('dotenv').config();
const express  = require('express');
const crypto   = require('crypto');
const { createClient } = require('@supabase/supabase-js');
const helmet   = require('helmet');
const cors     = require('cors');
const multer   = require('multer');
const XLSX     = require('xlsx');
const path     = require('path');

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 5 * 1024 * 1024 } });

const app = express();
app.use(helmet({ contentSecurityPolicy: false }));
app.use(cors());
app.use(express.json());
app.use(express.static('public'));

// ── Session / Auth Config ────────────────────────────────────────────
// Simple token-based auth. On login, server issues a random token stored in-memory.
// The frontend sends it as Authorization header on every API request.
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'superadmin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'SuperAdmin123!';
const activeSessions = new Map(); // token → { createdAt }
const SESSION_TTL_MS = 8 * 60 * 60 * 1000; // 8 hours

// ── Supabase Client ──────────────────────────────────────────────────
const supabaseUrl = process.env.SUPABASE_URL;
const serviceKey  = process.env.SUPABASE_SERVICE_KEY;

if (!supabaseUrl || !serviceKey) {
    console.error('Missing SUPABASE_URL or SUPABASE_SERVICE_KEY in .env');
    process.exit(1);
}

const supabase = createClient(supabaseUrl, serviceKey, {
    auth: { autoRefreshToken: false, persistSession: false }
});

// ── Auth Endpoints ───────────────────────────────────────────────────
app.post('/api/auth/login', (req, res) => {
    const ip = req.ip || req.connection.remoteAddress;
    if (!checkRateLimit(ip)) {
        return res.status(429).json({ error: 'Too many login attempts. Try again later.' });
    }
    const { username, password } = req.body;
    if (username === ADMIN_USERNAME && password === ADMIN_PASSWORD) {
        const token = crypto.randomBytes(32).toString('hex');
        activeSessions.set(token, { createdAt: Date.now() });
        return res.json({ token });
    }
    return res.status(401).json({ error: 'Invalid credentials.' });
});

app.post('/api/auth/logout', (req, res) => {
    const token = extractToken(req);
    if (token) activeSessions.delete(token);
    res.json({ ok: true });
});

app.get('/api/auth/check', (req, res) => {
    const token = extractToken(req);
    if (isValidSession(token)) return res.json({ authenticated: true });
    return res.status(401).json({ authenticated: false });
});

function extractToken(req) {
    const auth = req.headers.authorization || '';
    if (auth.startsWith('Bearer ')) return auth.slice(7);
    return null;
}

function isValidSession(token) {
    if (!token) return false;
    const session = activeSessions.get(token);
    if (!session) return false;
    if (Date.now() - session.createdAt > SESSION_TTL_MS) {
        activeSessions.delete(token);
        return false;
    }
    return true;
}

// ── Auth Middleware — protects all /api/* routes except login/check ──
function requireAuth(req, res, next) {
    if (req.path === '/api/auth/login' || req.path === '/api/auth/check') return next();
    const token = extractToken(req) || req.query.token;
    if (!isValidSession(token)) {
        return res.status(401).json({ error: 'Authentication required.' });
    }
    next();
}
app.use('/api', requireAuth);

// ── SHA-256 helper (for backward compat with existing users) ────────
function sha256(text) {
    return crypto.createHash('sha256').update(text).digest('hex');
}

// ── Rate limiting (simple in-memory) ─────────────────────────────────
const loginAttempts = new Map();
const MAX_ATTEMPTS  = 5;
const WINDOW_MS     = 15 * 60 * 1000; // 15 min

function checkRateLimit(ip) {
    const now = Date.now();
    const entry = loginAttempts.get(ip) || { count: 0, start: now };
    if (now - entry.start > WINDOW_MS) {
        loginAttempts.set(ip, { count: 1, start: now });
        return true;
    }
    entry.count++;
    loginAttempts.set(ip, entry);
    return entry.count <= MAX_ATTEMPTS;
}

// ═══ Organizations ═══════════════════════════════════════════════════
app.get('/api/organizations', async (req, res) => {
    const { data, error } = await supabase.from('organizations').select('*').order('id');
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

app.post('/api/organizations', async (req, res) => {
    const { name, code } = req.body;
    if (!name || !code) return res.status(400).json({ error: 'Name and code are required.' });
    const { data, error } = await supabase.from('organizations').insert({ name, code }).select().single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

app.delete('/api/organizations/:id', async (req, res) => {
    const { error } = await supabase.from('organizations').delete().eq('id', req.params.id);
    if (error) return res.status(400).json({ error: error.message });
    res.json({ ok: true });
});

// ═══ Admin Users ═════════════════════════════════════════════════════
// Creates admin users via Supabase Auth Admin API + public.users profile.
app.get('/api/admins', async (req, res) => {
    const { data, error } = await supabase
        .from('users')
        .select('*, organizations(*)')
        .eq('role', 'admin')
        .order('created_at', { ascending: false });
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

app.post('/api/admins', async (req, res) => {
    const { username, password, orgId, mustChangePassword = true } = req.body;
    if (!username || !password || !orgId) return res.status(400).json({ error: 'All fields required.' });
    if (password.length < 6) return res.status(400).json({ error: 'Password must be at least 6 characters.' });

    try {
        const email = `${username.trim().toLowerCase()}@unischeduler.app`;

        // 1. Create Supabase Auth user via Admin API
        const { data: authUser, error: authError } = await supabase.auth.admin.createUser({
            email: email,
            password: password,
            email_confirm: true // auto-confirm
        });
        if (authError) return res.status(400).json({ error: authError.message });

        // 2. Insert public.users profile
        const { data, error } = await supabase.from('users').insert({
            id: authUser.user.id,
            org_id: parseInt(orgId),
            username: username.trim(),
            role: 'admin',
            must_change_password: mustChangePassword
        }).select().single();

        if (error) {
            // Rollback auth user if profile insert fails
            await supabase.auth.admin.deleteUser(authUser.user.id);
            return res.status(400).json({ error: error.message });
        }

        res.json(data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.delete('/api/admins/:id', async (req, res) => {
    try {
        // Delete from auth.users (cascades to public.users via FK)
        const { error: authError } = await supabase.auth.admin.deleteUser(req.params.id);
        if (authError) {
            // Fallback: delete from public.users directly
            await supabase.from('users').delete().eq('id', req.params.id);
        }
        res.json({ ok: true });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.put('/api/admins/:id/reset-password', async (req, res) => {
    const { password } = req.body;
    if (!password || password.length < 6) return res.status(400).json({ error: 'Password must be at least 6 characters.' });

    try {
        // Update in Supabase Auth
        const { error: authError } = await supabase.auth.admin.updateUserById(req.params.id, {
            password: password
        });
        if (authError) return res.status(400).json({ error: authError.message });

        // Set must_change_password flag
        await supabase.from('users').update({ must_change_password: true }).eq('id', req.params.id);

        res.json({ ok: true });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// ═══ Dashboard / Stats ═══════════════════════════════════════════════
app.get('/api/stats/:orgId', async (req, res) => {
    const orgId = req.params.orgId;
    const [depts, lecs, courses, classrooms, offerings, entries] = await Promise.all([
        supabase.from('departments').select('id', { count: 'exact', head: true }).eq('org_id', orgId),
        supabase.from('lecturers').select('id', { count: 'exact', head: true }).eq('org_id', orgId),
        supabase.from('courses').select('id', { count: 'exact', head: true }).eq('org_id', orgId),
        supabase.from('classrooms').select('id', { count: 'exact', head: true }).eq('org_id', orgId),
        supabase.from('offerings').select('id', { count: 'exact', head: true }).eq('org_id', orgId),
        supabase.from('schedule_entries').select('id', { count: 'exact', head: true }).eq('org_id', orgId)
    ]);
    res.json({
        departments:    depts.count || 0,
        lecturers:      lecs.count || 0,
        courses:        courses.count || 0,
        classrooms:     classrooms.count || 0,
        offerings:      offerings.count || 0,
        scheduleEntries: entries.count || 0
    });
});

// ═══ Org Settings ════════════════════════════════════════════════════
app.get('/api/settings/:orgId', async (req, res) => {
    const { data, error } = await supabase.from('org_settings').select('*').eq('org_id', req.params.orgId).single();
    if (error && error.code !== 'PGRST116') return res.status(500).json({ error: error.message });
    res.json(data || { org_id: req.params.orgId, time_step_minutes: 10, day_start: '08:00', day_end: '18:00' });
});

app.put('/api/settings/:orgId', async (req, res) => {
    const { timeStepMinutes, dayStart, dayEnd } = req.body;
    const { data, error } = await supabase.from('org_settings').upsert({
        org_id: parseInt(req.params.orgId),
        time_step_minutes: parseInt(timeStepMinutes) || 10,
        day_start: dayStart || '08:00',
        day_end: dayEnd || '18:00'
    }).select().single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

// ═══ Departments ═════════════════════════════════════════════════════
app.get('/api/departments/:orgId', async (req, res) => {
    const { data, error } = await supabase.from('departments').select('*').eq('org_id', req.params.orgId).order('name');
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

app.post('/api/departments', async (req, res) => {
    const { name, orgId } = req.body;
    if (!name || !orgId) return res.status(400).json({ error: 'Name and orgId required.' });
    const { data, error } = await supabase.from('departments').insert({ name, org_id: orgId }).select().single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

app.put('/api/departments/:id', async (req, res) => {
    const { name } = req.body;
    if (!name) return res.status(400).json({ error: 'Name is required.' });
    const { data, error } = await supabase.from('departments').update({ name }).eq('id', req.params.id).select().single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

app.delete('/api/departments/:id', async (req, res) => {
    const { error } = await supabase.from('departments').delete().eq('id', req.params.id);
    if (error) return res.status(400).json({ error: error.message });
    res.json({ ok: true });
});

// ═══ Lecturers ═══════════════════════════════════════════════════════
app.get('/api/lecturers/:orgId', async (req, res) => {
    const { data, error } = await supabase
        .from('lecturers')
        .select('*, departments(*), users(*)')
        .eq('org_id', req.params.orgId)
        .order('last_name');
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

// ═══ Courses ═════════════════════════════════════════════════════════
app.get('/api/courses/:orgId', async (req, res) => {
    const { data, error } = await supabase
        .from('courses')
        .select('*, departments(*)')
        .eq('org_id', req.params.orgId)
        .order('code');
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

app.post('/api/courses', async (req, res) => {
    const { orgId, code, name, theoryHours = 0, labHours = 0, credits = 0, departmentId } = req.body;
    if (!orgId || !code || !name) return res.status(400).json({ error: 'orgId, code, and name are required.' });
    const { data, error } = await supabase.from('courses').insert({
        org_id: parseInt(orgId), code, name,
        theory_hours: parseInt(theoryHours), lab_hours: parseInt(labHours),
        credits: parseInt(credits), department_id: departmentId || null
    }).select().single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

app.put('/api/courses/:id', async (req, res) => {
    const { code, name, theoryHours = 0, labHours = 0, credits = 0, departmentId } = req.body;
    if (!code || !name) return res.status(400).json({ error: 'code and name are required.' });
    const { data, error } = await supabase.from('courses').update({
        code, name, theory_hours: parseInt(theoryHours), lab_hours: parseInt(labHours),
        credits: parseInt(credits), department_id: departmentId || null
    }).eq('id', req.params.id).select().single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

// ═══ Classrooms ══════════════════════════════════════════════════════
app.get('/api/classrooms/:orgId', async (req, res) => {
    const { data, error } = await supabase
        .from('classrooms')
        .select('*, departments(*)')
        .eq('org_id', req.params.orgId)
        .order('room_code');
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

app.post('/api/classrooms', async (req, res) => {
    const { orgId, roomCode, capacity, type, departmentId } = req.body;
    if (!orgId || !roomCode || !capacity) return res.status(400).json({ error: 'orgId, roomCode and capacity required.' });
    const { data, error } = await supabase.from('classrooms').insert({
        org_id: parseInt(orgId), room_code: roomCode,
        capacity: parseInt(capacity), type: type || 'theory',
        department_id: departmentId || null
    }).select().single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

app.delete('/api/classrooms/:id', async (req, res) => {
    const { error } = await supabase.from('classrooms').delete().eq('id', req.params.id);
    if (error) return res.status(400).json({ error: error.message });
    res.json({ ok: true });
});

app.put('/api/classrooms/:id', async (req, res) => {
    const { roomCode, capacity, type, departmentId } = req.body;
    if (!roomCode || !capacity) return res.status(400).json({ error: 'roomCode and capacity required.' });
    const { data, error } = await supabase.from('classrooms').update({
        room_code: roomCode, capacity: parseInt(capacity),
        type: type || 'theory', department_id: departmentId || null
    }).eq('id', req.params.id).select().single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

// ═══ Lecturer Credential Utilities ═════════════════════════════════════
const TURKISH_MAP = {
    'ş': 's', 'Ş': 's', 'ç': 'c', 'Ç': 'c', 'ğ': 'g', 'Ğ': 'g',
    'ü': 'u', 'Ü': 'u', 'ö': 'o', 'Ö': 'o', 'ı': 'i', 'İ': 'i'
};

function normalizeText(text) {
    if (!text) return '';
    return text.split('').map(c => TURKISH_MAP[c] || c).join('').toLowerCase().replace(/[^a-z0-9_]/g, '');
}

function stripTitle(name) {
    let result = name.trim();
    const titles = ["prof. dr.", "prof.", "doç. dr.", "doç.", "dr. öğr. üyesi", "dr.", "arş. gör.", "öğr. gör.", "assoc. prof.", "assist. prof.", "res. asst.", "lect."];
    for (const t of titles) {
        if (result.toLowerCase().startsWith(t)) {
            result = result.substring(t.length).trim();
            break;
        }
    }
    return result;
}

async function generateUniqueUsername(firstName, lastName) {
    const first = normalizeText(stripTitle(firstName));
    const last = normalizeText(stripTitle(lastName));
    const base = `${first}_${last}`;
    let username = base;
    let counter = 1;
    while (true) {
        const { data } = await supabase.from('users').select('id').eq('username', username).single();
        if (!data) return username; // unique!
        username = `${base}${counter}`;
        counter++;
    }
}

function generatePassword() {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let pass = '';
    for (let i = 0; i < 6; i++) pass += chars.charAt(Math.floor(Math.random() * chars.length));
    return pass;
}

// ═══ Lecturers CRUD ══════════════════════════════════════════════════
app.post('/api/lecturers', async (req, res) => {
    const { orgId, title, firstName, lastName, email, departmentId } = req.body;
    if (!orgId || !firstName || !lastName)
        return res.status(400).json({ error: 'orgId, firstName and lastName required.' });

    try {
        const username = await generateUniqueUsername(firstName, lastName);
        const password = generatePassword();
        const emailAddr = `${username}@unischeduler.app`;
        
        const { data: authUser, error: authError } = await supabase.auth.admin.createUser({
            email: emailAddr, password, email_confirm: true
        });
        if (authError) return res.status(400).json({ error: authError.message });

        const { data: userRow, error: userErr } = await supabase.from('users').insert({
            id: authUser.user.id, org_id: parseInt(orgId),
            username: username, role: 'lecturer', must_change_password: true
        }).select().single();
        if (userErr) { await supabase.auth.admin.deleteUser(authUser.user.id); return res.status(400).json({ error: userErr.message }); }

        const { data, error } = await supabase.from('lecturers').insert({
            org_id: parseInt(orgId), user_id: authUser.user.id,
            title: title || '', first_name: firstName, last_name: lastName,
            email: email || null, department_id: departmentId || null
        }).select().single();
        if (error) { await supabase.auth.admin.deleteUser(authUser.user.id); return res.status(400).json({ error: error.message }); }

        res.json({ ...data, generatedCredentials: { username, password } });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/lecturers/:id/reset-password', async (req, res) => {
    try {
        const { data: lec } = await supabase.from('lecturers').select('user_id, users(username)').eq('id', req.params.id).single();
        if (!lec || !lec.user_id) return res.status(404).json({ error: 'Lecturer user not found.' });

        const newPassword = generatePassword();
        const { error: updateAuthErr } = await supabase.auth.admin.updateUserById(lec.user_id, { password: newPassword });
        if (updateAuthErr) return res.status(400).json({ error: updateAuthErr.message });

        await supabase.from('users').update({ must_change_password: true }).eq('id', lec.user_id);
        
        res.json({ ok: true, generatedCredentials: { username: lec.users.username, password: newPassword } });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

app.delete('/api/lecturers/:id', async (req, res) => {
    try {
        const { data: lec } = await supabase.from('lecturers').select('user_id').eq('id', req.params.id).single();
        if (lec && lec.user_id) {
            await supabase.auth.admin.deleteUser(lec.user_id);
        }
        const { error } = await supabase.from('lecturers').delete().eq('id', req.params.id);
        if (error) return res.status(400).json({ error: error.message });
        res.json({ ok: true });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

app.put('/api/lecturers/:id', async (req, res) => {
    const { title, firstName, lastName, email, departmentId } = req.body;
    if (!firstName || !lastName) return res.status(400).json({ error: 'firstName and lastName required.' });
    const { data, error } = await supabase.from('lecturers').update({
        title: title || '', first_name: firstName, last_name: lastName,
        email: email || null, department_id: departmentId || null
    }).eq('id', req.params.id).select().single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

// ═══ Excel Import ════════════════════════════════════════════════════
app.post('/api/import/:type/:orgId', upload.single('file'), async (req, res) => {
    try {
        const { type, orgId } = req.params;
        if (!req.file) return res.status(400).json({ error: 'Dosya yüklenmedi.' });

        const wb = XLSX.read(req.file.buffer, { type: 'buffer' });
        const sheet = wb.Sheets[wb.SheetNames[0]];
        const rawRows = XLSX.utils.sheet_to_json(sheet);
        if (rawRows.length === 0) return res.status(400).json({ error: 'Dosya boş.' });

        function normalizeRow(row) {
            const out = {};
            for (const key of Object.keys(row)) {
                const normalized = key
                    .replace(/([a-z])([A-Z])/g, '$1_$2')
                    .replace(/[\s\-]+/g, '_')
                    .toLowerCase();
                out[normalized] = row[key];
            }
            return out;
        }
        const rows = rawRows.map(normalizeRow);

        let inserted = 0;
        let errors = [];

        if (type === 'lecturers') {
            for (const r of rows) {
                try {
                    const firstName = (r.first_name || r.ad || r.firstname || r.name || '').toString().trim();
                    const lastName = (r.last_name || r.soyad || r.lastname || r.surname || '').toString().trim();
                    if (!firstName || !lastName) { errors.push('Ad veya soyad boş — satır atlandı.'); continue; }
                    
                    let uname = (r.username || r.kullanici_adi || r.kullanici || '').toString().trim().toLowerCase();
                    if (!uname) uname = await generateUniqueUsername(firstName, lastName);
                    
                    let pwd = (r.password || r.sifre || '').toString();
                    if (!pwd) pwd = generatePassword();
                    
                    const emailAddr = `${uname}@unischeduler.app`;

                    const { data: authUser, error: authErr } = await supabase.auth.admin.createUser({ email: emailAddr, password: pwd, email_confirm: true });
                    if (authErr) { errors.push(`${firstName} ${lastName}: ${authErr.message}`); continue; }

                    await supabase.from('users').insert({ id: authUser.user.id, org_id: parseInt(orgId), username: uname, role: 'lecturer', must_change_password: true });

                    await supabase.from('lecturers').insert({
                        org_id: parseInt(orgId), user_id: authUser.user.id,
                        title: (r.title || r.unvan || '').toString(),
                        first_name: firstName,
                        last_name: lastName,
                        email: (r.email || r.eposta || r.e_posta || '').toString() || null
                    });
                    inserted++;
                } catch (e) { errors.push(`Satır hatası: ${e.message}`); }
            }
        } else if (type === 'courses') {
            const records = rows.map(r => ({
                org_id: parseInt(orgId),
                code: (r.code || r.kod || r.ders_kodu || '').toString().trim(),
                name: (r.name || r.ad || r.ders_adi || r.ders || '').toString().trim(),
                theory_hours: parseInt(r.theory_hours || r.teori || r.theory || 0) || 0,
                lab_hours: parseInt(r.lab_hours || r.lab || r.laboratory || 0) || 0,
                credits: parseInt(r.credits || r.kredi || r.credit || 0) || 0
            })).filter(r => r.code && r.name);
            if (records.length === 0) return res.status(400).json({ error: 'Geçerli ders kaydı bulunamadı. Sütunlarda en az "Kod" ve "Ad" olmalı.' });
            const { error } = await supabase.from('courses').insert(records);
            if (error) return res.status(400).json({ error: error.message });
            inserted = records.length;
        } else if (type === 'classrooms') {
            const records = rows.map(r => ({
                org_id: parseInt(orgId),
                room_code: (r.room_code || r.oda_kodu || r.code || r.kod || r.sinif || '').toString().trim(),
                capacity: parseInt(r.capacity || r.kapasite || 30) || 30,
                type: (r.type || r.tur || r.tip || 'theory').toString().toLowerCase().trim()
            })).filter(r => r.room_code);
            if (records.length === 0) return res.status(400).json({ error: 'Geçerli sınıf kaydı bulunamadı. Sütunlarda en az "Oda_Kodu" olmalı.' });
            const { error } = await supabase.from('classrooms').insert(records);
            if (error) return res.status(400).json({ error: error.message });
            inserted = records.length;
        } else {
            return res.status(400).json({ error: 'Geçersiz import tipi.' });
        }

        res.json({ inserted, errors, message: `${inserted} kayıt başarıyla eklendi.` });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

// ═══ Excel Export ════════════════════════════════════════════════════
app.get('/api/export/:type/:orgId', async (req, res) => {
    try {
        const { type, orgId } = req.params;
        let data, filename;

        if (type === 'lecturers') {
            const result = await supabase.from('lecturers').select('title, first_name, last_name, email, departments(name), users(username)').eq('org_id', orgId).order('last_name');
            data = (result.data || []).map(l => ({ Title: l.title, First_Name: l.first_name, Last_Name: l.last_name, Email: l.email || '', Department: l.departments ? l.departments.name : '', Username: l.users ? l.users.username : '' }));
            filename = 'akademisyenler.xlsx';
        } else if (type === 'courses') {
            const result = await supabase.from('courses').select('code, name, theory_hours, lab_hours, credits, departments(name)').eq('org_id', orgId).order('code');
            data = (result.data || []).map(c => ({ Code: c.code, Name: c.name, Theory_Hours: c.theory_hours, Lab_Hours: c.lab_hours, Credits: c.credits, Department: c.departments ? c.departments.name : '' }));
            filename = 'dersler.xlsx';
        } else if (type === 'classrooms') {
            const result = await supabase.from('classrooms').select('room_code, capacity, type, departments(name)').eq('org_id', orgId).order('room_code');
            data = (result.data || []).map(c => ({ Room_Code: c.room_code, Capacity: c.capacity, Type: c.type, Department: c.departments ? c.departments.name : '' }));
            filename = 'siniflar.xlsx';
        } else {
            return res.status(400).json({ error: 'Geçersiz export tipi.' });
        }

        const ws = XLSX.utils.json_to_sheet(data);
        const wb = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(wb, ws, 'Veri');
        const buf = XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' });

        res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
        res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
        res.send(buf);
    } catch (e) { res.status(500).json({ error: e.message }); }
});

// ═══ Schedule ════════════════════════════════════════════════════════
app.get('/api/schedule/:orgId', async (req, res) => {
    const { data, error } = await supabase
        .from('schedule_entries')
        .select('*, offerings(*, courses(*)), lecturers(*), classrooms(*)')
        .eq('org_id', req.params.orgId)
        .order('day')
        .order('start_time');
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

app.delete('/api/schedule/:id', async (req, res) => {
    const { error } = await supabase.from('schedule_entries').delete().eq('id', req.params.id);
    if (error) return res.status(400).json({ error: error.message });
    res.json({ ok: true });
});

// ═══ Availability ════════════════════════════════════════════════════
app.get('/api/availability/:orgId', async (req, res) => {
    const { lecturerId } = req.query;
    let query = supabase.from('lecturer_availability').select('*').eq('org_id', req.params.orgId);
    if (lecturerId) query = query.eq('lecturer_id', lecturerId);
    const { data, error } = await query.order('day').order('start_time');
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

app.post('/api/availability', async (req, res) => {
    const { lecturerId, day, startTime, endTime, orgId } = req.body;
    const { data, error } = await supabase.from('lecturer_availability').insert({
        lecturer_id: parseInt(lecturerId), day,
        start_time: startTime, end_time: endTime,
        org_id: parseInt(orgId)
    }).select().single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

app.delete('/api/availability/:id', async (req, res) => {
    const { error } = await supabase.from('lecturer_availability').delete().eq('id', req.params.id);
    if (error) return res.status(400).json({ error: error.message });
    res.json({ ok: true });
});

// ═══ Error Logs ══════════════════════════════════════════════════════
app.get('/api/error-logs', async (req, res) => {
    const { orgId } = req.query;
    let query = supabase.from('client_error_logs').select('*, organizations(name)');
    if (orgId) query = query.eq('org_id', orgId);
    const { data, error } = await query.order('created_at', { ascending: false }).limit(100);
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

// ═══ Start Server ════════════════════════════════════════════════════
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Admin panel → http://localhost:${PORT}`));
