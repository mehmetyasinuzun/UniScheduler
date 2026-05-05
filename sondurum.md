# UniScheduler — Son Durum Raporu
> Projeyi ilk kez okuyan biri için eksiksiz referans belgesi.  
> Tarih: Mayıs 2026 | Ders: Mobile Programming (Android)

---

## 1. Proje Özeti

**UniScheduler**, bir üniversite bölümünün ders programını yöneten Android uygulamasıdır.

- **Admin** rolü: Öğretim üyelerini, dersleri, dersliklerini ve bölümleri sisteme ekler; haftalık ders programını oluşturur; çakışmaları (double-booking) önler.
- **Lecturer** rolü: Kendi haftalık ders programını görüntüler.

**Backend:** Supabase (PostgreSQL + REST API + Realtime WebSocket)  
**Mimari:** MVVM + Repository + StateFlow + Kotlin Coroutines  

---

## 2. Teknik Stack

| Bileşen | Versiyon |
|---------|----------|
| Kotlin | 2.0.21 |
| AGP (Android Gradle Plugin) | 8.7.3 |
| Gradle Wrapper | 8.9 |
| Supabase Kotlin SDK | 3.0.0 (BOM) |
| Ktor (Supabase transport) | 3.0.1 (ktor-client-cio) |
| Kotlinx Coroutines | 1.9.0 |
| Kotlinx Serialization | 1.7.3 |
| Navigation Component | 2.7.x |
| Material Components | 1.x |
| Security Crypto (EncryptedSharedPreferences) | 1.1.0-alpha06 |
| AndroidX GridLayout | 1.0.0 |
| Min SDK | 26 | Target SDK | 35 |

---

## 3. Supabase Proje Bilgileri

```
Proje URL : https://lcnganxesvgbfiorifig.supabase.co
Anon Key  : local.properties dosyasında SUPABASE_ANON_KEY olarak saklanır
            (git'e commit edilmez — .gitignore'da)
```

`local.properties` (projenin kökünde, git'e girmez):
```
SUPABASE_URL=https://lcnganxesvgbfiorifig.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOi...  (Supabase Dashboard > Settings > API)
```

---

## 4. Veritabanı Şeması (6 Tablo)

### Tablo Yapısı

```sql
-- 1. Kimlik doğrulama
CREATE TABLE users (
    id                  UUID    DEFAULT gen_random_uuid() PRIMARY KEY,
    username            TEXT    UNIQUE NOT NULL,
    password_hash       TEXT    NOT NULL,           -- SHA-256 hex
    role                TEXT    NOT NULL CHECK (role IN ('admin','lecturer')),
    must_change_password BOOLEAN DEFAULT false,
    created_at          TIMESTAMPTZ DEFAULT now()
);

-- 2. Bölümler
CREATE TABLE departments (
    id   SERIAL PRIMARY KEY,
    name TEXT   NOT NULL
);

-- 3. Öğretim üyeleri
CREATE TABLE lecturers (
    id            SERIAL  PRIMARY KEY,
    user_id       UUID    REFERENCES users(id),
    title         TEXT,                              -- "Dr.", "Prof." vb.
    first_name    TEXT    NOT NULL,
    last_name     TEXT    NOT NULL,
    department_id INTEGER REFERENCES departments(id)
);

-- 4. Dersler
CREATE TABLE courses (
    id            SERIAL PRIMARY KEY,
    code          TEXT   UNIQUE NOT NULL,            -- "CS101"
    name          TEXT   NOT NULL,
    department_id INTEGER REFERENCES departments(id)
);

-- 5. Derslikler
CREATE TABLE classrooms (
    id            SERIAL  PRIMARY KEY,
    room_code     TEXT    UNIQUE NOT NULL,           -- "A101"
    capacity      INTEGER NOT NULL,
    department_id INTEGER REFERENCES departments(id)
);

-- 6. Program girişleri
CREATE TABLE schedule_entries (
    id           SERIAL  PRIMARY KEY,
    course_id    INTEGER REFERENCES courses(id),
    lecturer_id  INTEGER REFERENCES lecturers(id),
    classroom_id INTEGER REFERENCES classrooms(id),
    day          TEXT    NOT NULL,                   -- "Monday" … "Friday"
    time_slot    TEXT    NOT NULL,                   -- "08:00-10:00" vb.
    UNIQUE(lecturer_id, day, time_slot),             -- hoca çakışma önleme
    UNIQUE(classroom_id, day, time_slot)             -- derslik çakışma önleme
);
```

### RLS (Row Level Security) — ⚠️ Yapılması Zorunlu

Supabase'de RLS varsayılan olarak **AÇIK** gelir. Uygulama anon key ile bağlandığı için tüm tablolarda kapatılmalı:

```sql
-- Supabase Dashboard > SQL Editor'de çalıştır:
ALTER TABLE users            DISABLE ROW LEVEL SECURITY;
ALTER TABLE departments      DISABLE ROW LEVEL SECURITY;
ALTER TABLE lecturers        DISABLE ROW LEVEL SECURITY;
ALTER TABLE courses          DISABLE ROW LEVEL SECURITY;
ALTER TABLE classrooms       DISABLE ROW LEVEL SECURITY;
ALTER TABLE schedule_entries DISABLE ROW LEVEL SECURITY;
```

> **Not:** RLS kapalıyken anon key sahibi herkese tablolar açık olur. Bu öğrenci projesi için kabul edilebilir; production'da her tablo için uygun politikalar yazılmalı.

### Seed: Admin Kullanıcısı

SHA-256 hesaplamak için https://emn178.github.io/online-tools/sha256.html kullan.
`Admin123` ifadesinin SHA-256 hash'ini kopyala, aşağıya yapıştır:

```sql
INSERT INTO users (username, password_hash, role, must_change_password)
VALUES (
    'admin',
    '<Admin123 ifadesinin SHA-256 hex çıktısı>',
    'admin',
    false
);
```

### Seed: Örnek Veri (İsteğe Bağlı)

```sql
-- Bölümler
INSERT INTO departments (name) VALUES ('Computer Science'), ('Mathematics'), ('Physics');

-- Örnek derslikler
INSERT INTO classrooms (room_code, capacity, department_id)
VALUES ('A101', 40, 1), ('B202', 30, 2), ('C303', 50, null);
```

---

## 5. Uygulama Mimarisi

```
MainActivity (tek Activity)
│
├── NavHostFragment  ← Navigation Component
│   ├── Auth Screens      (BottomNav yok)
│   ├── Admin Screens     (bottomNavAdmin  — 5 tab)
│   └── Lecturer Screens  (bottomNavLecturer — 2 tab)
│
├── BottomNavigationView (Admin)   ← FrameLayout içinde, aynı anchor
└── BottomNavigationView (Lecturer)
```

**Katmanlar:**
```
Fragment (UI) → ViewModel (StateFlow<UiState<T>>) → Repository → Supabase SDK
```

**Session:** `EncryptedSharedPreferences` — `AES256_GCM`  
**Threading:** Tüm ağ çağrıları `Dispatchers.IO` üzerinde, `withContext` ile  
**Paralel yükleme:** `coroutineScope { async { } + async { } }.await()` pattern  
**Hata yönetimi:** `runCatching { }.onSuccess { }.onFailure { }` + sealed `UiState`

---

## 6. Roller ve Ekranlar

### 6.1 Auth Ekranları (BottomNav yok, 2 ekran)

| Ekran | Fragment | Durum |
|-------|----------|-------|
| Giriş | `LoginFragment` | ✅ Tam çalışıyor |
| Şifre Değiştir | `PasswordChangeFragment` | ✅ Tam çalışıyor |

**Login akışı:**
1. Kullanıcı username + password girer
2. `users` tablosundan kullanıcı çekilir
3. `PasswordHasher.sha256(password)` ile hash karşılaştırılır
4. Eşleşirse `SessionManager`'a `userId`, `username`, `role`, `lecturerId` kaydedilir
5. `role == "admin"` → AdminHome'a yönlendir
6. `role == "lecturer" && must_change_password == true` → PasswordChange'e yönlendir
7. `role == "lecturer" && must_change_password == false` → LecturerHome'a yönlendir

**PasswordChange akışı:**
- Mevcut şifreyi doğrular, yeni şifreyi `users` tablosuna hash'leyerek yazar
- `must_change_password = false` olarak günceller
- Back butonu **bloke edilmiştir** — şifre değiştirmeden çıkış yok
- Tamamlanınca LecturerHome'a yönlendirir

---

### 6.2 Admin Ekranları (5 tab + 1 alt ekran)

**Tab sırası (sol → sağ):** Home | Calendar | Data | Settings | Classrooms

#### TAB 1 — AdminHomeFragment (Dashboard)
**Durum: ✅ Tam çalışıyor**

3 paralel Supabase sorgusu (`async/await`):
- **Atanmamış Öğretim Üyeleri:** `schedule_entries`'de `lecturer_id`'si olmayan lecturerlar
- **Atanmamış Dersler:** `schedule_entries`'de `course_id`'si olmayan dersler
- **Müsait Derslikler:** Toplam slot sayısından (5 gün × 4 slot = 20) az rezervasyonu olan derslikler

Boş liste → "— None —" yazısı gösterilir. Hata → "Retry" butonu çalışır.

**Supabase çağrıları:**
```
GET /rest/v1/lecturers?select=*,departments(*),users(*)
GET /rest/v1/schedule_entries?select=lecturer_id
GET /rest/v1/courses?select=*,departments(*)
GET /rest/v1/schedule_entries?select=course_id
GET /rest/v1/classrooms?select=*,departments(*)
GET /rest/v1/schedule_entries?select=classroom_id
```

---

#### TAB 2 — AdminCalendarFragment (Departman Programı)
**Durum: ✅ Tam çalışıyor**

Tüm `schedule_entries` çekilir, `CalendarRenderer` ile Mon–Fri × 4 slot grid oluşturulur.
- Dolu hücreler: yeşil arka plan + "DERS_KODU\nDERSLİK_KODU"
- Boş hücreler: gri arka plan
- Yatay + dikey scroll desteği var
- Hata durumunda "Retry" butonu çalışır ✅

**Supabase çağrıları:**
```
GET /rest/v1/schedule_entries?select=*,courses(*,departments(*)),lecturers(*,departments(*),users(*)),classrooms(*,departments(*))
```

---

#### TAB 3 — DataFragment (Veri Yönetimi)
**Durum: ✅ Çalışıyor (manuel form)**

**Bölüm A — Öğretim Üyesi Ekle:**
- Unvan spinner: Dr. / Prof. / Asst. Prof. / Lecturer / Mr. / Ms.
- Ad, Soyad alanları
- Departman spinner (Settings'ten eklenen bölümler)
- "Add Lecturer & Generate Credentials" butonuna basınca:
  - `LecturerRepository.insertLecturerWithUser()` çağrılır
  - `users` tablosuna SHA-256 hash + `must_change_password=true` ile kayıt
  - `lecturers` tablosuna bağlı kayıt
  - `CredentialGenerator` username = `ad_soyad` (Türkçe karakter normalize), password = 6 karakter random alfasayısal
  - Üretilen `username` ve `password` AlertDialog ile gösterilir → Admin bu bilgileri öğretim üyesine iletir

**Bölüm B — Ders Ekle:**
- Ders kodu (otomatik büyük harfe çevrilir), Ders adı, Departman
- Başarıda Toast gösterilir

**Eksik kalan:** Dosyadan toplu import (CSV/Excel) — altyapı yok.

**Supabase çağrıları:**
```
GET /rest/v1/departments?select=*
POST /rest/v1/users          (lecturer kullanıcısı oluşturma)
POST /rest/v1/lecturers      (lecturer profili oluşturma)
POST /rest/v1/courses
```

---

#### TAB 4 — SettingsFragment (Bölüm Yönetimi)
**Durum: ✅ Çalışıyor**

- Mevcut bölümleri listeler
- Yeni bölüm adı girerek eklenebilir
- Boş liste → "No departments yet." gösterilir
- Hata → "Retry" butonu

**Supabase çağrıları:**
```
GET /rest/v1/departments?select=*
POST /rest/v1/departments
```

---

#### TAB 5 — ClassroomsFragment (Derslik Yönetimi)
**Durum: ✅ Çalışıyor**

- Mevcut derslik listesi (oda kodu, kapasite, bölüm)
- Yeni derslik ekleme formu:
  - Oda kodu (zorunlu), kapasite (zorunlu, sıfır/negatif reddedilir)
  - Departman spinner (opsiyonel, "— None —" seçilebilir)
- "Manage Assignments" butonu → Assignment ekranına yönlendirir

**Supabase çağrıları:**
```
GET /rest/v1/classrooms?select=*,departments(*)
GET /rest/v1/departments?select=*
POST /rest/v1/classrooms
```

---

#### ALT EKRAN — AssignmentFragment (Ders Atama)
**Erişim:** Classrooms tab → "Manage Assignments" butonu  
**Durum: ✅ Tam çalışıyor**

**Oluşturma formu (5 spinner):**
- Course, Lecturer, Classroom, Day (Mon–Fri), Time Slot (08:00–17:00)
- "Assign" butonuyla kayıt oluşturulur

**Double-booking koruması (2 aşamalı):**
1. Uygulama seviyesi: Kayıt öncesi `schedule_entries`'de hoca ve derslik çakışması ayrı sorgularla kontrol edilir
2. DB seviyesi: Tablodaki `UNIQUE(lecturer_id, day, time_slot)` ve `UNIQUE(classroom_id, day, time_slot)` kısıtları son güvenlik katmanıdır

**Atama listesi:** Mevcut tüm atamalar listelenir
- Her satırda: Ders kodu, öğretim üyesi adı, gün/saat/derslik
- **"Delete" butonu** → Onay dialogu → `ScheduleRepository.deleteEntry()` → Liste yenilenir
- Boş liste → "No assignments yet." gösterilir

**Supabase çağrıları:**
```
GET /rest/v1/courses?select=*,departments(*)
GET /rest/v1/lecturers?select=*,departments(*),users(*)
GET /rest/v1/classrooms?select=*,departments(*)
GET /rest/v1/schedule_entries?select=*,courses(*),lecturers(*),classrooms(*)
GET /rest/v1/schedule_entries?select=id  (lecturer conflict check)
GET /rest/v1/schedule_entries?select=id  (classroom conflict check)
POST /rest/v1/schedule_entries           (insert)
DELETE /rest/v1/schedule_entries?id=eq.X (delete)
```

---

### 6.3 Lecturer Ekranları (2 tab)

#### TAB 1 — LecturerHomeFragment (Ana Sayfa)
**Durum: ✅ Tam çalışıyor**

- Hoş geldiniz mesajı: "Welcome, Dr. Ayşe Yılmaz"
- Bölüm adı
- O haftaki ders sayısı (kart içinde)
- Veriler paralel yüklenir (`async/await`)
- Hata → "Retry" butonu

**Supabase çağrıları:**
```
GET /rest/v1/lecturers?select=*,departments(*),users(*)&user_id=eq.{userId}
GET /rest/v1/schedule_entries?select=*,...&lecturer_id=eq.{lecturerId}
```

---

#### TAB 2 — CalendarFragment (Kişisel Program)
**Durum: ✅ Tam çalışıyor**

- Sadece giriş yapan öğretim üyesinin derslerini gösterir
- `CalendarRenderer` ile aynı Mon–Fri × 4 slot grid
- Dolu hücreler: yeşil; boş hücreler: gri
- Hata → "Retry" butonu

**Supabase çağrıları:**
```
GET /rest/v1/schedule_entries?select=*,...&lecturer_id=eq.{lecturerId}
```

---

## 7. Çıkış (Logout)

**Durum: ✅ Çalışıyor**

- Her ekranda sağ üst köşede ActionBar overflow menüsü → "Log Out"
- Login ve PasswordChange ekranlarında gizlenir
- Tıklanınca: `SessionManager.clear()` → back stack tamamen temizlenir → Login ekranına
- Tekrar giriş yapıldığında bottom nav yeniden doğru rolle kurulur

---

## 8. Zaman Dilimleri ve Günler

Kod içinde sabit tanımlanmış (`ScheduleEntry.kt`):

| Değer | Açıklama |
|-------|----------|
| `DAYS` | Monday, Tuesday, Wednesday, Thursday, Friday |
| `TIME_SLOTS` | 08:00-10:00 / 10:00-12:00 / 13:00-15:00 / 15:00-17:00 |

Toplam slot: 5 × 4 = **20 slot/hafta**

---

## 9. Kimlik Bilgileri Şifrelemesi

**Şifre saklama:** SHA-256 (hex string, salt yok)  
**Üretim için not:** bcrypt/Argon2 olmalı; bu öğrenci projesi için yeterli.

**Kullanıcı adı üretme kuralı (`CredentialGenerator`):**
- Format: `{ad}_{soyad}` (küçük harf, Türkçe karakter normalize, harf dışı karakterler kaldırılır)
- Örnek: "Dr. Ahmet Çelik" → `ahmet_celik`
- Parola: 6 karakter random (büyük harf + küçük harf + rakam)

**Session depolama:** `EncryptedSharedPreferences` (AES-256-SIV key, AES-256-GCM value)  
Saklanır: `userId`, `username`, `role`, `lecturerId`

---

## 10. Realtime Altyapısı

Kod yazılmıştır ama **şu an hiçbir ekran tarafından kullanılmıyor** (dead code):

| Flow | Repository metodu | Aktif mi? |
|------|-------------------|-----------|
| Ders değişiklikleri | `CourseRepository.observeCourses()` | ❌ Kullanılmıyor |
| Program değişiklikleri | `ScheduleRepository.observeSchedule()` | ❌ Kullanılmıyor |

Tüm veriler one-shot `suspend fun` ile çekiliyor. Gerçek zamanlı güncelleme yok.  
Bu flow'lar eklenirse, Assignment veya Calendar ekranları manuel yenileme gerektirmez.

---

## 11. Tam Olarak Çalışan Özellikler ✅

| # | Özellik |
|---|---------|
| 1 | Admin girişi |
| 2 | Lecturer girişi |
| 3 | İlk giriş zorunlu şifre değiştirme (back butonu bloke) |
| 4 | Admin dashboard — 3 panel, paralel yükleme |
| 5 | Admin takvim — tüm program grid |
| 6 | Bölüm ekleme + listeleme |
| 7 | Öğretim üyesi ekleme + otomatik kimlik bilgisi üretme + dialog gösterme |
| 8 | Ders ekleme |
| 9 | Derslik ekleme (departman seçimi ile) |
| 10 | Ders atama (5 spinner) |
| 11 | Double-booking önleme (hoca + derslik çakışması) |
| 12 | Atama silme (onay dialogu ile) |
| 13 | Lecturer ana sayfası (profil + haftalık sayı) |
| 14 | Lecturer kişisel takvim |
| 15 | Logout (overflow menü, back stack temizleme) |
| 16 | Boş liste durumu ("— None —" / "No assignments yet.") |
| 17 | Hata durumunda Retry butonu (tüm ekranlarda) |
| 18 | EncryptedSharedPreferences oturum kalıcılığı |
| 19 | Loading indicator (ProgressBar) tüm ekranlarda |
| 20 | Navhost constraint fix — lecturer bottom nav artık içeriği örtmüyor |

---

## 12. Eksik / Yapılmayan Özellikler ❌

| # | Eksik | Öncelik | Not |
|---|-------|---------|-----|
| 1 | Derslik silme/düzenleme | Orta | Sadece ekleme var |
| 2 | Ders silme/düzenleme | Orta | Sadece ekleme var |
| 3 | Öğretim üyesi silme/düzenleme | Orta | Sadece ekleme var |
| 4 | Dosyadan toplu import (CSV/Excel) | Düşük | DataFragment'te manuel form var ama dosya okuma yok |
| 5 | Realtime canlı güncelleme | Düşük | Altyapı var, Fragment'lara bağlanmadı |
| 6 | Supabase RLS politikaları | Güvenlik | Kapalı; production'da açılıp politika yazılmalı |
| 7 | Admin şifre değiştirme | Düşük | Sadece lecturer ilk giriş şifre değişimi var |
| 8 | SHA-256 → bcrypt | Güvenlik | Akademik proje için kabul edilebilir |
| 9 | Unit / UI testler | Düşük | Test dosyası yok |
| 10 | Dependency Injection (Hilt/Koin) | Teknik | Repository'ler ViewModel'de inline instantiate |

---

## 13. Build & Run

### Gereksinimler
- Android Studio Hedgehog veya üstü
- JDK 17
- Android device/emulator (API 26+)
- İnternet bağlantısı (Supabase erişimi)

### local.properties Kurulumu
```
# android.sdk.dir otomatik eklenir, şunları ekle:
SUPABASE_URL=https://lcnganxesvgbfiorifig.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### İlk Çalıştırma Kontrol Listesi
1. ☐ `local.properties`'te URL ve ANON_KEY dolu
2. ☐ Supabase'de 6 tablo oluşturuldu (SQL Editor)
3. ☐ RLS 6 tablo için devre dışı bırakıldı
4. ☐ `users` tablosuna admin kaydı eklendi (SHA-256 hash ile)
5. ☐ Gradle sync başarılı
6. ☐ APK build (`./gradlew assembleDebug`)
7. ☐ Admin ile giriş → dashboard boş görünüyorsa normal (veri yok)
8. ☐ Settings'ten bölüm ekle → Data'dan öğretim üyesi ve ders ekle → Classrooms'tan derslik ekle → Manage Assignments'ta atama yap

### Sık Karşılaşılan Sorunlar

| Hata | Neden | Çözüm |
|------|-------|-------|
| `HTTP 400 / unauthorized` | RLS açık | `ALTER TABLE x DISABLE ROW LEVEL SECURITY` |
| `HTTP 406 Not Acceptable` | Yanlış Accept header | Supabase SDK sürümü uyumsuz |
| Login hatalı diyor | admin kaydı yok / hash yanlış | `users` tablosunu kontrol et |
| Build: `could not find supabase-kt:bom` | Gradle group ID yanlış | `libs.versions.toml`'da `jan-tennert.supabase` olmalı |
| `Unresolved: awaitClose` | Kotlin 2.0'da explicit import gerekiyor | `import kotlinx.coroutines.channels.awaitClose` |

---

## 14. Proje Dosya Yapısı

```
UniScheduler/
├── app/src/main/
│   ├── java/com/unischeduler/
│   │   ├── MainActivity.kt                  ← Tek Activity, nav + logout
│   │   ├── data/
│   │   │   ├── model/                       ← User, Department, Lecturer, Course,
│   │   │   │                                    Classroom, ScheduleEntry
│   │   │   ├── remote/SupabaseClient.kt     ← Singleton Supabase client
│   │   │   └── repository/                  ← AuthRepo, DeptRepo, LecturerRepo,
│   │   │                                        CourseRepo, ClassroomRepo, ScheduleRepo
│   │   ├── ui/
│   │   │   ├── auth/                        ← LoginFragment/VM, PasswordChangeFragment/VM
│   │   │   ├── admin/                       ← AdminHome, AdminCalendar, Data, Settings,
│   │   │   │                                    Classrooms, Assignment (Fragment+VM)
│   │   │   └── lecturer/                    ← LecturerHome, Calendar (Fragment+VM+Renderer)
│   │   └── util/
│   │       ├── UiState.kt                   ← sealed class: Idle/Loading/Success/Error
│   │       ├── SessionManager.kt            ← EncryptedSharedPreferences
│   │       ├── CredentialGenerator.kt       ← username + password üretme
│   │       ├── PasswordHasher.kt            ← SHA-256
│   │       └── Extensions.kt               ← collectFlow (repeatOnLifecycle)
│   └── res/
│       ├── layout/                          ← activity_main + tüm fragment XML'leri
│       ├── menu/                            ← menu_admin_bottom_nav, menu_lecturer_bottom_nav,
│       │                                        menu_overflow (logout)
│       └── navigation/nav_graph.xml         ← tüm destinasyonlar ve aksiyonlar
├── supabase/README.md                       ← RLS disable SQL
├── eksikler.md                              ← Senior dev denetim raporu
└── sondurum.md                              ← Bu dosya
```

---

## 15. Navigation Graph — Tüm Destinasyonlar

```
loginFragment
│   ├── action_login_to_adminHome       → adminHomeFragment (popUpToInclusive)
│   ├── action_login_to_lecturerHome    → lecturerHomeFragment (popUpToInclusive)
│   └── action_login_to_passwordChange  → passwordChangeFragment (popUpToInclusive)
│
passwordChangeFragment
│   └── action_passwordChange_to_lecturerHome → lecturerHomeFragment
│
[Admin — BottomNav]
├── adminHomeFragment
├── adminCalendarFragment
├── dataFragment
├── settingsFragment
└── classroomsFragment
    └── action_classrooms_to_assignment → assignmentFragment
            (Bottom nav'da direkt tab yok — Classrooms üzerinden erişilir)

[Lecturer — BottomNav]
├── lecturerHomeFragment
└── calendarFragment
```

> **Dikkat:** `assignmentFragment`'a admin bottom nav'ından **direkt sekme yok**.  
> Classrooms sekmesi → "Manage Assignments" butonuyla ulaşılır.

---

## 16. Önemli Notlar

- **Supabase Inactive Pause:** Supabase free tier projeleri 1 hafta inaktivite sonrası duraklar. İlk istekte ~5 sn gecikme olabilir, sonrasında normalleşir. Resume için dashboard'dan "Resume" butonuna basılır.
- **users.id tipi:** `UUID` (String olarak Kotlin'de saklanır). `lecturers.id` ise `INT`. Bu farkı `SessionManager`'da dikkat et: `userId: String`, `lecturerId: Int`.
- **Şifre flow:** Lecturer ilk giriş → `must_change_password=true` → PasswordChange ekranı → `must_change_password=false` güncellenir → LecturerHome. Bir sonraki girişte direkt LecturerHome.
- **Derslik departmanı:** Opsiyonel. "— None —" seçilirse `department_id = null` olarak kaydedilir.
