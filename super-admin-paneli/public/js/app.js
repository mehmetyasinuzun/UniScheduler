// ── State ────────────────────────────────────────────────────────
let orgs = [];
let allSchedule = [];
let currentAvailability = [];
let authToken = localStorage.getItem('adminToken') || '';
let currentEditType = null;
let currentEditId = null;

const dayNames = { Monday: 'Pazartesi', Tuesday: 'Salı', Wednesday: 'Çarşamba', Thursday: 'Perşembe', Friday: 'Cuma' };

// ── Auth ─────────────────────────────────────────────────────────
function authHeaders() { return { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + authToken }; }

async function apiFetch(url, opts = {}) {
    opts.headers = { ...authHeaders(), ...(opts.headers || {}) };
    const res = await fetch(url, opts);
    if (res.status === 401) { showLoginScreen(); throw new Error('Auth required'); }
    return res;
}

async function doLogin() {
    const errEl = document.getElementById('loginError');
    errEl.textContent = '';
    try {
        const u = document.getElementById('loginUser').value.trim();
        const p = document.getElementById('loginPass').value;
        if (!u || !p) { errEl.textContent = 'Kullanıcı adı ve şifre giriniz.'; return; }
        const res = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: u, password: p }) });
        const data = await res.json();
        if (data.token) {
            authToken = data.token;
            localStorage.setItem('adminToken', authToken);
            hideLoginScreen();
            loadOrganizations();
        } else {
            errEl.textContent = data.error || 'Giriş başarısız.';
        }
    } catch (e) {
        errEl.textContent = 'Bağlantı hatası: ' + e.message;
    }
}

function doLogout() {
    authToken = '';
    localStorage.removeItem('adminToken');
    showLoginScreen();
}

function showLoginScreen() {
    document.getElementById('loginOverlay').style.display = 'flex';
    document.getElementById('appSidebar').style.display = 'none';
    document.getElementById('appMain').style.display = 'none';
}

function hideLoginScreen() {
    document.getElementById('loginOverlay').style.display = 'none';
    document.getElementById('appSidebar').style.display = 'flex';
    document.getElementById('appMain').style.display = 'block';
}

async function checkAuth() {
    if (!authToken) { showLoginScreen(); return; }
    try {
        const r = await fetch('/api/auth/check', { headers: { 'Authorization': 'Bearer ' + authToken } });
        if (r.ok) { hideLoginScreen(); loadOrganizations(); }
        else showLoginScreen();
    } catch(e) { showLoginScreen(); }
}

// ── Navigation ───────────────────────────────────────────────────
function showPage(page) {
    document.querySelectorAll('.page-content').forEach(function(el) { el.classList.add('d-none'); });
    var target = document.getElementById('page-' + page);
    if (target) target.classList.remove('d-none');
    document.querySelectorAll('.sidebar a').forEach(function(a) { a.classList.remove('active'); });
    var nav = document.getElementById('nav-' + page);
    if (nav) nav.classList.add('active');

    if (page === 'organizations') loadOrganizations();
    if (page === 'admins') { loadOrganizations(); loadAdmins(); }
    if (page === 'dashboard') { loadOrganizations(); setTimeout(loadDashboard, 100); }
    if (page === 'availability') { loadOrganizations(); setTimeout(loadAvailabilityPage, 100); }
    if (page === 'schedule') { loadOrganizations(); }
    if (page === 'logs') { loadOrganizations(); setTimeout(loadErrorLogs, 100); }
}

// ── Organizations ────────────────────────────────────────────────
async function loadOrganizations() {
    var res = await apiFetch('/api/organizations');
    orgs = await res.json();
    renderOrgTable();
    populateOrgSelects();
}

function renderOrgTable() {
    if (orgs.length === 0) { document.getElementById('orgList').innerHTML = '<div class="empty-state">Henüz organizasyon yok.</div>'; return; }
    var html = '<table><tr><th>ID</th><th>Ad</th><th>Kod</th><th>Oluşturulma</th><th></th></tr>';
    orgs.forEach(function(org) {
        html += '<tr><td>' + org.id + '</td><td><strong>' + org.name + '</strong></td><td><span class="badge badge-blue">' + org.code + '</span></td><td>' + new Date(org.created_at).toLocaleDateString('tr-TR') + '</td><td><button class="btn btn-danger btn-sm btn-del-org" data-id="' + org.id + '">Sil</button></td></tr>';
    });
    document.getElementById('orgList').innerHTML = html + '</table>';
}

function populateOrgSelects() {
    var options = orgs.map(function(o) { return '<option value="' + o.id + '">' + o.name + ' (' + o.code + ')</option>'; }).join('');
    ['adminOrg', 'dashOrg', 'schedOrg', 'availOrg'].forEach(function(id) {
        var el = document.getElementById(id);
        if (el) el.innerHTML = options;
    });
    var logEl = document.getElementById('logOrg');
    if (logEl) logEl.innerHTML = '<option value="">Tüm Organizasyonlar</option>' + options;
}

async function addOrganization() {
    var name = document.getElementById('orgName').value.trim();
    var code = document.getElementById('orgCode').value.trim();
    if (!name || !code) return showAlert('orgAlert', 'Ad ve kod gereklidir.', 'error');
    var res = await apiFetch('/api/organizations', { method: 'POST', body: JSON.stringify({ name: name, code: code }) });
    var data = await res.json();
    if (data.error) return showAlert('orgAlert', data.error, 'error');
    showAlert('orgAlert', '"' + name + '" organizasyonu oluşturuldu!', 'success');
    document.getElementById('orgName').value = '';
    document.getElementById('orgCode').value = '';
    loadOrganizations();
}

async function deleteOrg(id) {
    if (!confirm('Bu organizasyonu silmek istediğinize emin misiniz? Tüm veriler silinecek!')) return;
    await apiFetch('/api/organizations/' + id, { method: 'DELETE' });
    loadOrganizations();
}

// ── Admins ───────────────────────────────────────────────────────
async function loadAdmins() {
    var res = await apiFetch('/api/admins');
    var admins = await res.json();
    if (admins.length === 0) { document.getElementById('adminList').innerHTML = '<div class="empty-state">Henüz admin yok.</div>'; return; }
    var html = '<table><tr><th>Kullanıcı Adı</th><th>Organizasyon</th><th>Şifre Değişmeli</th><th>Oluşturulma</th><th>İşlemler</th></tr>';
    admins.forEach(function(a) {
        var orgName = a.organizations ? a.organizations.name : '—';
        html += '<tr><td><strong>' + a.username + '</strong></td><td>' + orgName + '</td><td>' + (a.must_change_password ? '<span class="badge badge-orange">Evet</span>' : '<span class="badge badge-green">Hayır</span>') + '</td><td>' + new Date(a.created_at).toLocaleDateString('tr-TR') + '</td><td><button class="btn btn-outline-secondary btn-sm btn-reset-pw" data-id="' + a.id + '">Şifre Sıfırla</button> <button class="btn btn-danger btn-sm btn-del-admin" data-id="' + a.id + '">Sil</button></td></tr>';
    });
    document.getElementById('adminList').innerHTML = html + '</table>';
}

async function addAdmin() {
    var orgId = document.getElementById('adminOrg').value;
    var username = document.getElementById('adminUsername').value.trim();
    var password = document.getElementById('adminPassword').value.trim();
    if (!username || !password || !orgId) return showAlert('adminAlert', 'Tüm alanlar gereklidir.', 'error');
    if (password.length < 6) return showAlert('adminAlert', 'Şifre en az 6 karakter olmalıdır.', 'error');
    var res = await apiFetch('/api/admins', { method: 'POST', body: JSON.stringify({ username: username, password: password, orgId: orgId, mustChangePassword: true }) });
    var data = await res.json();
    if (data.error) return showAlert('adminAlert', data.error, 'error');
    showAlert('adminAlert', '"' + username + '" admin hesabı oluşturuldu!', 'success');
    document.getElementById('adminUsername').value = '';
    document.getElementById('adminPassword').value = '';
    loadAdmins();
}

async function deleteAdmin(id) {
    if (!confirm('Bu admin hesabını silmek istediğinize emin misiniz?')) return;
    await apiFetch('/api/admins/' + id, { method: 'DELETE' });
    loadAdmins();
}

async function resetPassword(id) {
    var newPw = prompt('Yeni şifre girin (min 6 karakter):');
    if (!newPw || newPw.length < 6) { alert('Şifre en az 6 karakter olmalıdır.'); return; }
    await apiFetch('/api/admins/' + id + '/reset-password', { method: 'PUT', body: JSON.stringify({ password: newPw }) });
    alert('Şifre sıfırlandı.');
    loadAdmins();
}

// ── Dashboard ────────────────────────────────────────────────────
async function loadDashboard() {
    var orgId = document.getElementById('dashOrg').value;
    if (!orgId) return;
    var results = await Promise.all([
        apiFetch('/api/stats/' + orgId), apiFetch('/api/departments/' + orgId),
        apiFetch('/api/lecturers/' + orgId), apiFetch('/api/courses/' + orgId), apiFetch('/api/classrooms/' + orgId),
        apiFetch('/api/settings/' + orgId)
    ]);
    var stats = await results[0].json();
    document.getElementById('dashStats').innerHTML =
        '<div class="stat-card"><div class="number">' + stats.departments + '</div><div class="label">Bölüm</div></div>' +
        '<div class="stat-card"><div class="number">' + stats.lecturers + '</div><div class="label">Akademisyen</div></div>' +
        '<div class="stat-card"><div class="number">' + stats.courses + '</div><div class="label">Ders</div></div>' +
        '<div class="stat-card"><div class="number">' + stats.classrooms + '</div><div class="label">Sınıf</div></div>' +
        '<div class="stat-card"><div class="number">' + stats.offerings + '</div><div class="label">Açılan Ders</div></div>' +
        '<div class="stat-card"><div class="number">' + stats.scheduleEntries + '</div><div class="label">Program Kaydı</div></div>';

    var settings = await results[5].json();
    document.getElementById('setTimeStep').value = settings.time_step_minutes || 10;
    document.getElementById('setDayStart').value = settings.day_start || '08:00';
    document.getElementById('setDayEnd').value = settings.day_end || '18:00';

    var depts = await results[1].json();
    populateCourseDeptSelect(depts);
    populateLecDeptSelect(depts);
    populateClassroomDeptSelect(depts);
    if (depts.length === 0) { document.getElementById('deptList').innerHTML = '<div class="empty-state">Bölüm yok.</div>'; }
    else { document.getElementById('deptList').innerHTML = '<table><tr><th>Ad</th><th>ID</th><th></th></tr>' + depts.map(function(d) { return '<tr><td>' + d.name + '</td><td>' + d.id + '</td><td><button class="btn btn-danger btn-sm btn-del-dept" data-id="' + d.id + '">Sil</button></td></tr>'; }).join('') + '</table>'; }

    var lecs = await results[2].json();
    if (lecs.length === 0) { document.getElementById('lecturerList').innerHTML = '<div class="empty-state">Akademisyen yok.</div>'; }
    else {
        var html = '<table><tr><th>Ad Soyad</th><th>Bölüm</th><th>Kullanıcı</th><th>E-posta</th><th>İşlemler</th></tr>';
        lecs.forEach(function(l) { 
            var deptName = l.departments ? l.departments.name : '—';
            var encodedData = encodeURIComponent(JSON.stringify(l));
            html += '<tr><td>' + l.title + ' ' + l.first_name + ' ' + l.last_name + '</td><td>' + deptName + '</td><td><span class="badge badge-blue">@' + (l.users ? l.users.username : '—') + '</span></td><td>' + (l.email || '—') + '</td><td><button class="btn btn-warning btn-sm btn-edit-lec me-1" data-data="' + encodedData + '">Düzenle</button><button class="btn btn-secondary btn-sm btn-reset-lec-pw me-1" data-id="' + l.id + '">Şifre Sıfırla</button><button class="btn btn-danger btn-sm btn-del-lec" data-id="' + l.id + '">Sil</button></td></tr>'; 
        });
        document.getElementById('lecturerList').innerHTML = html + '</table>';
    }

    var courses = await results[3].json();
    if (courses.length === 0) { document.getElementById('courseList').innerHTML = '<div class="empty-state">Ders yok.</div>'; }
    else {
        var html2 = '<table><tr><th>Kod</th><th>Ad</th><th>Teori/Lab</th><th>Kredi</th><th>Bölüm</th><th>İşlemler</th></tr>';
        courses.forEach(function(c) { 
            var deptName = c.departments ? c.departments.name : '—';
            var encodedData = encodeURIComponent(JSON.stringify(c));
            html2 += '<tr><td><strong>' + c.code + '</strong></td><td>' + c.name + '</td><td>' + c.theory_hours + 'T / ' + c.lab_hours + 'L</td><td>' + c.credits + '</td><td>' + deptName + '</td><td><button class="btn btn-warning btn-sm btn-edit-course me-1" data-data="' + encodedData + '">Düzenle</button><button class="btn btn-danger btn-sm btn-del-course" data-id="' + c.id + '">Sil</button></td></tr>'; 
        });
        document.getElementById('courseList').innerHTML = html2 + '</table>';
    }

    var classrooms = await results[4].json();
    if (classrooms.length === 0) { document.getElementById('classroomList').innerHTML = '<div class="empty-state">Sınıf yok.</div>'; }
    else {
        var html3 = '<table><tr><th>Oda Kodu</th><th>Kapasite</th><th>Tür</th><th>Bölüm</th><th>İşlemler</th></tr>';
        classrooms.forEach(function(c) { 
            var deptName = c.departments ? c.departments.name : '—';
            var encodedData = encodeURIComponent(JSON.stringify(c));
            html3 += '<tr><td><strong>' + c.room_code + '</strong></td><td>' + c.capacity + '</td><td><span class="badge ' + (c.type === 'lab' ? 'badge-purple' : 'badge-blue') + '">' + c.type + '</span></td><td>' + deptName + '</td><td><button class="btn btn-warning btn-sm btn-edit-classroom me-1" data-data="' + encodedData + '">Düzenle</button><button class="btn btn-danger btn-sm btn-del-classroom" data-id="' + c.id + '">Sil</button></td></tr>'; 
        });
        document.getElementById('classroomList').innerHTML = html3 + '</table>';
    }
}

async function addDepartment() {
    var orgId = document.getElementById('dashOrg').value;
    var name = document.getElementById('newDeptName').value.trim();
    if (!name) return;
    await apiFetch('/api/departments', { method: 'POST', body: JSON.stringify({ name: name, orgId: orgId }) });
    document.getElementById('newDeptName').value = '';
    loadDashboard();
}

async function deleteDept(id) {
    if (!confirm('Bu bölümü silmek istediğinize emin misiniz?')) return;
    await apiFetch('/api/departments/' + id, { method: 'DELETE' });
    loadDashboard();
}

function populateCourseDeptSelect(depts) {
    var el = document.getElementById('courseDept');
    if (!el) return;
    el.innerHTML = '<option value="">—</option>' + depts.map(function(d) { return '<option value="' + d.id + '">' + d.name + '</option>'; }).join('');
}
function populateLecDeptSelect(depts) {
    var el = document.getElementById('lecDept');
    if (!el) return;
    el.innerHTML = '<option value="">—</option>' + depts.map(function(d) { return '<option value="' + d.id + '">' + d.name + '</option>'; }).join('');
}
function populateClassroomDeptSelect(depts) {
    var el = document.getElementById('classroomDept');
    if (!el) return;
    el.innerHTML = '<option value="">—</option>' + depts.map(function(d) { return '<option value="' + d.id + '">' + d.name + '</option>'; }).join('');
}

async function saveSettings() {
    var orgId = document.getElementById('dashOrg').value;
    if (!orgId) return;
    var timeStep = document.getElementById('setTimeStep').value;
    var dayStart = document.getElementById('setDayStart').value;
    var dayEnd = document.getElementById('setDayEnd').value;
    var res = await apiFetch('/api/settings/' + orgId, {
        method: 'PUT',
        body: JSON.stringify({ timeStepMinutes: timeStep, dayStart: dayStart, dayEnd: dayEnd })
    });
    var data = await res.json();
    if (data.error) return showAlert('settingsAlert', data.error, 'error');
    showAlert('settingsAlert', 'Kurum ayarları kaydedildi!', 'success');
}

async function addLecturer() {
    var orgId = document.getElementById('dashOrg').value;
    var title = document.getElementById('lecTitle').value.trim();
    var firstName = document.getElementById('lecFirst').value.trim();
    var lastName = document.getElementById('lecLast').value.trim();
    var email = document.getElementById('lecEmail').value.trim();
    var departmentId = document.getElementById('lecDept').value;
    if (!firstName || !lastName) return showAlert('lecAlert', 'Ad ve soyad gereklidir.', 'error');
    
    var res = await apiFetch('/api/lecturers', { 
        method: 'POST', 
        body: JSON.stringify({ orgId: orgId, title: title, firstName: firstName, lastName: lastName, email: email, departmentId: departmentId || null }) 
    });
    var data = await res.json();
    if (data.error) return showAlert('lecAlert', data.error, 'error');
    
    document.getElementById('lecTitle').value = '';
    document.getElementById('lecFirst').value = '';
    document.getElementById('lecLast').value = '';
    document.getElementById('lecEmail').value = '';
    
    if (data.generatedCredentials) {
        alert('Akademisyen başarıyla eklendi!\n\nKullanıcı Adı: ' + data.generatedCredentials.username + '\nŞifre: ' + data.generatedCredentials.password + '\n\nLütfen bu bilgileri hocayla paylaşın. İlk girişte şifre değiştirmesi istenecektir.');
    }
    loadDashboard();
}

async function resetLecturerPassword(id) {
    if (!confirm('Bu akademisyenin şifresini sıfırlamak istediğinize emin misiniz?')) return;
    var res = await apiFetch('/api/lecturers/' + id + '/reset-password', { method: 'POST' });
    var data = await res.json();
    if (data.error) return alert('Hata: ' + data.error);
    alert('Şifre başarıyla sıfırlandı!\n\nKullanıcı Adı: ' + data.generatedCredentials.username + '\nYeni Şifre: ' + data.generatedCredentials.password + '\n\nLütfen bu bilgileri hocayla paylaşın.');
}

async function deleteLecturer(id) {
    if (!confirm('Bu akademisyeni silmek istediğinize emin misiniz?')) return;
    await apiFetch('/api/lecturers/' + id, { method: 'DELETE' });
    loadDashboard();
}

async function deleteCourse(id) {
    if (!confirm('Bu dersi silmek istediğinize emin misiniz?')) return;
    await apiFetch('/api/courses/' + id, { method: 'DELETE' });
    loadDashboard();
}

async function addClassroom() {
    var orgId = document.getElementById('dashOrg').value;
    var roomCode = document.getElementById('classroomCode').value.trim();
    var capacity = document.getElementById('classroomCapacity').value;
    var type = document.getElementById('classroomType').value;
    var departmentId = document.getElementById('classroomDept').value;
    if (!roomCode || !capacity) return showAlert('classroomAlert', 'Oda kodu ve kapasite gereklidir.', 'error');
    var res = await apiFetch('/api/classrooms', { method: 'POST', body: JSON.stringify({ orgId: orgId, roomCode: roomCode, capacity: capacity, type: type, departmentId: departmentId || null }) });
    var data = await res.json();
    if (data.error) return showAlert('classroomAlert', data.error, 'error');
    showAlert('classroomAlert', roomCode + ' sınıfı eklendi!', 'success');
    document.getElementById('classroomCode').value = '';
    document.getElementById('classroomCapacity').value = '30';
    loadDashboard();
}

async function deleteClassroom(id) {
    if (!confirm('Bu sınıfı silmek istediğinize emin misiniz?')) return;
    await apiFetch('/api/classrooms/' + id, { method: 'DELETE' });
    loadDashboard();
}

// ── Excel Import/Export ──────────────────────────────────────────
async function importExcel(type, file, alertId) {
    var orgId = document.getElementById('dashOrg').value;
    if (!orgId) { showAlert(alertId, 'Önce organizasyon seçin.', 'error'); return; }
    var formData = new FormData();
    formData.append('file', file);
    try {
        var res = await fetch('/api/import/' + type + '/' + orgId, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + authToken },
            body: formData
        });
        var data = await res.json();
        if (data.error) { showAlert(alertId, data.error, 'error'); return; }
        var msg = data.message || (data.inserted + ' kayıt eklendi.');
        if (data.errors && data.errors.length > 0) msg += ' Hatalar: ' + data.errors.join(', ');
        showAlert(alertId, msg, 'success');
        loadDashboard();
    } catch(e) { showAlert(alertId, 'Yükleme hatası: ' + e.message, 'error'); }
}

function exportExcel(type) {
    var orgId = document.getElementById('dashOrg').value;
    if (!orgId) { alert('Önce organizasyon seçin.'); return; }
    window.open('/api/export/' + type + '/' + orgId + '?token=' + authToken, '_blank');
}

async function addCourse() {
    var orgId = document.getElementById('dashOrg').value;
    var code = document.getElementById('courseCode').value.trim();
    var name = document.getElementById('courseName').value.trim();
    var departmentId = document.getElementById('courseDept').value;
    var theoryHours = document.getElementById('courseTheory').value;
    var labHours = document.getElementById('courseLab').value;
    var credits = document.getElementById('courseCredits').value;
    if (!orgId) return showAlert('courseAlert', 'Organizasyon seçin.', 'error');
    if (!code || !name) return showAlert('courseAlert', 'Kod ve ad gereklidir.', 'error');
    var res = await apiFetch('/api/courses', { method: 'POST', body: JSON.stringify({ orgId: orgId, code: code, name: name, theoryHours: theoryHours, labHours: labHours, credits: credits, departmentId: departmentId || null }) });
    var data = await res.json();
    if (data.error) return showAlert('courseAlert', data.error, 'error');
    showAlert('courseAlert', '"' + code + '" dersi eklendi.', 'success');
    document.getElementById('courseCode').value = '';
    document.getElementById('courseName').value = '';
    document.getElementById('courseTheory').value = '0';
    document.getElementById('courseLab').value = '0';
    document.getElementById('courseCredits').value = '0';
    loadDashboard();
}

// ── Availability ─────────────────────────────────────────────────
async function loadAvailabilityPage() {
    var orgId = document.getElementById('availOrg').value;
    if (!orgId) return;
    var res = await apiFetch('/api/lecturers/' + orgId);
    var lecs = await res.json();
    var select = document.getElementById('availLecturer');
    select.innerHTML = '<option value="">— Akademisyen seçin —</option>' + lecs.map(function(l) { return '<option value="' + l.id + '">' + l.title + ' ' + l.first_name + ' ' + l.last_name + '</option>'; }).join('');
    document.getElementById('availabilityContent').style.display = 'none';
}

async function loadLecturerAvailability() {
    var lecId = document.getElementById('availLecturer').value;
    var orgId = document.getElementById('availOrg').value;
    if (!lecId) { document.getElementById('availabilityContent').style.display = 'none'; return; }
    document.getElementById('availabilityContent').style.display = 'block';
    var res = await apiFetch('/api/availability/' + orgId + '?lecturerId=' + lecId);
    currentAvailability = await res.json();
    renderAvailability();
}

function renderAvailability() {
    if (currentAvailability.length === 0) { document.getElementById('availList').innerHTML = '<div class="empty-state">Müsaitlik kaydı yok.</div>'; return; }
    var grouped = {};
    currentAvailability.forEach(function(a) { if (!grouped[a.day]) grouped[a.day] = []; grouped[a.day].push(a); });
    var html = '';
    ['Monday','Tuesday','Wednesday','Thursday','Friday'].forEach(function(day) {
        if (!grouped[day]) return;
        html += '<h4 style="margin:12px 0 8px;font-size:14px;">' + dayNames[day] + '</h4><div class="availability-grid">';
        grouped[day].forEach(function(a) { html += '<div class="avail-slot"><span class="time">' + a.start_time + ' - ' + a.end_time + '</span><span class="del-btn btn-del-avail" data-id="' + a.id + '">✕</span></div>'; });
        html += '</div>';
    });
    document.getElementById('availList').innerHTML = html;
}

async function addAvailability() {
    var lecId = document.getElementById('availLecturer').value;
    var orgId = document.getElementById('availOrg').value;
    var day = document.getElementById('availDay').value;
    var startTime = document.getElementById('availStart').value;
    var endTime = document.getElementById('availEnd').value;
    if (!lecId || !day || !startTime || !endTime) return showAlert('availAlert', 'Tüm alanları doldurun.', 'error');
    if (startTime >= endTime) return showAlert('availAlert', 'Bitiş saati başlangıçtan sonra olmalıdır.', 'error');
    var res = await apiFetch('/api/availability', { method: 'POST', body: JSON.stringify({ lecturerId: lecId, day: day, startTime: startTime, endTime: endTime, orgId: orgId }) });
    var data = await res.json();
    if (data.error) return showAlert('availAlert', data.error, 'error');
    showAlert('availAlert', 'Müsaitlik eklendi.', 'success');
    loadLecturerAvailability();
}

async function deleteAvailability(id) {
    await apiFetch('/api/availability/' + id, { method: 'DELETE' });
    loadLecturerAvailability();
}

// ── Schedule View ────────────────────────────────────────────────
async function loadSchedule() {
    var orgId = document.getElementById('schedOrg').value;
    if (!orgId) return;
    var res = await apiFetch('/api/schedule/' + orgId);
    allSchedule = await res.json();
    filterDay('all');
}

function filterDay(day) {
    document.querySelectorAll('.day-tab').forEach(function(t) { t.classList.remove('active'); });
    var activeTab = document.querySelector('.day-tab[data-day="' + day + '"]');
    if (activeTab) activeTab.classList.add('active');
    var filtered = day === 'all' ? allSchedule : allSchedule.filter(function(e) { return e.day === day; });
    renderScheduleTable(filtered);
}

function renderScheduleTable(entries) {
    if (entries.length === 0) { document.getElementById('scheduleTable').innerHTML = '<div class="empty-state">Program kaydı bulunamadı.</div>'; return; }
    var html = '<table><tr><th>Gün</th><th>Saat</th><th>Ders</th><th>Akademisyen</th><th>Sınıf</th><th></th></tr>';
    entries.forEach(function(e) {
        var course = e.offerings && e.offerings.courses ? e.offerings.courses.code + ' — ' + e.offerings.courses.name : '—';
        var lecturer = e.lecturers ? e.lecturers.title + ' ' + e.lecturers.first_name + ' ' + e.lecturers.last_name : '—';
        var classroom = e.classrooms ? e.classrooms.room_code : '—';
        html += '<tr><td><span class="badge badge-blue">' + (dayNames[e.day] || e.day) + '</span></td><td><strong>' + e.start_time + ' - ' + e.end_time + '</strong></td><td>' + course + '</td><td>' + lecturer + '</td><td>' + classroom + '</td><td><button class="btn btn-danger btn-sm btn-del-sched" data-id="' + e.id + '">Sil</button></td></tr>';
    });
    document.getElementById('scheduleTable').innerHTML = html + '</table>';
}

async function deleteScheduleEntry(id) {
    if (!confirm('Bu program kaydını silmek istediğinize emin misiniz?')) return;
    await apiFetch('/api/schedule/' + id, { method: 'DELETE' });
    loadSchedule();
}

// ── Error Logs ───────────────────────────────────────────────────
async function loadErrorLogs() {
    var orgId = document.getElementById('logOrg').value;
    var url = orgId ? '/api/error-logs?orgId=' + orgId : '/api/error-logs';
    var res = await apiFetch(url);
    var data = await res.json();
    renderErrorLogs(data);
}

function renderErrorLogs(logs) {
    if (!Array.isArray(logs)) { document.getElementById('logList').innerHTML = '<div class="empty-state">Loglar alınamadı.</div>'; return; }
    if (logs.length === 0) { document.getElementById('logList').innerHTML = '<div class="empty-state">Henüz log yok.</div>'; return; }
    var html = '<table><tr><th>Tarih</th><th>Org</th><th>Kullanıcı</th><th>Ekran</th><th>Aksiyon</th><th>Mesaj</th><th>Cihaz</th><th>Detay</th></tr>';
    logs.forEach(function(l) {
        var orgName = l.organizations ? l.organizations.name : (l.org_id || '—');
        var username = l.username || (l.user_id ? l.user_id.slice(0, 8) + '…' : '—');
        var action = l.action || '—';
        var device = [l.device_model, l.os_version, l.app_version].filter(Boolean).join(' | ') || '—';
        var message = escapeHtml(l.message || '');
        var stack = l.stack_trace ? '<details><summary>Stack</summary><pre>' + escapeHtml(l.stack_trace) + '</pre></details>' : '—';
        html += '<tr><td>' + new Date(l.created_at).toLocaleString('tr-TR') + '</td><td>' + escapeHtml(String(orgName)) + '</td><td>' + escapeHtml(String(username)) + '</td><td>' + escapeHtml(String(l.screen || '—')) + '</td><td>' + escapeHtml(String(action)) + '</td><td>' + message + '</td><td>' + escapeHtml(String(device)) + '</td><td>' + stack + '</td></tr>';
    });
    document.getElementById('logList').innerHTML = html + '</table>';
}

// ── Edit Logic ────────────────────────────────────────────────────
function openEditModal(type, data) {
    currentEditType = type;
    currentEditId = data.id;
    var body = document.getElementById('editModalBody');
    var html = '';

    if (type === 'lecturer') {
        html = '<label class="form-label">Unvan</label><input type="text" id="editLecTitle" class="form-control mb-2" value="' + (data.title || '') + '">' +
               '<label class="form-label">Ad</label><input type="text" id="editLecFirst" class="form-control mb-2" value="' + data.first_name + '">' +
               '<label class="form-label">Soyad</label><input type="text" id="editLecLast" class="form-control mb-2" value="' + data.last_name + '">' +
               '<label class="form-label">E-posta</label><input type="text" id="editLecEmail" class="form-control mb-2" value="' + (data.email || '') + '">';
    } else if (type === 'course') {
        html = '<label class="form-label">Kod</label><input type="text" id="editCourseCode" class="form-control mb-2" value="' + data.code + '">' +
               '<label class="form-label">Ad</label><input type="text" id="editCourseName" class="form-control mb-2" value="' + data.name + '">' +
               '<label class="form-label">Teori</label><input type="number" id="editCourseTheory" class="form-control mb-2" value="' + data.theory_hours + '">' +
               '<label class="form-label">Lab</label><input type="number" id="editCourseLab" class="form-control mb-2" value="' + data.lab_hours + '">' +
               '<label class="form-label">Kredi</label><input type="number" id="editCourseCredits" class="form-control mb-2" value="' + data.credits + '">';
    } else if (type === 'classroom') {
        html = '<label class="form-label">Oda Kodu</label><input type="text" id="editRoomCode" class="form-control mb-2" value="' + data.room_code + '">' +
               '<label class="form-label">Kapasite</label><input type="number" id="editRoomCap" class="form-control mb-2" value="' + data.capacity + '">';
    }
    
    body.innerHTML = html;
    var myModal = new bootstrap.Modal(document.getElementById('editModal'));
    myModal.show();
}

document.getElementById('btnSaveEdit').addEventListener('click', async function() {
    var endpoint = '';
    var payload = {};

    if (currentEditType === 'lecturer') {
        endpoint = '/api/lecturers/' + currentEditId;
        payload = {
            title: document.getElementById('editLecTitle').value,
            firstName: document.getElementById('editLecFirst').value,
            lastName: document.getElementById('editLecLast').value,
            email: document.getElementById('editLecEmail').value
        };
    } else if (currentEditType === 'course') {
        endpoint = '/api/courses/' + currentEditId;
        payload = {
            code: document.getElementById('editCourseCode').value,
            name: document.getElementById('editCourseName').value,
            theoryHours: document.getElementById('editCourseTheory').value,
            labHours: document.getElementById('editCourseLab').value,
            credits: document.getElementById('editCourseCredits').value
        };
    } else if (currentEditType === 'classroom') {
        endpoint = '/api/classrooms/' + currentEditId;
        payload = {
            roomCode: document.getElementById('editRoomCode').value,
            capacity: document.getElementById('editRoomCap').value
        };
    }

    try {
        await apiFetch(endpoint, { method: 'PUT', body: JSON.stringify(payload) });
        bootstrap.Modal.getInstance(document.getElementById('editModal')).hide();
        loadDashboard();
    } catch (e) {
        alert('Kaydetme hatası: ' + e.message);
    }
});

// ── Helpers ──────────────────────────────────────────────────────
function showAlert(elementId, message, type) {
    var el = document.getElementById(elementId);
    el.innerHTML = '<div class="alert-box ' + type + '">' + message + '</div>';
    setTimeout(function() { if (el) el.innerHTML = ''; }, 4000);
}

function escapeHtml(value) {
    return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// ── Event Delegation & Init ──────────────────────────────────────
document.addEventListener('DOMContentLoaded', function() {
    // Login form
    document.getElementById('loginForm').addEventListener('submit', function(e) { e.preventDefault(); doLogin(); });

    // Logout
    document.getElementById('btnLogout').addEventListener('click', doLogout);

    // Sidebar nav
    document.querySelectorAll('.sidebar a[data-page]').forEach(function(a) {
        a.addEventListener('click', function(e) { e.preventDefault(); showPage(this.getAttribute('data-page')); });
    });

    // Dashboard org select
    document.getElementById('dashOrg').addEventListener('change', loadDashboard);
    document.getElementById('schedOrg').addEventListener('change', loadSchedule);
    document.getElementById('availOrg').addEventListener('change', loadAvailabilityPage);
    document.getElementById('availLecturer').addEventListener('change', loadLecturerAvailability);
    document.getElementById('logOrg').addEventListener('change', loadErrorLogs);

    // Buttons
    document.getElementById('btnAddOrg').addEventListener('click', addOrganization);
    document.getElementById('btnAddAdmin').addEventListener('click', addAdmin);
    document.getElementById('btnAddDept').addEventListener('click', addDepartment);
    document.getElementById('btnAddCourse').addEventListener('click', addCourse);
    document.getElementById('btnAddAvail').addEventListener('click', addAvailability);
    document.getElementById('btnAddLec').addEventListener('click', addLecturer);
    document.getElementById('btnAddClassroom').addEventListener('click', addClassroom);
    document.getElementById('btnSaveSettings').addEventListener('click', saveSettings);

    // Excel Export
    document.getElementById('btnExportLecturers').addEventListener('click', function() { exportExcel('lecturers'); });
    document.getElementById('btnExportCourses').addEventListener('click', function() { exportExcel('courses'); });
    document.getElementById('btnExportClassrooms').addEventListener('click', function() { exportExcel('classrooms'); });

    // Excel Import
    document.getElementById('importLecturers').addEventListener('change', function(e) { if (e.target.files[0]) importExcel('lecturers', e.target.files[0], 'importLecAlert'); e.target.value = ''; });
    document.getElementById('importCourses').addEventListener('change', function(e) { if (e.target.files[0]) importExcel('courses', e.target.files[0], 'importCourseAlert'); e.target.value = ''; });
    document.getElementById('importClassrooms').addEventListener('change', function(e) { if (e.target.files[0]) importExcel('classrooms', e.target.files[0], 'importClassroomAlert'); e.target.value = ''; });

    // Day tabs
    document.getElementById('dayTabs').addEventListener('click', function(e) {
        var tab = e.target.closest('.day-tab');
        if (tab) filterDay(tab.getAttribute('data-day'));
    });

    // Delegated clicks for dynamic buttons
    document.addEventListener('click', function(e) {
        var btn;
        if ((btn = e.target.closest('.btn-del-org'))) deleteOrg(btn.getAttribute('data-id'));
        if ((btn = e.target.closest('.btn-del-admin'))) deleteAdmin(btn.getAttribute('data-id'));
        if ((btn = e.target.closest('.btn-reset-pw'))) resetPassword(btn.getAttribute('data-id'));
        if ((btn = e.target.closest('.btn-del-dept'))) deleteDept(btn.getAttribute('data-id'));
        if ((btn = e.target.closest('.btn-del-sched'))) deleteScheduleEntry(btn.getAttribute('data-id'));
        if ((btn = e.target.closest('.btn-del-avail'))) deleteAvailability(btn.getAttribute('data-id'));
        if ((btn = e.target.closest('.btn-del-lec'))) deleteLecturer(btn.getAttribute('data-id'));
        if ((btn = e.target.closest('.btn-reset-lec-pw'))) resetLecturerPassword(btn.getAttribute('data-id'));
        if ((btn = e.target.closest('.btn-del-course'))) deleteCourse(btn.getAttribute('data-id'));
        if ((btn = e.target.closest('.btn-del-classroom'))) deleteClassroom(btn.getAttribute('data-id'));
        
        // Edit actions
        if ((btn = e.target.closest('.btn-edit-lec'))) openEditModal('lecturer', JSON.parse(decodeURIComponent(btn.getAttribute('data-data'))));
        if ((btn = e.target.closest('.btn-edit-course'))) openEditModal('course', JSON.parse(decodeURIComponent(btn.getAttribute('data-data'))));
        if ((btn = e.target.closest('.btn-edit-classroom'))) openEditModal('classroom', JSON.parse(decodeURIComponent(btn.getAttribute('data-data'))));
    });

    // Start
    checkAuth();
});
