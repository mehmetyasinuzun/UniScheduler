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

// CORS: in production, only allow explicit origins (ALLOWED_ORIGINS env).
// In dev, allow everything for convenience.
const isProduction = process.env.NODE_ENV === 'production';
const allowedOrigins = (process.env.ALLOWED_ORIGINS || '')
    .split(',').map(s => s.trim()).filter(Boolean);

if (isProduction && allowedOrigins.length > 0) {
    app.use(cors({
        origin: (origin, cb) => {
            if (!origin || allowedOrigins.includes(origin)) cb(null, true);
            else cb(new Error('Origin not allowed'));
        },
        credentials: true
    }));
} else {
    app.use(cors());
}

app.use(express.json());

// Optional IP allowlist — set ALLOWED_IPS to restrict access to known IPs.
const allowedIps = (process.env.ALLOWED_IPS || '')
    .split(',').map(s => s.trim()).filter(Boolean);
if (allowedIps.length > 0) {
    app.use((req, res, next) => {
        const clientIp = (req.headers['x-forwarded-for']?.split(',')[0]?.trim()) || req.ip;
        // Match exact IP. CIDR support intentionally omitted; keep this simple
        // and use a real firewall (nginx, iptables, cloud security groups) for
        // network-level filtering. This is a defense-in-depth backup.
        if (!allowedIps.includes(clientIp)) {
            return res.status(403).json({ error: 'IP not allowed.' });
        }
        next();
    });
}

// Disable caching on every API response so the panel never serves stale data
// after switching organizations. Browsers / proxies otherwise hold onto the
// previous org's payload until the URL changes.
app.use('/api', (req, res, next) => {
    res.set('Cache-Control', 'no-store, no-cache, must-revalidate, proxy-revalidate');
    res.set('Pragma', 'no-cache');
    res.set('Expires', '0');
    res.set('Surrogate-Control', 'no-store');
    next();
});

app.use(express.static('public', {
    setHeaders: (res, filePath) => {
        if (filePath.endsWith('.html')) {
            res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
        }
    }
}));

// Validate that any `:orgId` path parameter is a positive integer before it
// reaches a handler. Catches typos / tampering early and prevents accidental
// `eq('org_id', 'NaN')` queries that return zero rows or full-table scans.
function requireValidOrgId(req, res, next) {
    if (!('orgId' in req.params)) return next();
    const n = Number.parseInt(req.params.orgId, 10);
    if (!Number.isFinite(n) || n <= 0) {
        return res.status(400).json({ error: 'Invalid orgId.' });
    }
    req.params.orgId = String(n); // normalize
    next();
}
app.param('orgId', requireValidOrgId);

// ── Session / Auth Config ────────────────────────────────────────────
// Simple token-based auth. On login, server issues a random token stored in-memory.
// The frontend sends it as Authorization header on every API request.
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'superadmin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'SuperAdmin123!';
const activeSessions = new Map(); // token → { createdAt }
const SESSION_TTL_MS = 8 * 60 * 60 * 1000; // 8 hours

// Production safety: refuse to start with default / weak credentials.
const WEAK_DEFAULTS = new Set([
    'SuperAdmin123!',
    'CHANGE_ME_PRODUCTION_REQUIRES_STRONG_PASSWORD',
    'admin',
    'password',
    ''
]);
if (isProduction) {
    if (WEAK_DEFAULTS.has(ADMIN_PASSWORD) || ADMIN_PASSWORD.length < 12) {
        console.error('REFUSING TO START: ADMIN_PASSWORD is the default or too short.');
        console.error('Set a strong (16+ char random) ADMIN_PASSWORD in .env before NODE_ENV=production.');
        process.exit(1);
    }
}

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

// ── General API rate limiter: 100 req / 60s per IP ───────────────────
const apiRateMap = new Map();
const API_MAX_REQ   = 100;
const API_WINDOW_MS = 60 * 1000; // 1 min

function apiRateLimiter(req, res, next) {
    const ip = req.ip || req.connection.remoteAddress;
    const now = Date.now();
    const entry = apiRateMap.get(ip) || { count: 0, start: now };
    if (now - entry.start > API_WINDOW_MS) {
        apiRateMap.set(ip, { count: 1, start: now });
        return next();
    }
    entry.count++;
    apiRateMap.set(ip, entry);
    if (entry.count > API_MAX_REQ) {
        return res.status(429).json({ error: 'Too many requests. Try again later.' });
    }
    next();
}
app.use('/api', apiRateLimiter);

// Cleanup stale rate limit entries every 5 minutes to prevent memory leak
setInterval(() => {
    const now = Date.now();
    for (const [ip, entry] of apiRateMap) {
        if (now - entry.start > API_WINDOW_MS * 2) apiRateMap.delete(ip);
    }
    for (const [ip, entry] of loginAttempts) {
        if (now - entry.start > WINDOW_MS * 2) loginAttempts.delete(ip);
    }
}, 5 * 60 * 1000);

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

    // Auto-create default org_settings for the new organization
    await supabase.from('org_settings').insert({
        org_id: data.id,
        time_step_minutes: 10,
        day_start: '08:00',
        day_end: '18:00'
    });

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

async function generateUniqueAdminUsername(base) {
    const normalized = normalizeText(base);
    let username = normalized;
    let counter = 2;
    while (true) {
        const { data } = await supabase.from('users').select('id').eq('username', username).single();
        if (!data) return username;
        username = `${normalized}_${counter}`;
        counter++;
    }
}

app.post('/api/admins', async (req, res) => {
    const { username, password, orgId, mustChangePassword = true } = req.body;
    if (!username || !password || !orgId) return res.status(400).json({ error: 'All fields required.' });
    if (password.length < 6) return res.status(400).json({ error: 'Password must be at least 6 characters.' });

    try {
        // Normalize + deduplicate username to avoid UNIQUE(username) constraint failures
        const finalUsername = await generateUniqueAdminUsername(username);
        const email = usernameToEmail(finalUsername);

        // 1. Create Supabase Auth user via Admin API
        const { data: authUser, error: authError } = await supabase.auth.admin.createUser({
            email: email,
            password: password,
            email_confirm: true // auto-confirm
        });
        if (authError) {
            if (authError.message && authError.message.toLowerCase().includes('already registered')) {
                return res.status(400).json({ error: `Bu kullanıcı adı (${finalUsername}) zaten alınmış. Farklı bir kullanıcı adı deneyin.` });
            }
            return res.status(400).json({ error: authError.message });
        }

        // 2. Insert public.users profile
        const { data, error } = await supabase.from('users').insert({
            id: authUser.user.id,
            org_id: parseInt(orgId),
            username: finalUsername,
            role: 'admin',
            must_change_password: mustChangePassword
        }).select().single();

        if (error) {
            // Rollback auth user if profile insert fails
            await supabase.auth.admin.deleteUser(authUser.user.id);
            return res.status(400).json({ error: error.message });
        }

        res.json({ ...data, finalUsername });
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
    if (data) return res.json(data);

    // Auto-create default settings if missing (handles orgs created before this fix)
    const defaults = { org_id: parseInt(req.params.orgId), time_step_minutes: 10, day_start: '08:00', day_end: '18:00' };
    const { data: created } = await supabase.from('org_settings').upsert(defaults).select().single();
    res.json(created || defaults);
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

app.delete('/api/courses/:id', async (req, res) => {
    const orgId = req.query.orgId;
    if (!orgId) return res.status(400).json({ error: 'orgId query parameter is required.' });
    const { data: row } = await supabase.from('courses').select('org_id').eq('id', req.params.id).single();
    if (!row) return res.status(404).json({ error: 'Not found.' });
    if (String(row.org_id) !== String(orgId)) return res.status(403).json({ error: 'org_id mismatch.' });
    const { error } = await supabase.from('courses').delete().eq('id', req.params.id);
    if (error) return res.status(400).json({ error: error.message });
    res.json({ ok: true });
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
    const orgId = req.query.orgId;
    if (!orgId) return res.status(400).json({ error: 'orgId query parameter is required.' });
    const { data: row } = await supabase.from('classrooms').select('org_id').eq('id', req.params.id).single();
    if (!row) return res.status(404).json({ error: 'Not found.' });
    if (String(row.org_id) !== String(orgId)) return res.status(403).json({ error: 'org_id mismatch.' });
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

// Converts username to synthetic email for Supabase Auth
// Uses dots instead of underscores because some Supabase configs reject underscores
function usernameToEmail(username) {
    return username.trim().toLowerCase().replace(/_/g, '.') + '@unischeduler.app';
}

// ═══ Lecturers CRUD ══════════════════════════════════════════════════
app.post('/api/lecturers', async (req, res) => {
    const { orgId, title, firstName, lastName, email, departmentId } = req.body;
    if (!orgId || !firstName || !lastName)
        return res.status(400).json({ error: 'orgId, firstName and lastName required.' });

    try {
        const username = await generateUniqueUsername(firstName, lastName);
        const password = generatePassword();
        const emailAddr = usernameToEmail(username);
        
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
        const orgId = req.query.orgId;
        if (!orgId) return res.status(400).json({ error: 'orgId query parameter is required.' });
        const { data: lec } = await supabase.from('lecturers').select('user_id, org_id').eq('id', req.params.id).single();
        if (!lec) return res.status(404).json({ error: 'Not found.' });
        if (String(lec.org_id) !== String(orgId)) return res.status(403).json({ error: 'org_id mismatch.' });
        if (lec.user_id) {
            await supabase.auth.admin.deleteUser(lec.user_id).catch(() => {});
            await supabase.from('users').delete().eq('id', lec.user_id).catch(() => {});
        }
        await supabase.from('lecturers').delete().eq('id', req.params.id);
        res.json({ ok: true });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

// ═══ Bulk Password Reset — resets ALL lecturers in an org and returns credentials ═══
app.post('/api/lecturers/bulk-reset/:orgId', async (req, res) => {
    try {
        const orgId = req.params.orgId;
        const { data: lecturers } = await supabase
            .from('lecturers')
            .select('id, title, first_name, last_name, user_id, users(username)')
            .eq('org_id', orgId)
            .order('last_name');

        if (!lecturers || lecturers.length === 0)
            return res.status(400).json({ error: 'Bu organizasyonda akademisyen bulunamadı.' });

        const credentials = [];
        const errors = [];

        for (const lec of lecturers) {
            if (!lec.user_id) { errors.push(`${lec.first_name} ${lec.last_name}: Kullanıcı kaydı eksik.`); continue; }
            try {
                const newPwd = generatePassword();
                const { error: authErr } = await supabase.auth.admin.updateUserById(lec.user_id, { password: newPwd });
                if (authErr) { errors.push(`${lec.first_name} ${lec.last_name}: ${authErr.message}`); continue; }
                await supabase.from('users').update({ must_change_password: true }).eq('id', lec.user_id);
                credentials.push({
                    ad: lec.first_name,
                    soyad: lec.last_name,
                    unvan: lec.title || '',
                    kullanici_adi: lec.users ? lec.users.username : '—',
                    yeni_sifre: newPwd
                });
            } catch (e) { errors.push(`${lec.first_name} ${lec.last_name}: ${e.message}`); }
        }

        res.json({ reset: credentials.length, credentials, errors });
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
            const credentials = [];
            for (const r of rows) {
                try {
                    const firstName = (r.first_name || r.ad || r.firstname || r.name || '').toString().trim();
                    const lastName = (r.last_name || r.soyad || r.lastname || r.surname || '').toString().trim();
                    if (!firstName || !lastName) { errors.push('Ad veya soyad boş — satır atlandı.'); continue; }
                    
                    // Always generate a unique username — Excel may contain usernames
                    // from another org that already exist in Supabase Auth
                    const uname = await generateUniqueUsername(firstName, lastName);
                    
                    let pwd = (r.password || r.sifre || '').toString();
                    if (!pwd) pwd = generatePassword();
                    
                    const emailAddr = usernameToEmail(uname);

                    const { data: authUser, error: authErr } = await supabase.auth.admin.createUser({ email: emailAddr, password: pwd, email_confirm: true });
                    if (authErr) { errors.push(`${firstName} ${lastName}: ${authErr.message}`); continue; }

                    const { error: userErr } = await supabase.from('users').insert({ id: authUser.user.id, org_id: parseInt(orgId), username: uname, role: 'lecturer', must_change_password: true });
                    if (userErr) {
                        await supabase.auth.admin.deleteUser(authUser.user.id).catch(() => {});
                        errors.push(`${firstName} ${lastName}: ${userErr.message}`);
                        continue;
                    }

                    const { error: lecErr } = await supabase.from('lecturers').insert({
                        org_id: parseInt(orgId), user_id: authUser.user.id,
                        title: (r.title || r.unvan || '').toString(),
                        first_name: firstName,
                        last_name: lastName,
                        email: (r.email || r.eposta || r.e_posta || '').toString() || null
                    });
                    if (lecErr) {
                        await supabase.auth.admin.deleteUser(authUser.user.id).catch(() => {});
                        errors.push(`${firstName} ${lastName}: ${lecErr.message}`);
                        continue;
                    }

                    credentials.push({ ad: firstName, soyad: lastName, kullanici_adi: uname, gecici_sifre: pwd });
                    inserted++;
                } catch (e) { errors.push(`Satır hatası: ${e.message}`); }
            }
            return res.json({ inserted, errors, credentials, message: `${inserted} akademisyen başarıyla eklendi.` });
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

// ═══ Offerings ══════════════════════════════════════════════════════
app.get('/api/offerings/:orgId', async (req, res) => {
    const { data, error } = await supabase
        .from('offerings')
        .select('*, courses(*, departments(*)), lecturers(*)')
        .eq('org_id', req.params.orgId)
        .order('id');
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

app.post('/api/offerings', async (req, res) => {
    const { orgId, courseId, lecturerId, academicYear, term, classYear, section, capacity } = req.body;
    if (!orgId || !courseId) return res.status(400).json({ error: 'orgId and courseId required.' });
    const { data, error } = await supabase.from('offerings').insert({
        org_id: parseInt(orgId), course_id: parseInt(courseId),
        lecturer_id: lecturerId ? parseInt(lecturerId) : null,
        academic_year: academicYear || '2025-2026', term: term || 'Fall',
        class_year: parseInt(classYear) || 1, section: section || 'A',
        capacity: parseInt(capacity) || 40
    }).select('*, courses(*, departments(*)), lecturers(*)').single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

app.put('/api/offerings/:id', async (req, res) => {
    const { courseId, lecturerId, academicYear, term, classYear, section, capacity } = req.body;
    const update = {};
    if (courseId) update.course_id = parseInt(courseId);
    if (lecturerId !== undefined) update.lecturer_id = lecturerId ? parseInt(lecturerId) : null;
    if (academicYear) update.academic_year = academicYear;
    if (term) update.term = term;
    if (classYear) update.class_year = parseInt(classYear);
    if (section) update.section = section;
    if (capacity) update.capacity = parseInt(capacity);
    const { data, error } = await supabase.from('offerings').update(update)
        .eq('id', req.params.id).select('*, courses(*, departments(*)), lecturers(*)').single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

app.delete('/api/offerings/:id', async (req, res) => {
    const orgId = req.query.orgId;
    if (!orgId) return res.status(400).json({ error: 'orgId query parameter is required.' });
    const { data: row } = await supabase.from('offerings').select('org_id').eq('id', req.params.id).single();
    if (!row) return res.status(404).json({ error: 'Not found.' });
    if (String(row.org_id) !== String(orgId)) return res.status(403).json({ error: 'org_id mismatch.' });
    const { error } = await supabase.from('offerings').delete().eq('id', req.params.id);
    if (error) return res.status(400).json({ error: error.message });
    res.json({ ok: true });
});

// ═══ Schedule ════════════════════════════════════════════════════════
app.get('/api/schedule/:orgId', async (req, res) => {
    const { data, error } = await supabase
        .from('schedule_entries')
        .select('*, offerings(*, courses(*, departments(*)), lecturers(*)), lecturers(*), classrooms(*)')
        .eq('org_id', req.params.orgId)
        .order('day')
        .order('start_time');
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

app.post('/api/schedule/bulk', async (req, res) => {
    const { entries } = req.body;
    if (!Array.isArray(entries) || !entries.length) return res.status(400).json({ error: 'entries array required.' });
    const rows = entries.map(e => ({
        org_id: parseInt(e.orgId), offering_id: parseInt(e.offeringId),
        lecturer_id: e.lecturerId ? parseInt(e.lecturerId) : null,
        classroom_id: parseInt(e.classroomId), day: e.day,
        start_time: e.startTime, end_time: e.endTime
    }));
    const { data, error } = await supabase.from('schedule_entries').insert(rows).select('id');
    if (error) return res.status(400).json({ error: error.message });
    res.json({ inserted: data.length });
});

app.post('/api/schedule', async (req, res) => {
    const { orgId, offeringId, lecturerId, classroomId, day, startTime, endTime } = req.body;
    if (!orgId || !offeringId || !classroomId || !day || !startTime || !endTime)
        return res.status(400).json({ error: 'All fields required.' });
    const { data, error } = await supabase.from('schedule_entries').insert({
        org_id: parseInt(orgId), offering_id: parseInt(offeringId),
        lecturer_id: lecturerId ? parseInt(lecturerId) : null,
        classroom_id: parseInt(classroomId), day, start_time: startTime, end_time: endTime
    }).select('*, offerings(*, courses(*)), lecturers(*), classrooms(*)').single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

app.put('/api/schedule/:id', async (req, res) => {
    const { offeringId, lecturerId, classroomId, day, startTime, endTime } = req.body;
    const update = {};
    if (offeringId) update.offering_id = parseInt(offeringId);
    if (lecturerId !== undefined) update.lecturer_id = lecturerId ? parseInt(lecturerId) : null;
    if (classroomId) update.classroom_id = parseInt(classroomId);
    if (day) update.day = day;
    if (startTime) update.start_time = startTime;
    if (endTime) update.end_time = endTime;
    const { data, error } = await supabase.from('schedule_entries').update(update)
        .eq('id', req.params.id).select('*, offerings(*, courses(*)), lecturers(*), classrooms(*)').single();
    if (error) return res.status(400).json({ error: error.message });
    res.json(data);
});

app.delete('/api/schedule/:id', async (req, res) => {
    const orgId = req.query.orgId;
    if (!orgId) return res.status(400).json({ error: 'orgId query parameter is required.' });
    const { data: row } = await supabase.from('schedule_entries').select('org_id').eq('id', req.params.id).single();
    if (!row) return res.status(404).json({ error: 'Not found.' });
    if (String(row.org_id) !== String(orgId)) return res.status(403).json({ error: 'org_id mismatch.' });
    const { error } = await supabase.from('schedule_entries').delete().eq('id', req.params.id);
    if (error) return res.status(400).json({ error: error.message });
    res.json({ ok: true });
});

// ═══ Availability ════════════════════════════════════════════════════
app.get('/api/availability/:orgId', async (req, res) => {
    const { lecturerId } = req.query;
    let query = supabase.from('lecturer_availability').select('*, lecturers(first_name, last_name, title)').eq('org_id', req.params.orgId);
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
    const { data, error } = await query.order('created_at', { ascending: false }).limit(200);
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
});

// ═══ Panel Error Log (web panel kendine ait hataları buraya yazar) ══════
app.post('/api/log/panel', async (req, res) => {
    const { screen, action, message, stack } = req.body;
    if (!message) return res.status(400).json({ error: 'message required.' });

    const screenValue = screen ? `PANEL/${screen}` : 'PANEL';

    const { error } = await supabase.from('client_error_logs').insert({
        org_id: null,
        username: 'super_admin',
        role: 'super_admin',
        screen: screenValue,
        action: action || null,
        message: String(message).slice(0, 2000),
        stack_trace: stack ? String(stack).slice(0, 5000) : null,
        device_model: 'Web Panel',
        os_version: null,
        app_version: 'panel'
    });

    if (error) return res.status(500).json({ error: error.message });
    res.json({ ok: true });
});

// ═══ Startup: ensure all existing orgs have org_settings ═════════════
async function ensureOrgSettings() {
    const { data: orgs } = await supabase.from('organizations').select('id');
    if (!orgs || !orgs.length) return;
    const { data: existing } = await supabase.from('org_settings').select('org_id');
    const existingIds = new Set((existing || []).map(s => s.org_id));
    const missing = orgs.filter(o => !existingIds.has(o.id));
    for (const org of missing) {
        await supabase.from('org_settings').insert({
            org_id: org.id, time_step_minutes: 10, day_start: '08:00', day_end: '18:00'
        });
        console.log(`  ✓ org_settings created for org #${org.id}`);
    }
}

// ═══ Process-level crash handlers ═══════════════════════════════════
function logProcessError(type, err) {
    const msg = err && err.message ? err.message : String(err);
    const stack = err && err.stack ? err.stack : null;
    console.error(`[${type}]`, msg, stack || '');
    // Best-effort Supabase insert — do NOT await (process may be dying)
    supabase.from('client_error_logs').insert({
        org_id: null,
        username: 'super_admin',
        role: 'super_admin',
        screen: `PANEL/process`,
        action: type,
        message: msg.slice(0, 2000),
        stack_trace: stack ? stack.slice(0, 5000) : null,
        device_model: 'Web Panel (Node)',
        os_version: null,
        app_version: 'panel'
    }).then(() => {}).catch(() => {});
}

process.on('uncaughtException', (err) => {
    logProcessError('uncaughtException', err);
});

process.on('unhandledRejection', (reason) => {
    logProcessError('unhandledRejection', reason instanceof Error ? reason : new Error(String(reason)));
});

// ═══ Start Server ════════════════════════════════════════════════════
const PORT = process.env.PORT || 3000;
app.listen(PORT, async () => {
    console.log(`Admin panel → http://localhost:${PORT}`);
    await ensureOrgSettings();
});
