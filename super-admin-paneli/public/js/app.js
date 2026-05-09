let orgs=[], allSchedule=[], allOfferings=[], allLecturers=[], allClassrooms=[], allDepts=[], currentAvailability=[], allAvailability=[];
let authToken=localStorage.getItem('adminToken')||'';
let currentEditType=null, currentEditId=null, editingEntryId=null, currentDetailEntry=null;
let schedSettings={day_start:'08:00',day_end:'18:00',time_step_minutes:30};
const DAYS=['Monday','Tuesday','Wednesday','Thursday','Friday'];
const DAY_TR={Monday:'Pazartesi',Tuesday:'Sali',Wednesday:'Carsamba',Thursday:'Persembe',Friday:'Cuma'};
const COLORS=['#1565c0','#2e7d32','#6a1b9a','#e65100','#00838f','#ad1457','#4527a0','#283593','#558b2f','#bf360c','#00695c','#880e4f'];

function authHeaders(){return{'Content-Type':'application/json','Authorization':'Bearer '+authToken}}

// ── Panel error logger — sessizce POST eder, hata fırlatmaz
function sendPanelLog(screen,action,message,stack){try{fetch('/api/log/panel',{method:'POST',headers:authHeaders(),body:JSON.stringify({screen,action,message:String(message).slice(0,2000),stack:stack?String(stack).slice(0,5000):undefined})}).catch(()=>{})}catch(_){}}

async function apiFetch(url,opts={}){
  opts.headers={...authHeaders(),...(opts.headers||{})};
  const r=await fetch(url,opts);
  if(r.status===401){showLoginScreen();throw new Error('Auth')}
  if(r.status>=500){
    const txt=await r.clone().text().catch(()=>'');
    sendPanelLog('apiFetch',opts.method||'GET','HTTP '+r.status+' '+url,txt);
  }
  return r
}

function escapeHtml(v){return String(v).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;')}
function showAlert(id,msg,type){const el=document.getElementById(id);if(!el)return;el.innerHTML='<div class="alert-box '+type+'">'+msg+'</div>';setTimeout(()=>{if(el)el.innerHTML=''},5000)}
function toMin(t){if(!t)return 0;const p=t.split(':');return(parseInt(p[0])||0)*60+(parseInt(p[1])||0)}
function fmtTime(m){return String(Math.floor(m/60)).padStart(2,'0')+':'+String(m%60).padStart(2,'0')}
function colorFor(id){return COLORS[(id||0)%COLORS.length]}
const TERM_TR={Fall:'Guz',Spring:'Bahar',Summer:'Yaz'};

// ── Global org helper ────────────────────────────────────────────────
function getCurrentOrgId(){return document.getElementById('globalOrg').value||''}
function setCurrentOrgId(id){if(!id)return;localStorage.setItem('currentOrgId',String(id));const el=document.getElementById('globalOrg');if(el)el.value=id;['dashOrg','schedOrg','availOrg','offOrg','logOrg','adminOrg'].forEach(sid=>{const s=document.getElementById(sid);if(s)s.value=id})}

// ── Auth
async function doLogin(){const errEl=document.getElementById('loginError');errEl.textContent='';try{const u=document.getElementById('loginUser').value.trim(),p=document.getElementById('loginPass').value;if(!u||!p){errEl.textContent='Giris bilgilerini doldurun.';return}const r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:u,password:p})});const d=await r.json();if(d.token){authToken=d.token;localStorage.setItem('adminToken',authToken);hideLoginScreen();loadOrganizations()}else errEl.textContent=d.error||'Giris basarisiz.'}catch(e){errEl.textContent='Baglanti hatasi.'}}
function resetClientCaches(){orgs=[];allSchedule=[];allOfferings=[];allLecturers=[];allClassrooms=[];allDepts=[];currentAvailability=[];allAvailability=[];currentEditType=null;currentEditId=null;editingEntryId=null;currentDetailEntry=null;}
function doLogout(){resetClientCaches();authToken='';localStorage.removeItem('adminToken');showLoginScreen()}
function onOrgSelectChanged(){const id=getCurrentOrgId();setCurrentOrgId(id);resetClientCaches();const active=document.querySelector('.sidebar a.active');if(active&&active.id&&active.id.startsWith('nav-')){showPage(active.id.substring(4));}}
function showLoginScreen(){document.getElementById('loginOverlay').style.display='flex';document.getElementById('appSidebar').style.display='none';document.getElementById('appMain').style.display='none';document.getElementById('globalOrgBar').style.display='none'}
function hideLoginScreen(){document.getElementById('loginOverlay').style.display='none';document.getElementById('appSidebar').style.display='flex';document.getElementById('appMain').style.display='block';document.getElementById('globalOrgBar').style.display='flex'}
async function checkAuth(){if(!authToken){showLoginScreen();return}try{const r=await fetch('/api/auth/check',{headers:{'Authorization':'Bearer '+authToken}});if(r.ok){hideLoginScreen();loadOrganizations()}else showLoginScreen()}catch(e){showLoginScreen()}}

// ── Nav
function showPage(page){document.querySelectorAll('.page-content').forEach(el=>el.classList.add('d-none'));const t=document.getElementById('page-'+page);if(t)t.classList.remove('d-none');document.querySelectorAll('.sidebar a').forEach(a=>a.classList.remove('active'));const n=document.getElementById('nav-'+page);if(n)n.classList.add('active');
if(page==='organizations')loadOrganizations();
if(page==='admins'){loadOrganizations();loadAdmins()}
if(page==='dashboard'){loadOrganizations();setTimeout(loadDashboard,100)}
if(page==='offerings'){loadOrganizations();setTimeout(loadOfferingsPage,100)}
if(page==='schedule'){loadOrganizations();setTimeout(loadSchedulePage,100)}
if(page==='availability'){loadOrganizations();setTimeout(loadAvailabilityPage,100)}
if(page==='logs'){loadOrganizations();setTimeout(loadErrorLogs,100)}
if(page==='security'){setTimeout(loadSecurityPage,100)}}

// ── Organizations
async function loadOrganizations(){try{const r=await apiFetch('/api/organizations');orgs=await r.json();renderOrgTable();populateOrgSelects()}catch(e){if(e.message!=='Auth')showAlert('orgAlert','Organizasyonlar yuklenemedi: '+e.message,'error')}}
function renderOrgTable(){if(!orgs.length){document.getElementById('orgList').innerHTML='<div class="empty-state">Henuz organizasyon yok.</div>';return}let h='<table><tr><th>ID</th><th>Ad</th><th>Kod</th><th>Tarih</th><th></th></tr>';orgs.forEach(o=>{h+='<tr><td>'+o.id+'</td><td><strong>'+escapeHtml(o.name)+'</strong></td><td><span class="badge-blue">'+escapeHtml(o.code)+'</span></td><td>'+new Date(o.created_at).toLocaleDateString('tr-TR')+'</td><td><button class="btn btn-danger btn-sm btn-del-org" data-id="'+o.id+'">Sil</button></td></tr>'});document.getElementById('orgList').innerHTML=h+'</table>'}
function populateOrgSelects(){
  const opts=orgs.map(o=>'<option value="'+o.id+'">'+escapeHtml(o.name)+'</option>').join('');
  // Global selector
  const gEl=document.getElementById('globalOrg');
  if(gEl){gEl.innerHTML=opts;}
  // Hidden mirror selectors (sayfa fonksiyonları hâlâ bunları okuyabilsin)
  ['adminOrg','dashOrg','schedOrg','availOrg','offOrg'].forEach(id=>{const el=document.getElementById(id);if(el)el.innerHTML=opts});
  const logEl=document.getElementById('logOrg');if(logEl)logEl.innerHTML='<option value="">Tum Org</option>'+opts;
  // Logs sayfası görünür filtre
  const logFilter=document.getElementById('logOrgFilter');if(logFilter)logFilter.innerHTML='<option value="">Tum Organizasyonlar</option>'+opts;
  // Restore veya ilk org seç
  const saved=localStorage.getItem('currentOrgId');
  const firstId=orgs.length?String(orgs[0].id):'';
  const target=saved&&orgs.find(o=>String(o.id)===saved)?saved:firstId;
  if(target)setCurrentOrgId(target);
}

// Hata logları kaynak filtresi (client-side, yeniden fetch etmez)
let _cachedLogData=[];
function filterLogsBySource(){
  const src=document.getElementById('logSourceFilter')?document.getElementById('logSourceFilter').value:'';
  if(!_cachedLogData.length)return;
  const filtered=src==='WEB'?_cachedLogData.filter(l=>l.screen&&l.screen.startsWith('PANEL')):src==='MOBILE'?_cachedLogData.filter(l=>!l.screen||!l.screen.startsWith('PANEL')):_cachedLogData;
  renderLogTable(filtered);
}
async function addOrganization(){const name=document.getElementById('orgName').value.trim(),code=document.getElementById('orgCode').value.trim();if(!name||!code)return showAlert('orgAlert','Ad ve kod gerekli.','error');const r=await apiFetch('/api/organizations',{method:'POST',body:JSON.stringify({name,code})});const d=await r.json();if(d.error)return showAlert('orgAlert',d.error,'error');showAlert('orgAlert','Olusturuldu!','success');document.getElementById('orgName').value='';document.getElementById('orgCode').value='';loadOrganizations()}
async function deleteOrg(id){if(!confirm('Organizasyonu silmek istediginize emin misiniz?'))return;await apiFetch('/api/organizations/'+id,{method:'DELETE'});loadOrganizations()}

// ── Admins
async function loadAdmins(){const r=await apiFetch('/api/admins');const admins=await r.json();if(!admins.length){document.getElementById('adminList').innerHTML='<div class="empty-state">Admin yok.</div>';return}let h='<table><tr><th>Kullanici</th><th>Org</th><th>Sifre</th><th>Tarih</th><th></th></tr>';admins.forEach(a=>{const on=a.organizations?a.organizations.name:'-';h+='<tr><td><strong>'+escapeHtml(a.username)+'</strong></td><td>'+escapeHtml(on)+'</td><td>'+(a.must_change_password?'<span class="badge-orange">Gecici</span>':'<span class="badge-green">OK</span>')+'</td><td>'+new Date(a.created_at).toLocaleDateString('tr-TR')+'</td><td><button class="btn btn-outline-secondary btn-sm btn-reset-pw" data-id="'+a.id+'">Sifre</button> <button class="btn btn-danger btn-sm btn-del-admin" data-id="'+a.id+'">Sil</button></td></tr>'});document.getElementById('adminList').innerHTML=h+'</table>'}
async function addAdmin(){const orgId=document.getElementById('adminOrg').value||getCurrentOrgId(),u=document.getElementById('adminUsername').value.trim(),p=document.getElementById('adminPassword').value.trim();if(!u||!p||!orgId)return showAlert('adminAlert','Tum alanlar gerekli.','error');if(p.length<6)return showAlert('adminAlert','Sifre min 6 karakter.','error');const r=await apiFetch('/api/admins',{method:'POST',body:JSON.stringify({username:u,password:p,orgId,mustChangePassword:true})});const d=await r.json();if(d.error)return showAlert('adminAlert',d.error,'error');const created=d.finalUsername&&d.finalUsername!==u?'Olusturuldu! (Kullanici adi: <strong>'+escapeHtml(d.finalUsername)+'</strong> olarak kaydedildi)':'Olusturuldu!';showAlert('adminAlert',created,'success');document.getElementById('adminUsername').value='';document.getElementById('adminPassword').value='';loadAdmins()}
async function deleteAdmin(id){if(!confirm('Silmek istediginize emin misiniz?'))return;await apiFetch('/api/admins/'+id,{method:'DELETE'});loadAdmins()}
async function resetPassword(id){const pw=prompt('Yeni sifre (min 6):');if(!pw||pw.length<6){alert('Min 6 karakter.');return}await apiFetch('/api/admins/'+id+'/reset-password',{method:'PUT',body:JSON.stringify({password:pw})});alert('Sifre sifirlandi.');loadAdmins()}

// ── Dashboard
async function loadDashboard(){const orgId=getCurrentOrgId();if(!orgId)return;let rStats,rDepts,rLecs,rCourses,rClassrooms,rSettings;try{[rStats,rDepts,rLecs,rCourses,rClassrooms,rSettings]=await Promise.all([apiFetch('/api/stats/'+orgId),apiFetch('/api/departments/'+orgId),apiFetch('/api/lecturers/'+orgId),apiFetch('/api/courses/'+orgId),apiFetch('/api/classrooms/'+orgId),apiFetch('/api/settings/'+orgId)])}catch(e){if(e.message!=='Auth')showAlert('orgAlert','Dashboard yuklenemedi: '+e.message,'error');return;}
const stats=await rStats.json();document.getElementById('dashStats').innerHTML=['departments/Bolum','lecturers/Akademisyen','courses/Ders','classrooms/Sinif','offerings/Acilan Ders','scheduleEntries/Program'].map(s=>{const[k,l]=s.split('/');return'<div class="stat-card"><div class="number">'+(stats[k]||0)+'</div><div class="label">'+l+'</div></div>'}).join('');
const settings=await rSettings.json();document.getElementById('setTimeStep').value=settings.time_step_minutes||10;document.getElementById('setDayStart').value=settings.day_start||'08:00';document.getElementById('setDayEnd').value=settings.day_end||'18:00';
const depts=await rDepts.json();['courseDept','lecDept','classroomDept'].forEach(id=>{const el=document.getElementById(id);if(el)el.innerHTML='<option value="">-</option>'+depts.map(d=>'<option value="'+d.id+'">'+escapeHtml(d.name)+'</option>').join('')});
document.getElementById('countDepts').textContent=depts.length;
document.getElementById('deptList').innerHTML=depts.length?'<table><tr><th>Ad</th><th>ID</th><th></th></tr>'+depts.map(d=>'<tr><td>'+escapeHtml(d.name)+'</td><td>'+d.id+'</td><td><button class="btn btn-danger btn-sm btn-del-dept" data-id="'+d.id+'">Sil</button></td></tr>').join('')+'</table>':'<div class="empty-state">Bolum yok.</div>';
const lecs=await rLecs.json();document.getElementById('countLecs').textContent=lecs.length;
if(!lecs.length)document.getElementById('lecturerList').innerHTML='<div class="empty-state">Akademisyen yok.</div>';
else{let h='<table><tr><th>Ad Soyad</th><th>Bolum</th><th>Kullanici</th><th>Durum</th><th></th></tr>';lecs.forEach(l=>{const dn=l.departments?l.departments.name:'-';const mc=l.users&&l.users.must_change_password;const st=mc?'<span class="status-pending">Gecici</span>':'<span class="status-active">Aktif</span>';const ed=encodeURIComponent(JSON.stringify(l));h+='<tr><td>'+escapeHtml((l.title||'')+' '+l.first_name+' '+l.last_name)+'</td><td>'+escapeHtml(dn)+'</td><td><span class="badge-blue">@'+escapeHtml(l.users?l.users.username:'-')+'</span></td><td>'+st+'</td><td><button class="btn btn-warning btn-sm btn-edit-lec me-1" data-data="'+ed+'">Duzenle</button><button class="btn btn-secondary btn-sm btn-reset-lec-pw me-1" data-id="'+l.id+'">Sifre</button><button class="btn btn-danger btn-sm btn-del-lec" data-id="'+l.id+'">Sil</button></td></tr>'});document.getElementById('lecturerList').innerHTML=h+'</table>'}
const courses=await rCourses.json();document.getElementById('countCourses').textContent=courses.length;
if(!courses.length)document.getElementById('courseList').innerHTML='<div class="empty-state">Ders yok.</div>';
else{let h='<table><tr><th>Kod</th><th>Ad</th><th>T/L</th><th>Kredi</th><th>Bolum</th><th></th></tr>';courses.forEach(c=>{const ed=encodeURIComponent(JSON.stringify(c));h+='<tr><td><strong>'+escapeHtml(c.code)+'</strong></td><td>'+escapeHtml(c.name)+'</td><td>'+c.theory_hours+'T/'+c.lab_hours+'L</td><td>'+c.credits+'</td><td>'+escapeHtml(c.departments?c.departments.name:'-')+'</td><td><button class="btn btn-warning btn-sm btn-edit-course me-1" data-data="'+ed+'">Duzenle</button><button class="btn btn-danger btn-sm btn-del-course" data-id="'+c.id+'">Sil</button></td></tr>'});document.getElementById('courseList').innerHTML=h+'</table>'}
const classrooms=await rClassrooms.json();document.getElementById('countClassrooms').textContent=classrooms.length;
if(!classrooms.length)document.getElementById('classroomList').innerHTML='<div class="empty-state">Sinif yok.</div>';
else{let h='<table><tr><th>Kod</th><th>Kap.</th><th>Tur</th><th>Bolum</th><th></th></tr>';classrooms.forEach(c=>{const ed=encodeURIComponent(JSON.stringify(c));h+='<tr><td><strong>'+escapeHtml(c.room_code)+'</strong></td><td>'+c.capacity+'</td><td><span class="'+(c.type==='lab'?'badge-purple':'badge-blue')+'">'+c.type+'</span></td><td>'+escapeHtml(c.departments?c.departments.name:'-')+'</td><td><button class="btn btn-warning btn-sm btn-edit-classroom me-1" data-data="'+ed+'">Duzenle</button><button class="btn btn-danger btn-sm btn-del-classroom" data-id="'+c.id+'">Sil</button></td></tr>'});document.getElementById('classroomList').innerHTML=h+'</table>'}}

async function saveSettings(){const orgId=getCurrentOrgId();if(!orgId)return;const r=await apiFetch('/api/settings/'+orgId,{method:'PUT',body:JSON.stringify({timeStepMinutes:document.getElementById('setTimeStep').value,dayStart:document.getElementById('setDayStart').value,dayEnd:document.getElementById('setDayEnd').value})});const d=await r.json();if(d.error)return showAlert('settingsAlert',d.error,'error');showAlert('settingsAlert','Kaydedildi!','success')}
async function addDepartment(){const orgId=getCurrentOrgId(),name=document.getElementById('newDeptName').value.trim();if(!name)return;await apiFetch('/api/departments',{method:'POST',body:JSON.stringify({name,orgId})});document.getElementById('newDeptName').value='';loadDashboard()}
async function deleteDept(id){if(!confirm('Silmek istediginize emin misiniz?'))return;await apiFetch('/api/departments/'+id,{method:'DELETE'});loadDashboard()}
async function addLecturer(){const orgId=getCurrentOrgId();const body={orgId,title:document.getElementById('lecTitle').value.trim(),firstName:document.getElementById('lecFirst').value.trim(),lastName:document.getElementById('lecLast').value.trim(),email:document.getElementById('lecEmail').value.trim(),departmentId:document.getElementById('lecDept').value||null};if(!body.firstName||!body.lastName)return showAlert('lecAlert','Ad ve soyad gerekli.','error');const r=await apiFetch('/api/lecturers',{method:'POST',body:JSON.stringify(body)});const d=await r.json();if(d.error)return showAlert('lecAlert',d.error,'error');['lecTitle','lecFirst','lecLast','lecEmail'].forEach(id=>document.getElementById(id).value='');if(d.generatedCredentials)showCredentialBanner(d.generatedCredentials.username,d.generatedCredentials.password,body.firstName+' '+body.lastName);loadDashboard()}
async function resetLecturerPassword(id){if(!confirm('Sifreyi sifirlamak istediginize emin misiniz?'))return;const r=await apiFetch('/api/lecturers/'+id+'/reset-password',{method:'POST'});const d=await r.json();if(d.error)return alert(d.error);showCredentialBanner(d.generatedCredentials.username,d.generatedCredentials.password,'Sifre Sifirlandi');loadDashboard()}
async function deleteLecturer(id){if(!confirm('Silmek istediginize emin misiniz?'))return;const orgId=getCurrentOrgId();await apiFetch('/api/lecturers/'+id+'?orgId='+orgId,{method:'DELETE'});loadDashboard()}
async function deleteCourse(id){if(!confirm('Silmek istediginize emin misiniz?'))return;const orgId=getCurrentOrgId();await apiFetch('/api/courses/'+id+'?orgId='+orgId,{method:'DELETE'});loadDashboard()}
async function addClassroom(){const orgId=getCurrentOrgId();const r=await apiFetch('/api/classrooms',{method:'POST',body:JSON.stringify({orgId,roomCode:document.getElementById('classroomCode').value.trim(),capacity:document.getElementById('classroomCapacity').value,type:document.getElementById('classroomType').value,departmentId:document.getElementById('classroomDept').value||null})});const d=await r.json();if(d.error)return showAlert('classroomAlert',d.error,'error');showAlert('classroomAlert','Eklendi!','success');document.getElementById('classroomCode').value='';loadDashboard()}
async function deleteClassroom(id){if(!confirm('Silmek istediginize emin misiniz?'))return;const orgId=getCurrentOrgId();await apiFetch('/api/classrooms/'+id+'?orgId='+orgId,{method:'DELETE'});loadDashboard()}
async function addCourse(){const orgId=getCurrentOrgId();const r=await apiFetch('/api/courses',{method:'POST',body:JSON.stringify({orgId,code:document.getElementById('courseCode').value.trim(),name:document.getElementById('courseName').value.trim(),theoryHours:document.getElementById('courseTheory').value,labHours:document.getElementById('courseLab').value,credits:document.getElementById('courseCredits').value,departmentId:document.getElementById('courseDept').value||null})});const d=await r.json();if(d.error)return showAlert('courseAlert',d.error,'error');showAlert('courseAlert','Eklendi!','success');document.getElementById('courseCode').value='';document.getElementById('courseName').value='';loadDashboard()}

function showCredentialBanner(username,password,displayName){const t=document.getElementById('lecAlert');if(!t)return;t.innerHTML='<div class="credential-banner"><span class="cred-dismiss" onclick="this.parentElement.remove()">&times;</span><div class="cred-title">'+escapeHtml(displayName)+'</div><div class="cred-row"><span class="cred-label">Kullanici:</span><span class="cred-value">'+escapeHtml(username)+'</span><span style="margin:0 6px;color:#999;">|</span><span class="cred-label">Sifre:</span><span class="cred-value">'+escapeHtml(password)+'</span><button class="btn-copy" onclick="navigator.clipboard.writeText(\''+username+' / '+password+'\');this.textContent=\'Kopyalandi!\';setTimeout(()=>this.textContent=\'Kopyala\',2000)">Kopyala</button></div><div class="cred-note">Ilk giriste sifre degisimi istenecektir.</div></div>';const acc=document.getElementById('accLecturers');if(acc&&!acc.classList.contains('show'))new bootstrap.Collapse(acc,{toggle:true})}

// ── Excel
async function importExcel(type,file,alertId){const orgId=getCurrentOrgId();if(!orgId){showAlert(alertId,'Org secin.','error');return}const fd=new FormData();fd.append('file',file);try{const r=await fetch('/api/import/'+type+'/'+orgId,{method:'POST',headers:{'Authorization':'Bearer '+authToken},body:fd});const d=await r.json();if(d.error){showAlert(alertId,d.error,'error');return}showAlert(alertId,(d.message||d.inserted+' kayit eklendi.'),'success');loadDashboard()}catch(e){showAlert(alertId,e.message,'error')}}
function exportExcel(type){const orgId=getCurrentOrgId();if(!orgId){alert('Org secin.');return}window.open('/api/export/'+type+'/'+orgId+'?token='+authToken,'_blank')}
async function bulkResetPasswords(){const orgId=getCurrentOrgId();if(!orgId)return;if(!confirm('TUM akademisyenlerin sifreleri sifirlanacak!'))return;try{const r=await apiFetch('/api/lecturers/bulk-reset/'+orgId,{method:'POST'});const d=await r.json();if(d.error){alert(d.error);return}if(d.credentials&&d.credentials.length){const csv='﻿'+'Ad,Soyad,Kullanici,Sifre\n'+d.credentials.map(c=>'"'+c.ad+'","'+c.soyad+'","'+c.kullanici_adi+'","'+c.yeni_sifre+'"').join('\n');const blob=new Blob([csv],{type:'text/csv;charset=utf-8;'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='sifreler.csv';a.click();alert(d.reset+' sifre sifirlandi.')}loadDashboard()}catch(e){alert(e.message)}}

// ── Offerings
async function loadOfferingsPage(){const orgId=getCurrentOrgId();if(!orgId)return;let rOff,rCourses,rLecs;try{[rOff,rCourses,rLecs]=await Promise.all([apiFetch('/api/offerings/'+orgId),apiFetch('/api/courses/'+orgId),apiFetch('/api/lecturers/'+orgId)])}catch(e){if(e.message!=='Auth')showAlert('offAlert','Dersler yuklenemedi: '+e.message,'error');return;}
allOfferings=await rOff.json();const courses=await rCourses.json();const lecs=await rLecs.json();
document.getElementById('offCourse').innerHTML=courses.map(c=>'<option value="'+c.id+'">'+escapeHtml(c.code+' - '+c.name)+'</option>').join('');
document.getElementById('offLecturer').innerHTML='<option value="">- Yok -</option>'+lecs.map(l=>'<option value="'+l.id+'">'+escapeHtml((l.title||'')+' '+l.first_name+' '+l.last_name)+'</option>').join('');
renderOfferings()}
function renderOfferings(){if(!allOfferings.length){document.getElementById('offeringList').innerHTML='<div class="empty-state">Acilan ders yok.</div>';return}let h='<table><tr><th>Ders</th><th>Hoca</th><th>Sinif/Sube</th><th>Kont.</th><th>Donem</th><th></th></tr>';allOfferings.forEach(o=>{const code=o.courses?o.courses.code:'-';const name=o.courses?o.courses.name:'';const lec=o.lecturers?(o.lecturers.title||'')+' '+o.lecturers.first_name+' '+o.lecturers.last_name:'<span class="badge-orange">Atanmamis</span>';h+='<tr><td><strong>'+escapeHtml(code)+'</strong> '+escapeHtml(name)+'</td><td>'+lec+'</td><td>'+o.class_year+'/'+escapeHtml(o.section)+'</td><td>'+o.capacity+'</td><td>'+(TERM_TR[o.term]||o.term)+' '+escapeHtml(o.academic_year)+'</td><td><button class="btn btn-danger btn-sm btn-del-offering" data-id="'+o.id+'">Sil</button></td></tr>'});document.getElementById('offeringList').innerHTML=h+'</table>'}
async function addOffering(){const orgId=getCurrentOrgId();const r=await apiFetch('/api/offerings',{method:'POST',body:JSON.stringify({orgId,courseId:document.getElementById('offCourse').value,lecturerId:document.getElementById('offLecturer').value||null,classYear:document.getElementById('offYear').value,section:document.getElementById('offSection').value.trim()||'A',capacity:document.getElementById('offCapacity').value,term:document.getElementById('offTerm').value,academicYear:'2025-2026'})});const d=await r.json();if(d.error)return showAlert('offAlert',d.error,'error');showAlert('offAlert','Ders acildi!','success');loadOfferingsPage()}
async function deleteOffering(id){if(!confirm('Silmek istediginize emin misiniz?'))return;const orgId=getCurrentOrgId();await apiFetch('/api/offerings/'+id+'?orgId='+orgId,{method:'DELETE'});loadOfferingsPage()}

// ══════════════════════════════════════════════════════════════════
// WEEKLY SCHEDULE GRID
// ══════════════════════════════════════════════════════════════════
async function loadSchedulePage(){const orgId=getCurrentOrgId();if(!orgId)return;
let rSched,rSettings,rLecs,rDepts,rClass,rOff,rAvail;try{[rSched,rSettings,rLecs,rDepts,rClass,rOff,rAvail]=await Promise.all([apiFetch('/api/schedule/'+orgId),apiFetch('/api/settings/'+orgId),apiFetch('/api/lecturers/'+orgId),apiFetch('/api/departments/'+orgId),apiFetch('/api/classrooms/'+orgId),apiFetch('/api/offerings/'+orgId),apiFetch('/api/availability/'+orgId)])}catch(e){if(e.message!=='Auth')showAlert('seAlert','Program yuklenemedi: '+e.message,'error');return;}
allSchedule=await rSched.json();schedSettings=await rSettings.json();allLecturers=await rLecs.json();allClassrooms=await rClass.json();allOfferings=await rOff.json();allDepts=await rDepts.json();allAvailability=await rAvail.json();
document.getElementById('filterLecturer').innerHTML='<option value="">Tumu</option>'+allLecturers.map(l=>'<option value="'+l.id+'">'+escapeHtml((l.title||'')+' '+l.first_name+' '+l.last_name)+'</option>').join('');
document.getElementById('filterDept').innerHTML='<option value="">Tumu</option>'+allDepts.map(d=>'<option value="'+d.id+'">'+escapeHtml(d.name)+'</option>').join('');
document.getElementById('filterClassroom').innerHTML='<option value="">Tumu</option>'+allClassrooms.map(c=>'<option value="'+c.id+'">'+escapeHtml(c.room_code)+'</option>').join('');
const years=[...new Set(allOfferings.map(o=>o.class_year))].sort((a,b)=>a-b);
document.getElementById('filterYear').innerHTML='<option value="">Tumu</option>'+years.map(y=>'<option value="'+y+'">'+y+'. Sinif</option>').join('');
const assignedOfferingIds=new Set(allSchedule.map(e=>e.offering_id));const unassigned=allOfferings.filter(o=>!assignedOfferingIds.has(o.id));
const statsEl=document.getElementById('schedStats');if(statsEl)statsEl.innerHTML='<div class="stat-card"><div class="number">'+allSchedule.length+'</div><div class="label">Program Kaydi</div></div><div class="stat-card"><div class="number">'+allOfferings.length+'</div><div class="label">Acilan Ders</div></div><div class="stat-card" style="border-top-color:'+(unassigned.length?'#e65100':'#2e7d32')+'"><div class="number" style="color:'+(unassigned.length?'#e65100':'#2e7d32')+'">'+unassigned.length+'</div><div class="label">Atanmamis</div></div><div class="stat-card"><div class="number">'+allClassrooms.length+'</div><div class="label">Derslik</div></div>';
populateScheduleModal();renderWeeklyGrid()}

function getFilteredSchedule(){let entries=allSchedule;const fLec=document.getElementById('filterLecturer').value;const fDept=document.getElementById('filterDept').value;const fRoom=document.getElementById('filterClassroom').value;const fYear=document.getElementById('filterYear').value;
if(fLec)entries=entries.filter(e=>e.lecturer_id==fLec);
if(fDept)entries=entries.filter(e=>e.offerings&&e.offerings.courses&&e.offerings.courses.department_id==fDept);
if(fRoom)entries=entries.filter(e=>e.classroom_id==fRoom);
if(fYear)entries=entries.filter(e=>e.offerings&&e.offerings.class_year==fYear);
return entries}

function renderWeeklyGrid(){const grid=document.getElementById('weeklyGrid');const dayStart=toMin(schedSettings.day_start||'08:00');const dayEnd=toMin(schedSettings.day_end||'18:00');const totalMin=dayEnd-dayStart;const PX_PER_MIN=1;const totalH=totalMin*PX_PER_MIN;const hours=[];
for(let m=dayStart;m<dayEnd;m+=60)hours.push(m);
let html='<div class="wg-header time-h"></div>';DAYS.forEach(d=>{html+='<div class="wg-header">'+DAY_TR[d]+'</div>'});
hours.forEach(h=>{html+='<div class="wg-time-label" style="height:'+60*PX_PER_MIN+'px">'+fmtTime(h)+'</div>';DAYS.forEach(()=>{html+='<div class="wg-day-col" style="height:'+60*PX_PER_MIN+'px"><div class="hour-line" style="top:0"></div><div class="half-line" style="top:'+30*PX_PER_MIN+'px"></div></div>'})});
grid.innerHTML=html;grid.style.gridTemplateRows='auto repeat('+hours.length+','+60*PX_PER_MIN+'px)';

const entries=getFilteredSchedule();const dayCols={};DAYS.forEach((d,i)=>{const colEls=grid.querySelectorAll('.wg-day-col');const dayEls=[];for(let r=0;r<hours.length;r++)dayEls.push(colEls[r*5+i]);dayCols[d]=dayEls});

entries.forEach(e=>{const cols=dayCols[e.day];if(!cols||!cols.length)return;const sMin=toMin(e.start_time);const eMin=toMin(e.end_time);const top=(sMin-dayStart)*PX_PER_MIN;const h=(eMin-sMin)*PX_PER_MIN;const courseId=e.offerings&&e.offerings.courses?e.offerings.courses.id:0;const code=e.offerings&&e.offerings.courses?e.offerings.courses.code:'?';const cname=e.offerings&&e.offerings.courses?e.offerings.courses.name:'';const lec=e.lecturers?(e.lecturers.title||'')+' '+e.lecturers.first_name+' '+e.lecturers.last_name:'';const room=e.classrooms?e.classrooms.room_code:'';const col=colorFor(courseId);
const card=document.createElement('div');card.className='schedule-card';card.style.cssText='top:'+top+'px;height:'+h+'px;background:'+col+'18;border-left-color:'+col+';color:'+col;card.innerHTML='<div class="sc-code">'+escapeHtml(code)+'</div>'+(h>25?'<div class="sc-info">'+escapeHtml(cname)+'</div>':'')+(h>40?'<div class="sc-info">'+escapeHtml(lec)+'</div>':'')+(h>55?'<div class="sc-room">'+escapeHtml(room)+' | '+e.start_time+'-'+e.end_time+'</div>':'');
card.title=code+' - '+cname+'\n'+lec+'\n'+room+' | '+e.start_time+'-'+e.end_time;card.setAttribute('data-id',e.id);card.addEventListener('click',()=>onScheduleCardClick(e));
const firstCol=cols[0];if(firstCol)firstCol.style.position='relative',firstCol.appendChild(card)});

drawCurrentTimeLine(grid,dayStart,dayEnd,PX_PER_MIN)}

function drawCurrentTimeLine(grid,dayStart,dayEnd,pxPerMin){const existing=grid.querySelector('.current-time-line');if(existing)existing.remove();const now=new Date();const currentMin=now.getHours()*60+now.getMinutes();if(currentMin<dayStart||currentMin>dayEnd)return;const dayOfWeek=now.getDay();if(dayOfWeek<1||dayOfWeek>5)return;
const top=(currentMin-dayStart)*pxPerMin;const headerH=grid.querySelector('.wg-header').offsetHeight;const line=document.createElement('div');line.className='current-time-line';line.style.top=(headerH+top)+'px';grid.appendChild(line)}

function onScheduleCardClick(entry){currentDetailEntry=entry;const code=entry.offerings&&entry.offerings.courses?entry.offerings.courses.code:'?';const name=entry.offerings&&entry.offerings.courses?entry.offerings.courses.name:'';const lec=entry.lecturers?(entry.lecturers.title||'')+' '+entry.lecturers.first_name+' '+entry.lecturers.last_name:'Atanmamis';const room=entry.classrooms?entry.classrooms.room_code:'';const dept=entry.offerings&&entry.offerings.courses&&entry.offerings.courses.departments?entry.offerings.courses.departments.name:'';const col=colorFor(entry.offerings&&entry.offerings.courses?entry.offerings.courses.id:0);
document.getElementById('entryDetailTitle').innerHTML='<span style="color:'+col+'">'+escapeHtml(code)+'</span> — '+escapeHtml(name);
document.getElementById('entryDetailBody').innerHTML='<div style="font-size:13px;line-height:2"><div><i class="bi bi-person"></i> <strong>Hoca:</strong> '+escapeHtml(lec)+'</div><div><i class="bi bi-geo-alt"></i> <strong>Derslik:</strong> '+escapeHtml(room)+'</div><div><i class="bi bi-clock"></i> <strong>Saat:</strong> '+(DAY_TR[entry.day]||entry.day)+' '+entry.start_time+' - '+entry.end_time+'</div>'+(dept?'<div><i class="bi bi-building"></i> <strong>Bolum:</strong> '+escapeHtml(dept)+'</div>':'')+'<div><i class="bi bi-people"></i> <strong>Sinif:</strong> '+(entry.offerings?entry.offerings.class_year+'. sinif / '+escapeHtml(entry.offerings.section)+' subesi':'')+'</div></div>';
new bootstrap.Modal(document.getElementById('entryDetailModal')).show()}

function populateScheduleModal(){document.getElementById('seOffering').innerHTML=allOfferings.map(o=>{const c=o.courses?o.courses.code+' - '+o.courses.name:'Ders #'+o.course_id;const lec=o.lecturers?(o.lecturers.title||'')+' '+o.lecturers.first_name+' '+o.lecturers.last_name:'';return'<option value="'+o.id+'">'+escapeHtml(c)+' ('+o.class_year+'/'+o.section+')'+(lec?' — '+escapeHtml(lec):'')+'</option>'}).join('');
document.getElementById('seLecturer').innerHTML='<option value="">- Atanmamis -</option>'+allLecturers.map(l=>'<option value="'+l.id+'">'+escapeHtml((l.title||'')+' '+l.first_name+' '+l.last_name)+'</option>').join('');
document.getElementById('seClassroom').innerHTML=allClassrooms.map(c=>'<option value="'+c.id+'">'+escapeHtml(c.room_code)+' ('+c.capacity+', '+c.type+')</option>').join('');
onOfferingChange()}

function onOfferingChange(){const offId=parseInt(document.getElementById('seOffering').value);const off=allOfferings.find(o=>o.id===offId);const infoEl=document.getElementById('seOfferingInfo');
if(!off){if(infoEl)infoEl.style.display='none';return}
if(off.lecturer_id){document.getElementById('seLecturer').value=off.lecturer_id}
const th=off.courses?off.courses.theory_hours:0;const lh=off.courses?off.courses.lab_hours:0;const dur=(th+lh)||1;const dept=off.courses&&off.courses.departments?off.courses.departments.name:'';
const endTime=fmtTime(toMin(document.getElementById('seStart').value)+dur*60);document.getElementById('seEnd').value=endTime;
if(infoEl){infoEl.style.display='block';infoEl.innerHTML='<div class="alert-box" style="background:#e3f2fd;color:#1565c0;padding:8px 12px;font-size:12px"><strong>'+escapeHtml(off.courses?off.courses.code:'')+'</strong> — '+(th?th+'T':'')+(lh?'+'+lh+'L':'')+' = '+dur+' saat'+(dept?' | '+escapeHtml(dept):'')+' | '+off.class_year+'. sinif '+escapeHtml(off.section)+' | Kontenjan: '+off.capacity+(off.lecturers?' | Eslesen hoca: '+escapeHtml((off.lecturers.title||'')+' '+off.lecturers.first_name+' '+off.lecturers.last_name):'')+'</div>'}}

function checkConflicts(){const warnEl=document.getElementById('seConflictWarning');if(!warnEl)return;
const day=document.getElementById('seDay').value;const st=document.getElementById('seStart').value;const et=document.getElementById('seEnd').value;const lecId=parseInt(document.getElementById('seLecturer').value)||null;const crId=parseInt(document.getElementById('seClassroom').value)||null;
if(!day||!st||!et){warnEl.style.display='none';return}
const sMin=toMin(st);const eMin=toMin(et);if(sMin>=eMin){warnEl.style.display='none';return}
const warnings=[];
const entriesToCheck=allSchedule.filter(e=>editingEntryId?e.id!==editingEntryId:true);
if(lecId){const lecConflicts=entriesToCheck.filter(e=>e.lecturer_id===lecId&&e.day===day&&toMin(e.start_time)<eMin&&sMin<toMin(e.end_time));
if(lecConflicts.length){lecConflicts.forEach(e=>{const code=e.offerings&&e.offerings.courses?e.offerings.courses.code:'?';const overlapStart=fmtTime(Math.max(sMin,toMin(e.start_time)));const overlapEnd=fmtTime(Math.min(eMin,toMin(e.end_time)));warnings.push('<div class="alert-box error" style="margin-bottom:4px"><strong>Hoca Cakismasi:</strong> '+escapeHtml(code)+' ('+e.start_time+'-'+e.end_time+') — cakisan aralik: '+overlapStart+'-'+overlapEnd+'</div>')})}}
if(crId){const crConflicts=entriesToCheck.filter(e=>e.classroom_id===crId&&e.day===day&&toMin(e.start_time)<eMin&&sMin<toMin(e.end_time));
if(crConflicts.length){crConflicts.forEach(e=>{const code=e.offerings&&e.offerings.courses?e.offerings.courses.code:'?';warnings.push('<div class="alert-box error" style="margin-bottom:4px"><strong>Derslik Cakismasi:</strong> '+escapeHtml(code)+' ('+e.start_time+'-'+e.end_time+')</div>')})}}
const offId=parseInt(document.getElementById('seOffering').value);const off=allOfferings.find(o=>o.id===offId);
if(off){const deptId=off.courses?off.courses.department_id:null;const studConflicts=entriesToCheck.filter(e=>e.offerings&&e.offerings.class_year===off.class_year&&e.offerings.section===off.section&&e.offerings.courses&&e.offerings.courses.department_id===deptId&&e.day===day&&toMin(e.start_time)<eMin&&sMin<toMin(e.end_time));
if(studConflicts.length){studConflicts.forEach(e=>{const code=e.offerings.courses?e.offerings.courses.code:'?';warnings.push('<div class="alert-box" style="background:#fff3e0;color:#e65100;margin-bottom:4px"><strong>Ogrenci Cakismasi:</strong> '+off.class_year+'. sinif '+escapeHtml(off.section)+' subesi — '+escapeHtml(code)+' ('+e.start_time+'-'+e.end_time+')</div>')})}}
if(lecId){const busySlots=(allAvailability||[]).filter(a=>a.lecturer_id===lecId&&a.day===day&&toMin(a.start_time)<eMin&&sMin<toMin(a.end_time));
if(busySlots.length){warnings.push('<div class="alert-box" style="background:#fff3e0;color:#e65100;margin-bottom:4px"><strong>Musaitlik Uyarisi:</strong> Hoca bu saatlerde mesgul olarak isaretlenmis</div>')}}
if(warnings.length){warnEl.style.display='block';warnEl.innerHTML=warnings.join('')}else{warnEl.style.display='none';warnEl.innerHTML=''}}

async function saveScheduleEntry(){const orgId=getCurrentOrgId();const body={orgId,offeringId:document.getElementById('seOffering').value,lecturerId:document.getElementById('seLecturer').value||null,classroomId:document.getElementById('seClassroom').value,day:document.getElementById('seDay').value,startTime:document.getElementById('seStart').value,endTime:document.getElementById('seEnd').value};
if(!body.offeringId||!body.classroomId||!body.day||!body.startTime||!body.endTime)return showAlert('seAlert','Tum alanlari doldurun.','error');
if(body.startTime>=body.endTime)return showAlert('seAlert','Bitis baslangictan sonra olmali.','error');
const warnEl=document.getElementById('seConflictWarning');if(warnEl&&warnEl.style.display==='block'&&!warnEl.dataset.forced){warnEl.dataset.forced='1';return showAlert('seAlert','Cakisma var! Yine de kaydetmek icin tekrar tikladiniz.','error')}
const url=editingEntryId?'/api/schedule/'+editingEntryId:'/api/schedule';const method=editingEntryId?'PUT':'POST';
const r=await apiFetch(url,{method,body:JSON.stringify(body)});const d=await r.json();if(d.error)return showAlert('seAlert',d.error,'error');
if(warnEl)warnEl.dataset.forced='';
bootstrap.Modal.getInstance(document.getElementById('scheduleModal')).hide();editingEntryId=null;loadSchedulePage()}

async function deleteScheduleEntry(id){const orgId=getCurrentOrgId();await apiFetch('/api/schedule/'+id+'?orgId='+orgId,{method:'DELETE'});loadSchedulePage()}

// ══════════════════════════════════════════════════════════════════
// AUTO-SCHEDULE GENERATOR (port from mobile ScheduleGenerator.kt)
// ══════════════════════════════════════════════════════════════════
function generateAutoSchedule(offerings, classrooms, busySlots, existingEntries, settings, prefs, seed){
const activeDays=settings.active_days&&settings.active_days.length?settings.active_days:DAYS;
const dayStartMin=toMin(settings.day_start||'08:00');const dayEndMin=toMin(settings.day_end||'18:00');
const step=Math.max(settings.time_step_minutes||30,5);
const prefStartMin=prefs.preferredStartTime?toMin(prefs.preferredStartTime):-1;
const prefEndMin=prefs.preferredEndTime?toMin(prefs.preferredEndTime):-1;
const rng=seed?{next:function(max){this.s=(this.s*1103515245+12345)&0x7fffffff;return this.s%max},s:seed}:null;

const assigned=[];const usedSlots=[];
existingEntries.forEach(e=>{usedSlots.push({day:e.day,startMin:toMin(e.start_time),endMin:toMin(e.end_time),lecturerId:e.lecturer_id,classroomId:e.classroom_id,classYear:e.offerings?e.offerings.class_year:0,section:e.offerings?e.offerings.section:'',departmentId:e.offerings&&e.offerings.courses?e.offerings.courses.department_id:null})});

function getSlotDuration(off){const th=off.courses?off.courses.theory_hours:0;const lh=off.courses?off.courses.lab_hours:0;return(th+lh>0)?(th+lh)*60:60}
function findSuitableClassrooms(off){const needLab=off.courses&&off.courses.lab_hours>0;return classrooms.filter(c=>c.capacity>=off.capacity&&(!needLab||c.type==='lab')).sort((a,b)=>a.capacity-b.capacity)}
function generateTimeSlots(dur){const slots=[];activeDays.forEach(day=>{let s=dayStartMin;while(s+dur<=dayEndMin){slots.push({day,startTime:fmtTime(s),endTime:fmtTime(s+dur)});s+=step}});return slots}
function isLecturerBusy(lecId,day,sMin,eMin){const busy=busySlots[lecId]||[];return busy.some(b=>b.day===day&&toMin(b.start_time)<eMin&&sMin<toMin(b.end_time))}
function isLecturerOccupied(lecId,day,sMin,eMin){return usedSlots.some(s=>s.lecturerId===lecId&&s.day===day&&s.startMin<eMin&&sMin<s.endMin)}
function isClassroomOccupied(crId,day,sMin,eMin){return usedSlots.some(s=>s.classroomId===crId&&s.day===day&&s.startMin<eMin&&sMin<s.endMin)}
function hasStudentConflict(off,day,sMin,eMin){const deptId=off.courses?off.courses.department_id:null;return usedSlots.some(s=>s.classYear===off.class_year&&s.section===off.section&&s.departmentId===deptId&&s.day===day&&s.startMin<eMin&&sMin<s.endMin)}
function nearestGap(slots,sMin,eMin){const lastEnd=Math.max(...slots.map(s=>s.endMin));const firstStart=Math.min(...slots.map(s=>s.startMin));const ga=sMin>=lastEnd?sMin-lastEnd:Infinity;const gb=eMin<=firstStart?firstStart-eMin:Infinity;return Math.min(ga,gb)}
function studentDaysForGroup(off){const deptId=off.courses?off.courses.department_id:null;return[...new Set(usedSlots.filter(s=>s.classYear===off.class_year&&s.section===off.section&&s.departmentId===deptId).map(s=>s.day))]}

function scorePlacement(day,sMin,eMin,off){let score=0;
const deptId=off.courses?off.courses.department_id:null;
const studentSlots=usedSlots.filter(s=>s.classYear===off.class_year&&s.section===off.section&&s.departmentId===deptId&&s.day===day);
if(prefs.compactness==='COMPACT'){if(studentSlots.length){const gap=nearestGap(studentSlots,sMin,eMin);if(gap===0)score-=30;else if(gap<=30)score-=25;else if(gap<=60)score-=15;else if(gap<=120)score+=10;else if(gap!==Infinity)score+=40}else{const sd=studentDaysForGroup(off);if(sd.length&&!sd.includes(day))score+=15}}
else if(prefs.compactness==='SPREAD'){if(studentSlots.length){const gap=nearestGap(studentSlots,sMin,eMin);if(gap===0)score+=20;else if(gap<=30)score+=15;else if(gap<=90)score-=10;else if(gap<=180)score-=15;else if(gap!==Infinity)score-=5;score+=studentSlots.length*5}else{const sd=studentDaysForGroup(off);if(sd.length&&!sd.includes(day))score-=20}}
const lecId=off.lecturer_id;if(lecId){const lecDaySlots=usedSlots.filter(s=>s.lecturerId===lecId&&s.day===day);if(lecDaySlots.length){if(prefs.maxDaily>0&&lecDaySlots.length>=prefs.maxDaily)score+=25;const lastEnd=Math.max(...lecDaySlots.map(s=>s.endMin));if(sMin>lastEnd){const gap=sMin-lastEnd;if(gap<=30)score-=5;if(gap>120)score+=10}}}
if(prefs.dayBalance){score+=usedSlots.filter(s=>s.day===day).length*3}
if(prefStartMin>0&&prefEndMin>0){if(sMin>=prefStartMin&&eMin<=prefEndMin)score-=5;else{const outside=Math.max(0,prefStartMin-sMin)+Math.max(0,eMin-prefEndMin);score+=Math.min(Math.floor(outside/30),10)}}
return score}

const sorted=[...offerings].sort((a,b)=>{const fa=a.lecturer_id?generateTimeSlots(getSlotDuration(a)).filter(s=>!isLecturerBusy(a.lecturer_id,s.day,toMin(s.startTime),toMin(s.endTime))).length:9999;const fb=b.lecturer_id?generateTimeSlots(getSlotDuration(b)).filter(s=>!isLecturerBusy(b.lecturer_id,s.day,toMin(s.startTime),toMin(s.endTime))).length:9999;return(fa+(rng?rng.next(5):0))-(fb+(rng?rng.next(5):0))});

const unassigned=[];const failures=[];
sorted.forEach(off=>{const dur=getSlotDuration(off);const candRooms=findSuitableClassrooms(off);
if(!candRooms.length){unassigned.push(off);failures.push({offering:off,reasons:['Uygun kapasitede derslik yok (gerekli: '+off.capacity+')']});return}
const slots=generateTimeSlots(dur);const scored=slots.map(s=>{const sc=scorePlacement(s.day,toMin(s.startTime),toMin(s.endTime),off)+(rng?rng.next(8):0);return{...s,score:sc}}).sort((a,b)=>a.score-b.score);

let placed=false;let lecBusy=0,lecOcc=0,studConf=0,roomFull=0;
for(const slot of scored){const sMin=toMin(slot.startTime);const eMin=toMin(slot.endTime);
if(off.lecturer_id){if(isLecturerBusy(off.lecturer_id,slot.day,sMin,eMin)){lecBusy++;continue}if(isLecturerOccupied(off.lecturer_id,slot.day,sMin,eMin)){lecOcc++;continue}}
if(hasStudentConflict(off,slot.day,sMin,eMin)){studConf++;continue}
const room=candRooms.find(c=>!isClassroomOccupied(c.id,slot.day,sMin,eMin));
if(!room){roomFull++;continue}
assigned.push({offering:off,lecturerId:off.lecturer_id,classroom:room,day:slot.day,startTime:slot.startTime,endTime:slot.endTime});
usedSlots.push({day:slot.day,startMin:sMin,endMin:eMin,lecturerId:off.lecturer_id,classroomId:room.id,classYear:off.class_year,section:off.section,departmentId:off.courses?off.courses.department_id:null});
placed=true;break}
if(!placed){unassigned.push(off);const reasons=[];const lecName=off.lecturers?(off.lecturers.title||'')+' '+off.lecturers.first_name+' '+off.lecturers.last_name:'?';
if(lecBusy>0)reasons.push(lecName+' musait degil ('+lecBusy+'/'+scored.length+' slot mesgul)');
if(lecOcc>0)reasons.push(lecName+' baska dersle dolu ('+lecOcc+'/'+scored.length+' cakisma)');
if(studConf>0)reasons.push(off.class_year+'. sinif '+off.section+' ogrenci cakismasi ('+studConf+' slot)');
if(roomFull>0)reasons.push('Derslikler dolu ('+roomFull+' slot — '+candRooms.length+' uygun derslik)');
if(!reasons.length)reasons.push('Tum '+scored.length+' slot tukenmi');
failures.push({offering:off,reasons})}});
return{assigned,unassigned,failures,score:assigned.length*100}}

function generateAlternatives(count,offerings,classrooms,busySlots,existing,settings,prefs){
const results=[];for(let i=0;i<count;i++){results.push(generateAutoSchedule(offerings,classrooms,busySlots,existing,settings,prefs,i*31+7))}
return results.sort((a,b)=>(b.assigned.length*1000+b.score)-(a.assigned.length*1000+a.score))}

async function runAutoSchedule(){const orgId=getCurrentOrgId();if(!orgId)return;
const prefs={compactness:document.querySelector('input[name="asCompact"]:checked').value,maxDaily:parseInt(document.getElementById('asMaxDaily').value)||0,dayBalance:document.getElementById('asDayBalance').checked,preferredStartTime:document.getElementById('asPrefStart').value||'',preferredEndTime:document.getElementById('asPrefEnd').value||''};
const altCount=parseInt(document.getElementById('asAltCount').value)||3;

const assignedOfferingIds=new Set(allSchedule.map(e=>e.offering_id));
const unassignedOfferings=allOfferings.filter(o=>!assignedOfferingIds.has(o.id));
if(!unassignedOfferings.length){showAlert('asAlert','Tum dersler zaten atanmis!','error');return}

const busyMap={};allAvailability.forEach(a=>{if(!busyMap[a.lecturer_id])busyMap[a.lecturer_id]=[];busyMap[a.lecturer_id].push(a)});

const results=generateAlternatives(altCount,unassignedOfferings,allClassrooms,busyMap,allSchedule,schedSettings,prefs);

const resEl=document.getElementById('asResults');resEl.style.display='block';
let html='<h5 class="mt-3 mb-2">Sonuclar ('+results.length+' alternatif)</h5>';
results.forEach((r,i)=>{const pct=unassignedOfferings.length?Math.round(r.assigned.length/unassignedOfferings.length*100):0;
html+='<div class="card mb-2"><div class="d-flex justify-content-between align-items-center"><div><strong>Alternatif '+(i+1)+'</strong> — <span class="badge-green">'+r.assigned.length+' atandi</span>'+(r.unassigned.length?' <span class="badge-red">'+r.unassigned.length+' atanamadi</span>':'')+' ('+pct+'%)</div><button class="btn btn-success btn-sm btn-apply-schedule" data-idx="'+i+'">Uygula</button></div>';
if(r.failures.length){html+='<details class="mt-2" style="font-size:12px"><summary style="cursor:pointer;color:#e65100">Atanamayan dersler ('+r.failures.length+')</summary><ul class="mt-1">';r.failures.forEach(f=>{const code=f.offering.courses?f.offering.courses.code:'?';html+='<li><strong>'+escapeHtml(code)+'</strong> ('+f.offering.class_year+'/'+f.offering.section+'): '+f.reasons.map(escapeHtml).join('; ')+'</li>'});html+='</ul></details>'}
if(r.assigned.length){html+='<details class="mt-2" style="font-size:12px"><summary style="cursor:pointer;color:#1565c0">Atanan dersler ('+r.assigned.length+')</summary><table class="mt-1"><tr><th>Ders</th><th>Hoca</th><th>Derslik</th><th>Gun</th><th>Saat</th></tr>';r.assigned.forEach(a=>{const code=a.offering.courses?a.offering.courses.code:'?';const lec=a.offering.lecturers?(a.offering.lecturers.title||'')+' '+a.offering.lecturers.first_name+' '+a.offering.lecturers.last_name:'-';html+='<tr><td>'+escapeHtml(code)+'</td><td>'+escapeHtml(lec)+'</td><td>'+escapeHtml(a.classroom.room_code)+'</td><td>'+(DAY_TR[a.day]||a.day)+'</td><td>'+a.startTime+'-'+a.endTime+'</td></tr>'});html+='</table></details>'}
html+='</div>'});
resEl.innerHTML=html;
window._autoResults=results;
resEl.querySelectorAll('.btn-apply-schedule').forEach(btn=>{btn.addEventListener('click',async()=>{const idx=parseInt(btn.dataset.idx);const result=window._autoResults[idx];if(!result||!result.assigned.length)return;
btn.disabled=true;btn.textContent='Kaydediliyor...';
const entries=result.assigned.map(a=>({orgId,offeringId:a.offering.id,lecturerId:a.lecturerId,classroomId:a.classroom.id,day:a.day,startTime:a.startTime,endTime:a.endTime}));
try{const r=await apiFetch('/api/schedule/bulk',{method:'POST',body:JSON.stringify({entries})});const d=await r.json();if(d.error){showAlert('asAlert',d.error,'error');btn.disabled=false;btn.textContent='Uygula';return}
bootstrap.Modal.getInstance(document.getElementById('autoScheduleModal')).hide();loadSchedulePage()}catch(e){showAlert('asAlert',e.message,'error');btn.disabled=false;btn.textContent='Uygula'}})})}

// ══════════════════════════════════════════════════════════════════
// AVAILABILITY GRID
// ══════════════════════════════════════════════════════════════════
async function loadAvailabilityPage(){const orgId=getCurrentOrgId();if(!orgId)return;
let rLecs,rSettings;try{[rLecs,rSettings]=await Promise.all([apiFetch('/api/lecturers/'+orgId),apiFetch('/api/settings/'+orgId)])}catch(e){if(e.message!=='Auth')showAlert('availAlert','Musaitlik yuklenemedi: '+e.message,'error');return;}
allLecturers=await rLecs.json();schedSettings=await rSettings.json();
document.getElementById('availLecturer').innerHTML='<option value="">- Secin -</option>'+allLecturers.map(l=>'<option value="'+l.id+'">'+escapeHtml((l.title||'')+' '+l.first_name+' '+l.last_name)+'</option>').join('');
document.getElementById('availabilityContent').style.display='none'}

async function loadLecturerAvailability(){const lecId=document.getElementById('availLecturer').value;const orgId=getCurrentOrgId();if(!lecId){document.getElementById('availabilityContent').style.display='none';return}
document.getElementById('availabilityContent').style.display='block';
const[rAvail,rSched]=await Promise.all([apiFetch('/api/availability/'+orgId+'?lecturerId='+lecId),apiFetch('/api/schedule/'+orgId)]);
currentAvailability=await rAvail.json();const sched=await rSched.json();const lecSched=sched.filter(e=>e.lecturer_id==lecId);
renderAvailGrid(currentAvailability,lecSched)}

function renderAvailGrid(busy,sched){const grid=document.getElementById('availGrid');const dayStart=toMin(schedSettings.day_start||'08:00');const dayEnd=toMin(schedSettings.day_end||'18:00');const PX_PER_MIN=0.7;const hours=[];
for(let m=dayStart;m<dayEnd;m+=60)hours.push(m);
let html='<div class="ag-header"></div>';DAYS.forEach(d=>{html+='<div class="ag-header">'+DAY_TR[d]+'</div>'});
hours.forEach(h=>{html+='<div class="ag-time-label" style="height:'+60*PX_PER_MIN+'px">'+fmtTime(h)+'</div>';DAYS.forEach(()=>{html+='<div class="ag-day-col" style="height:'+60*PX_PER_MIN+'px"><div class="hour-line" style="top:0"></div></div>'})});
grid.innerHTML=html;grid.style.gridTemplateRows='auto repeat('+hours.length+','+60*PX_PER_MIN+'px)';

const dayCols={};DAYS.forEach((d,i)=>{const colEls=grid.querySelectorAll('.ag-day-col');const dayEls=[];for(let r=0;r<hours.length;r++)dayEls.push(colEls[r*5+i]);dayCols[d]=dayEls});

busy.forEach(b=>{const cols=dayCols[b.day];if(!cols||!cols.length)return;const sMin=toMin(b.start_time);const eMin=toMin(b.end_time);const top=(sMin-dayStart)*PX_PER_MIN;const h=(eMin-sMin)*PX_PER_MIN;
const block=document.createElement('div');block.className='avail-busy-block';block.style.cssText='top:'+top+'px;height:'+Math.max(h,16)+'px';block.innerHTML='<span>'+b.start_time+'-'+b.end_time+'</span><span class="del-x" data-id="'+b.id+'">x</span>';
cols[0].style.position='relative';cols[0].appendChild(block)});

sched.forEach(e=>{const cols=dayCols[e.day];if(!cols||!cols.length)return;const sMin=toMin(e.start_time);const eMin=toMin(e.end_time);const top=(sMin-dayStart)*PX_PER_MIN;const h=(eMin-sMin)*PX_PER_MIN;
const code=e.offerings&&e.offerings.courses?e.offerings.courses.code:'?';
const block=document.createElement('div');block.className='avail-sched-block';block.style.cssText='top:'+top+'px;height:'+Math.max(h,16)+'px';block.innerHTML=escapeHtml(code)+' '+e.start_time+'-'+e.end_time;
cols[0].style.position='relative';cols[0].appendChild(block)})}

async function addAvailability(){const lecId=document.getElementById('availLecturer').value;const orgId=getCurrentOrgId();const day=document.getElementById('availDay').value;const st=document.getElementById('availStart').value;const et=document.getElementById('availEnd').value;
if(!lecId||!day||!st||!et)return showAlert('availAlert','Tum alanlari doldurun.','error');
if(st>=et)return showAlert('availAlert','Bitis baslangictan sonra olmali.','error');
const r=await apiFetch('/api/availability',{method:'POST',body:JSON.stringify({lecturerId:lecId,day,startTime:st,endTime:et,orgId})});const d=await r.json();if(d.error)return showAlert('availAlert',d.error,'error');
showAlert('availAlert','Mesgul blok eklendi.','success');loadLecturerAvailability()}
async function deleteAvailability(id){await apiFetch('/api/availability/'+id,{method:'DELETE'});loadLecturerAvailability()}

// ── Error Logs
function renderLogTable(data){
  if(!data||!data.length){document.getElementById('logList').innerHTML='<div class="empty-state">Log yok.</div>';return}
  let h='<table><tr><th>Tarih</th><th>Kaynak</th><th>Kullanici</th><th>Rol</th><th>Cihaz</th><th>Versiyon</th><th>Ekran</th><th>Aksiyon</th><th>Mesaj</th><th>Detay</th></tr>';
  data.forEach((l,i)=>{const isPanel=l.screen&&l.screen.startsWith('PANEL');const sourceBadge=isPanel?'<span class="badge-purple">WEB</span>':'<span class="badge-blue">MOBiL</span>';const roleBadge=l.role==='super_admin'?'badge-orange':l.role==='admin'?'badge-blue':l.role==='lecturer'?'badge-purple':'badge-orange';h+='<tr><td style="white-space:nowrap">'+new Date(l.created_at).toLocaleString('tr-TR')+'</td><td>'+sourceBadge+'</td><td>'+(l.username?'<span class="'+roleBadge+'">'+escapeHtml(l.username)+'</span>':'-')+'</td><td>'+(l.role||'-')+'</td><td>'+(l.device_model?escapeHtml(l.device_model):'-')+'</td><td>'+(l.app_version||'-')+'</td><td>'+escapeHtml(l.screen||'-')+'</td><td>'+escapeHtml(l.action||'-')+'</td><td style="max-width:260px;word-break:break-word">'+escapeHtml(l.message||'')+'</td><td>'+(l.stack_trace?'<button class="btn btn-outline-secondary btn-sm btn-show-stack" data-idx="'+i+'">Stack</button>':'')+'</td></tr>'});
  document.getElementById('logList').innerHTML=h+'</table>';
}
async function loadErrorLogs(){const orgId=document.getElementById('logOrg').value||'';const url=orgId?'/api/error-logs?orgId='+orgId:'/api/error-logs';const r=await apiFetch(url);const data=await r.json();
_cachedLogData=Array.isArray(data)?data:[];
const statsEl=document.getElementById('logStats');
if(!_cachedLogData.length){document.getElementById('logList').innerHTML='<div class="empty-state">Log yok.</div>';if(statsEl)statsEl.innerHTML='';return}
const todayLogs=_cachedLogData.filter(l=>{const d=new Date(l.created_at);const today=new Date();return d.toDateString()===today.toDateString()});
const panelLogs=_cachedLogData.filter(l=>l.screen&&l.screen.startsWith('PANEL'));const mobileLogs=_cachedLogData.filter(l=>!l.screen||!l.screen.startsWith('PANEL'));
if(statsEl)statsEl.innerHTML='<div class="stat-card"><div class="number">'+_cachedLogData.length+'</div><div class="label">Toplam Log</div></div><div class="stat-card"><div class="number">'+todayLogs.length+'</div><div class="label">Bugun</div></div><div class="stat-card"><div class="number">'+mobileLogs.length+'</div><div class="label">Mobil</div></div><div class="stat-card" style="border-top-color:#6a1b9a"><div class="number" style="color:#6a1b9a">'+panelLogs.length+'</div><div class="label">Web Panel</div></div>';
renderLogTable(_cachedLogData);
document.querySelectorAll('.btn-show-stack').forEach(btn=>{btn.addEventListener('click',()=>{const idx=parseInt(btn.dataset.idx);const log=data[idx];if(!log||!log.stack_trace)return;const modal=document.createElement('div');modal.style.cssText='position:fixed;inset:0;background:rgba(0,0,0,.5);z-index:9999;display:flex;align-items:center;justify-content:center;padding:20px';modal.innerHTML='<div style="background:#fff;border-radius:12px;padding:20px;max-width:800px;width:100%;max-height:80vh;overflow-y:auto"><div style="display:flex;justify-content:space-between;margin-bottom:12px"><strong>Stack Trace — '+escapeHtml(log.screen||'')+' / '+escapeHtml(log.action||'')+'</strong><span style="cursor:pointer;font-size:20px" class="close-modal">&times;</span></div><pre style="font-size:11px">'+escapeHtml(log.stack_trace)+'</pre></div>';document.body.appendChild(modal);modal.querySelector('.close-modal').addEventListener('click',()=>modal.remove());modal.addEventListener('click',e=>{if(e.target===modal)modal.remove()})})})}

// ── Edit Modal
function openEditModal(type,data){currentEditType=type;currentEditId=data.id;const body=document.getElementById('editModalBody');let html='';
if(type==='lecturer'){html='<label class="form-label">Unvan</label><input type="text" id="editLecTitle" class="form-control mb-2" value="'+(data.title||'')+'"><label class="form-label">Ad</label><input type="text" id="editLecFirst" class="form-control mb-2" value="'+escapeHtml(data.first_name)+'"><label class="form-label">Soyad</label><input type="text" id="editLecLast" class="form-control mb-2" value="'+escapeHtml(data.last_name)+'"><label class="form-label">E-posta</label><input type="text" id="editLecEmail" class="form-control mb-2" value="'+escapeHtml(data.email||'')+'">'}
else if(type==='course'){html='<label class="form-label">Kod</label><input type="text" id="editCourseCode" class="form-control mb-2" value="'+escapeHtml(data.code)+'"><label class="form-label">Ad</label><input type="text" id="editCourseName" class="form-control mb-2" value="'+escapeHtml(data.name)+'"><label class="form-label">Teori</label><input type="number" id="editCourseTheory" class="form-control mb-2" value="'+data.theory_hours+'"><label class="form-label">Lab</label><input type="number" id="editCourseLab" class="form-control mb-2" value="'+data.lab_hours+'"><label class="form-label">Kredi</label><input type="number" id="editCourseCredits" class="form-control mb-2" value="'+data.credits+'">'}
else if(type==='classroom'){html='<label class="form-label">Oda Kodu</label><input type="text" id="editRoomCode" class="form-control mb-2" value="'+escapeHtml(data.room_code)+'"><label class="form-label">Kapasite</label><input type="number" id="editRoomCap" class="form-control mb-2" value="'+data.capacity+'">'}
body.innerHTML=html;new bootstrap.Modal(document.getElementById('editModal')).show()}

document.getElementById('btnSaveEdit').addEventListener('click',async()=>{let endpoint='',payload={};
if(currentEditType==='lecturer'){endpoint='/api/lecturers/'+currentEditId;payload={title:document.getElementById('editLecTitle').value,firstName:document.getElementById('editLecFirst').value,lastName:document.getElementById('editLecLast').value,email:document.getElementById('editLecEmail').value}}
else if(currentEditType==='course'){endpoint='/api/courses/'+currentEditId;payload={code:document.getElementById('editCourseCode').value,name:document.getElementById('editCourseName').value,theoryHours:document.getElementById('editCourseTheory').value,labHours:document.getElementById('editCourseLab').value,credits:document.getElementById('editCourseCredits').value}}
else if(currentEditType==='classroom'){endpoint='/api/classrooms/'+currentEditId;payload={roomCode:document.getElementById('editRoomCode').value,capacity:document.getElementById('editRoomCap').value}}
try{await apiFetch(endpoint,{method:'PUT',body:JSON.stringify(payload)});bootstrap.Modal.getInstance(document.getElementById('editModal')).hide();loadDashboard()}catch(e){alert('Hata: '+e.message)}});

// ── Current time line refresh
setInterval(()=>{const grid=document.getElementById('weeklyGrid');if(!grid||!grid.children.length)return;const dayStart=toMin(schedSettings.day_start||'08:00');const dayEnd=toMin(schedSettings.day_end||'18:00');drawCurrentTimeLine(grid,dayStart,dayEnd,1)},60000);

// ── Init
// ── Global error handlers — panel hatalarını sessizce logla
window.addEventListener('error',e=>{sendPanelLog('window','error',e.message||(e.error&&e.error.message)||'Unknown error',e.error&&e.error.stack)});
window.addEventListener('unhandledrejection',e=>{const r=e.reason;const msg=r instanceof Error?r.message:String(r);const stk=r instanceof Error?r.stack:null;sendPanelLog('window','unhandledrejection',msg,stk)});

document.addEventListener('DOMContentLoaded',()=>{
document.getElementById('loginForm').addEventListener('submit',e=>{e.preventDefault();doLogin()});
document.getElementById('btnLogout').addEventListener('click',doLogout);
document.querySelectorAll('.sidebar a[data-page]').forEach(a=>a.addEventListener('click',e=>{e.preventDefault();showPage(a.getAttribute('data-page'))}));
// Global org selector — tüm sayfalar buradan dinlenir
document.getElementById('globalOrg').addEventListener('change',onOrgSelectChanged);
document.getElementById('logOrg').addEventListener('change',loadErrorLogs);
document.getElementById('availLecturer').addEventListener('change',loadLecturerAvailability);
['filterLecturer','filterDept','filterClassroom','filterYear'].forEach(id=>document.getElementById(id).addEventListener('change',renderWeeklyGrid));
// Live normalisation for org code: as the user types, fold Turkish chars,
// uppercase, replace spaces with `_`, drop anything outside [A-Z0-9_-].
// This mirrors the server-side normalizeOrgCode so the input box always
// shows what will actually hit the database — fewer "why is my code
// invalid" surprises.
(function wireOrgCodeNormalisation(){
    const TURKISH_FOLD = {ş:'s',Ş:'S',ç:'c',Ç:'C',ğ:'g',Ğ:'G',ü:'u',Ü:'U',ö:'o',Ö:'O',ı:'i',İ:'I'};
    const orgCode = document.getElementById('orgCode');
    if (!orgCode) return;
    orgCode.addEventListener('input', () => {
        const start = orgCode.selectionStart;
        const out = orgCode.value
            .split('').map(c => TURKISH_FOLD[c] || c).join('')
            .replace(/\s+/g, '_')
            .toUpperCase()
            .replace(/[^A-Z0-9_-]/g, '')
            .substring(0, 20);
        if (out !== orgCode.value) {
            orgCode.value = out;
            // restore caret position roughly so the user can keep typing
            orgCode.setSelectionRange(start, start);
        }
    });
})();

document.getElementById('btnAddOrg').addEventListener('click',addOrganization);
document.getElementById('btnAddAdmin').addEventListener('click',addAdmin);
document.getElementById('btnAddDept').addEventListener('click',addDepartment);
document.getElementById('btnAddCourse').addEventListener('click',addCourse);
document.getElementById('btnAddAvail').addEventListener('click',addAvailability);
document.getElementById('btnAddLec').addEventListener('click',addLecturer);
document.getElementById('btnAddClassroom').addEventListener('click',addClassroom);
document.getElementById('btnSaveSettings').addEventListener('click',saveSettings);
document.getElementById('btnAddOffering').addEventListener('click',addOffering);
document.getElementById('btnExportLecturers').addEventListener('click',()=>exportExcel('lecturers'));
document.getElementById('btnExportCourses').addEventListener('click',()=>exportExcel('courses'));
document.getElementById('btnExportClassrooms').addEventListener('click',()=>exportExcel('classrooms'));
document.getElementById('btnBulkReset').addEventListener('click',bulkResetPasswords);
document.getElementById('importLecturers').addEventListener('change',e=>{if(e.target.files[0])importExcel('lecturers',e.target.files[0],'importLecAlert');e.target.value=''});
document.getElementById('importCourses').addEventListener('change',e=>{if(e.target.files[0])importExcel('courses',e.target.files[0],'importCourseAlert');e.target.value=''});
document.getElementById('importClassrooms').addEventListener('change',e=>{if(e.target.files[0])importExcel('classrooms',e.target.files[0],'importClassroomAlert');e.target.value=''});
document.getElementById('btnAddEntry').addEventListener('click',()=>{editingEntryId=null;document.getElementById('schedModalTitle').textContent='Ders Ekle';document.getElementById('seAlert').innerHTML='';document.getElementById('seConflictWarning').style.display='none';new bootstrap.Modal(document.getElementById('scheduleModal')).show()});
document.getElementById('btnSaveEntry').addEventListener('click',saveScheduleEntry);
document.getElementById('seOffering').addEventListener('change',onOfferingChange);
document.getElementById('btnAutoSchedule').addEventListener('click',()=>{document.getElementById('asAlert').innerHTML='';document.getElementById('asResults').style.display='none';new bootstrap.Modal(document.getElementById('autoScheduleModal')).show()});
document.getElementById('btnRunAutoSchedule').addEventListener('click',runAutoSchedule);
document.getElementById('asAltCount').addEventListener('input',()=>{document.getElementById('asAltLabel').textContent=document.getElementById('asAltCount').value});
document.getElementById('btnEditEntry').addEventListener('click',()=>{if(!currentDetailEntry)return;bootstrap.Modal.getInstance(document.getElementById('entryDetailModal')).hide();editingEntryId=currentDetailEntry.id;document.getElementById('schedModalTitle').textContent='Ders Duzenle';document.getElementById('seOffering').value=currentDetailEntry.offering_id;onOfferingChange();if(currentDetailEntry.lecturer_id)document.getElementById('seLecturer').value=currentDetailEntry.lecturer_id;if(currentDetailEntry.classroom_id)document.getElementById('seClassroom').value=currentDetailEntry.classroom_id;document.getElementById('seDay').value=currentDetailEntry.day;document.getElementById('seStart').value=currentDetailEntry.start_time;document.getElementById('seEnd').value=currentDetailEntry.end_time;new bootstrap.Modal(document.getElementById('scheduleModal')).show()});
document.getElementById('btnDeleteEntry').addEventListener('click',()=>{if(!currentDetailEntry)return;if(!confirm('Bu kaydi silmek istediginize emin misiniz?'))return;bootstrap.Modal.getInstance(document.getElementById('entryDetailModal')).hide();deleteScheduleEntry(currentDetailEntry.id)});

document.addEventListener('click',e=>{let btn;
if((btn=e.target.closest('.btn-del-org')))deleteOrg(btn.dataset.id);
if((btn=e.target.closest('.btn-del-admin')))deleteAdmin(btn.dataset.id);
if((btn=e.target.closest('.btn-reset-pw')))resetPassword(btn.dataset.id);
if((btn=e.target.closest('.btn-del-dept')))deleteDept(btn.dataset.id);
if((btn=e.target.closest('.btn-del-lec')))deleteLecturer(btn.dataset.id);
if((btn=e.target.closest('.btn-reset-lec-pw')))resetLecturerPassword(btn.dataset.id);
if((btn=e.target.closest('.btn-del-course')))deleteCourse(btn.dataset.id);
if((btn=e.target.closest('.btn-del-classroom')))deleteClassroom(btn.dataset.id);
if((btn=e.target.closest('.btn-del-offering')))deleteOffering(btn.dataset.id);
if((btn=e.target.closest('.btn-edit-lec')))openEditModal('lecturer',JSON.parse(decodeURIComponent(btn.dataset.data)));
if((btn=e.target.closest('.btn-edit-course')))openEditModal('course',JSON.parse(decodeURIComponent(btn.dataset.data)));
if((btn=e.target.closest('.btn-edit-classroom')))openEditModal('classroom',JSON.parse(decodeURIComponent(btn.dataset.data)));
if((btn=e.target.closest('.del-x')))deleteAvailability(btn.dataset.id)});

// ─── Security / Threat Intelligence page (P3) ─────────────────────────
//
// Pure monitoring — no blocking. Risk thresholds are panel-side and
// configurable via the inputs at the top of the page so an analyst can
// hunt with different sensitivities without restarting the server.
const SEC_DEFAULTS = { failMid:5, failHigh:10, churnMid:3, churnHigh:5, windowMin:15 };

function readThresholds() {
    return {
        failMid:   parseInt(document.getElementById('thFailMid').value,   10) || SEC_DEFAULTS.failMid,
        failHigh:  parseInt(document.getElementById('thFailHigh').value,  10) || SEC_DEFAULTS.failHigh,
        churnMid:  parseInt(document.getElementById('thChurnMid').value,  10) || SEC_DEFAULTS.churnMid,
        churnHigh: parseInt(document.getElementById('thChurnHigh').value, 10) || SEC_DEFAULTS.churnHigh,
        windowMin: parseInt(document.getElementById('thWindowMin').value, 10) || SEC_DEFAULTS.windowMin,
    };
}

function resetThresholds() {
    document.getElementById('thFailMid').value   = SEC_DEFAULTS.failMid;
    document.getElementById('thFailHigh').value  = SEC_DEFAULTS.failHigh;
    document.getElementById('thChurnMid').value  = SEC_DEFAULTS.churnMid;
    document.getElementById('thChurnHigh').value = SEC_DEFAULTS.churnHigh;
    document.getElementById('thWindowMin').value = SEC_DEFAULTS.windowMin;
    loadSecurityPage();
}

async function loadSecurityPage() {
    const win = document.getElementById('secWindow').value || '7d';
    const user = document.getElementById('secUser').value.trim();
    const onlyFailed = document.getElementById('secOnlyFailed').checked;
    const th = readThresholds();

    // Summary card
    try {
        const sumParams = new URLSearchParams({
            since: win,
            susFails: String(th.failHigh),
            susUsers: String(th.churnHigh)
        });
        const sumRes = await apiFetch('/api/login-attempts/summary?' + sumParams.toString());
        const sum = await sumRes.json();
        const failRate = sum.total > 0 ? Math.round((sum.failures / sum.total) * 100) : 0;
        document.getElementById('secStats').innerHTML = `
            <div class="stat-card"><div class="stat-label">Toplam Deneme</div><div class="stat-value">${sum.total||0}</div></div>
            <div class="stat-card"><div class="stat-label">Basarili</div><div class="stat-value" style="color:#28a745">${sum.success||0}</div></div>
            <div class="stat-card"><div class="stat-label">Basarisiz</div><div class="stat-value" style="color:#dc3545">${sum.failures||0}</div></div>
            <div class="stat-card"><div class="stat-label">Basarisizlik Orani</div><div class="stat-value">${failRate}%</div></div>
        `;
        document.getElementById('secTopUsers').innerHTML = sum.topUsernames.length === 0
            ? '<div class="text-muted">Veri yok</div>'
            : '<table class="table table-sm"><thead><tr><th>Kullanici</th><th>Toplam</th><th>Basarisiz</th></tr></thead><tbody>'
            + sum.topUsernames.map(u => `<tr><td>${escapeHtml(u.username)}</td><td>${u.total}</td><td style="color:#dc3545">${u.failed}</td></tr>`).join('')
            + '</tbody></table>';
        document.getElementById('secSusDevices').innerHTML = sum.suspiciousDevices.length === 0
            ? '<div class="text-muted">Supheli cihaz yok</div>'
            : '<table class="table table-sm"><thead><tr><th>Cihaz</th><th>Kullanici</th><th>Basarisiz</th></tr></thead><tbody>'
            + sum.suspiciousDevices.map(d => `<tr><td><code style="font-size:0.75rem">${escapeHtml((d.device_id||'').substring(0,12))}…</code></td><td>${d.usernameCount}</td><td style="color:#dc3545">${d.failed}</td></tr>`).join('')
            + '</tbody></table>';
        document.getElementById('secTopIps').innerHTML = sum.topIps.length === 0
            ? '<div class="text-muted">IP bilgisi yok (mobil tarafindan toplanmıyor)</div>'
            : '<table class="table table-sm"><thead><tr><th>IP</th><th>Toplam</th><th>Basarisiz</th></tr></thead><tbody>'
            + sum.topIps.map(ip => `<tr><td><code>${escapeHtml(ip.ip)}</code></td><td>${ip.total}</td><td style="color:#dc3545">${ip.failed}</td></tr>`).join('')
            + '</tbody></table>';
    } catch (e) {
        document.getElementById('secStats').innerHTML = `<div class="alert alert-danger">Ozet yuklenemedi: ${escapeHtml(e.message)}</div>`;
    }

    // Detail attempts list
    try {
        const params = new URLSearchParams();
        params.set('since', win);
        params.set('pageSize', '100');
        params.set('failMid',   String(th.failMid));
        params.set('failHigh',  String(th.failHigh));
        params.set('churnMid',  String(th.churnMid));
        params.set('churnHigh', String(th.churnHigh));
        params.set('windowMin', String(th.windowMin));
        if (user) params.set('username', user);
        if (onlyFailed) params.set('onlyFailed', '1');
        const r = await apiFetch('/api/login-attempts?' + params.toString());
        const data = await r.json();
        const rows = data.rows || [];
        if (rows.length === 0) {
            document.getElementById('secAttempts').innerHTML = '<div class="text-muted">Bu pencerede deneme yok</div>';
            return;
        }
        const tbl = '<div class="table-responsive"><table class="table table-sm table-hover">'
            + '<thead><tr>'
            + '<th>Zaman</th><th>Kullanici</th><th>Sonuc</th><th>Asama</th>'
            + '<th>Cihaz Modeli</th><th>OS</th><th>App</th>'
            + '<th>Cihaz Hash</th><th>IP</th><th>Risk</th>'
            + '</tr></thead><tbody>'
            + rows.map(r => {
                const t = new Date(r.created_at);
                const okBadge = r.succeeded
                    ? '<span class="badge bg-success">OK</span>'
                    : '<span class="badge bg-danger">FAIL</span>';
                const riskColor = r.risk >= 60 ? 'bg-danger' : r.risk >= 30 ? 'bg-warning' : 'bg-secondary';
                return `<tr>
                    <td>${t.toLocaleString('tr-TR')}</td>
                    <td><strong>${escapeHtml(r.username)}</strong></td>
                    <td>${okBadge}</td>
                    <td>${escapeHtml(r.failure_step || '-')}</td>
                    <td>${escapeHtml(r.device_model || '-')}</td>
                    <td>${escapeHtml(r.os_version || '-')}</td>
                    <td>${escapeHtml(r.app_version || '-')}</td>
                    <td><code style="font-size:0.7rem">${escapeHtml((r.device_id||'-').substring(0,10))}</code></td>
                    <td><code style="font-size:0.75rem">${escapeHtml(r.ip_address || '-')}</code></td>
                    <td><span class="badge ${riskColor}">${r.risk}</span></td>
                </tr>`;
            }).join('')
            + '</tbody></table></div>';
        document.getElementById('secAttempts').innerHTML = tbl;
    } catch (e) {
        document.getElementById('secAttempts').innerHTML = `<div class="alert alert-danger">Detay yuklenemedi: ${escapeHtml(e.message)}</div>`;
    }
}

function escapeHtml(s) {
    if (s == null) return '';
    return String(s).replace(/[&<>"']/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c]));
}

document.getElementById('btnSecRefresh').addEventListener('click', loadSecurityPage);
document.getElementById('btnSecResetThresholds').addEventListener('click', resetThresholds);
['secWindow','secUser','secOnlyFailed'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('change', loadSecurityPage);
});

checkAuth()});
