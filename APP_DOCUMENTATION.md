# UniScheduler — Kapsamlı Uygulama Dokümantasyonu

> **Versiyon:** 1.0.0
> **Platform:** Android (minSdk 26 / targetSdk 35)
> **Kotlin:** 2.0.21 | **Jetpack Compose** | **Supabase**

---

## İçindekiler

1. [Proje Genel Bakış](#1-proje-genel-bakış)
2. [Mimari](#2-mimari)
3. [Kullanıcı Rolleri](#3-kullanıcı-rolleri)
4. [Özellikler](#4-özellikler)
5. [Ekranlar ve Akışlar](#5-ekranlar-ve-akışlar)
6. [CSP Algoritması](#6-csp-algoritması)
7. [Bildirim Sistemi](#7-bildirim-sistemi)
8. [Dil ve Tema](#8-dil-ve-tema)
9. [Veri Katmanı](#9-veri-katmanı)
10. [Supabase Şeması](#10-supabase-şeması)
11. [Hata ve Eksiklik Analizi](#11-hata-ve-eksiklik-analizi)
12. [Yapılan Değişiklikler](#12-yapılan-değişiklikler)
13. [Kurulum](#13-kurulum)

---

## 1. Proje Genel Bakış

UniScheduler, üniversite ders programlarını otomatik olarak oluşturan, yöneten ve onay süreçlerini takip eden bir Android uygulamasıdır. Temel felsefesi:

- **Otomatik program üretimi** — CSP (Constraint Satisfaction Problem) algoritması ile
- **Çok katmanlı onay** — Admin + Bölüm Başkanı ikili onay sistemi
- **Excel entegrasyonu** — Veri içe/dışa aktarma
- **Gerçek zamanlı güncellemeler** — Supabase Realtime ile anlık bildirimler

---

## 2. Mimari

```
UniScheduler/
├── domain/           ← Saf Kotlin, bağımlılık yok
│   ├── model/        ← Domain entity'leri
│   ├── repository/   ← Interface tanımları
│   ├── usecase/      ← İş mantığı
│   └── algorithm/    ← CSP çözücü
│
├── data/             ← Supabase implementasyonu
│   ├── remote/dto/   ← Serializable DTO'lar
│   ├── remote/mapper/← DTO ↔ Domain dönüşümleri
│   └── repository/   ← Repository impl'ları
│
├── di/               ← Hilt DI modülleri
│   ├── SupabaseModule
│   ├── DataStoreModule   ← YENİ
│   └── RepositoryModule
│
├── presentation/     ← Compose UI
│   ├── navigation/
│   ├── auth/
│   ├── home/
│   ├── calendar/
│   ├── data/
│   ├── drafts/
│   ├── requests/
│   ├── settings/     ← Tema + Dil + Bildirim — GÜNCELLENDİ
│   ├── splash/
│   └── theme/
│
├── util/             ← Yardımcı sınıflar
│   ├── Constants
│   ├── CredentialGenerator
│   ├── ExcelParser / ExcelExporter / ExcelTemplates
│   ├── TurkishCharUtils
│   └── NotificationHelper  ← YENİ
│
└── worker/           ← YENİ
    └── ScheduleReminderWorker
```

### Teknoloji Yığını

| Katman | Teknoloji |
|---|---|
| UI | Jetpack Compose + Material3 |
| DI | Hilt 2.53.1 + KSP |
| Backend | Supabase (Auth + PostgREST + Storage + Realtime) |
| HTTP | Ktor CIO engine 3.0.1 |
| State | ViewModel + StateFlow |
| Navigation | Compose Navigation (type-safe routes) |
| Ayar Kalıcılığı | DataStore Preferences |
| Bildirim | WorkManager + NotificationCompat |
| Excel | Apache POI 5.3.0 |
| Görsel | Coil 3.0.4 |
| Serialization | KotlinX Serialization 1.7.3 |

---

## 3. Kullanıcı Rolleri

### ADMIN (Sistem Yöneticisi)
- Tüm bölüm, hoca ve ders verilerini yönetir
- Excel ile toplu veri içe aktarır
- Taslak programları onaylar/reddeder
- Değişiklik taleplerini inceler
- Bölüm başkanı yetkilerini (FULL_ACCESS / APPROVAL_REQUIRED / READ_ONLY) yönetir
- Alt navigasyon: Ana Sayfa | Takvim | Veri | Ayarlar

### DEPT_HEAD (Bölüm Başkanı)
- Kendi bölümüne ait program taslakları oluşturur ve düzenler
- Taslakları admin onayına gönderir
- Kendi bölümündeki değişiklik taleplerini ilk kademe olarak inceler
- Hoca müsaitlik gridlerini yönetir
- Alt navigasyon: Ana Sayfa | Takvim | Taslaklar | Ayarlar

### LECTURER (Öğretim Üyesi)
- Kendi müsaitlik gridini (haftanın hangi saatlerinde uygun) doldurur
- Değişiklik talebi oluşturur (zaman/sınıf/ders değişikliği)
- Kendi ders programını görüntüler
- Alt navigasyon: Ana Sayfa | Takvim | Talepler | Ayarlar

### STUDENT (Öğrenci)
- Bölümüne ait onaylanmış ders programını (read-only) görüntüler
- Takvim sekmesinden haftalık görünüme erişir
- Alt navigasyon: Ana Sayfa | Takvim

---

## 4. Özellikler

### 4.1 Otomatik Program Oluşturma (CSP)
- **Algoritma:** Constraint Satisfaction Problem çözücü
- **Değişken sıralaması:** MRV (Minimum Remaining Values) heuristic
- **Sert kısıtlar (ConstraintChecker):**
  - Aynı anda iki derse aynı öğretim üyesi atanamaz
  - Aynı anda aynı sınıfa iki ders atanamaz
  - Kilitli (isLocked=true) dersler taşınamaz
  - Öğretim üyesinin müsait olmadığı slotlara atama yapılamaz
- **Yumuşak skorlama (SoftScorer):**
  - Hocanın tercih ettiği saatler puan kazandırır
  - Ard arda boşluk bırakmayan programlar daha yüksek puan alır
  - Alternatif çözümler arasından en yüksek soft score seçilir

### 4.2 Taslak Onay Süreci
```
DEPT_HEAD → Taslak oluştur → Onaya gönder (PENDING)
    ↓
ADMIN → İnceler → APPROVED / REJECTED (not ile)
    ↓
Onaylanırsa → Canlı programa alınır
```

### 4.3 Değişiklik Talebi (Dual-Routing)
```
LECTURER → Talep oluştur
    ↓
Approval Mode = DUAL_APPROVAL:
    DEPT_HEAD → deptHeadStatus = APPROVED / REJECTED
    ADMIN     → adminStatus    = APPROVED / REJECTED
    İkisi de onaylarsa → status = APPROVED

Approval Mode = ADMIN_ONLY:
    Sadece admin inceleme yapar

Approval Mode = DEPT_HEAD_ONLY:
    Sadece bölüm başkanı inceleme yapar
```

### 4.4 Hoca Müsaitlik Grid
- Haftanın aktif günleri × slot sayısı kadar bir grid
- Her cell: yeşil (müsait) / kırmızı (müsait değil)
- Tıklayarak toggle
- Kaydedince Supabase'e upsert edilir

### 4.5 Excel İçe/Dışa Aktarma
- **İçe aktarma:** Apache POI ile `.xlsx` okuma
  - Hoca listesi + ders atamaları tek dosyadan içe aktarılır
  - Preview ekranında kayıtları göster, onaylayarak kaydet
- **Dışa aktarma:**
  - Ders programı Excel olarak export
  - Müsaitlik verileri export
  - Giriş kimlik bilgileri (kullanıcı adı/şifre) export
  - Taslak export

### 4.6 Kimlik Bilgisi Üretme
- `CredentialGenerator` + `TurkishCharUtils` ile otomatik kullanıcı adı
- Örnek: "Ahmet Yılmaz" → "a.yilmaz" + random sayı suffix
- Güvenli rastgele şifre üretimi
- Excel dosyası olarak admin'e dışa aktarılır

### 4.7 Ders Başlangıcı Bildirimleri (YENİ)
- WorkManager ile her 15 dakikada bir arka planda çalışır
- Aktif gün + saat kontrolü ile yaklaşan dersleri tespit eder
- Ayarlardan açılıp kapatılabilir
- Hatırlatma süresi: 5 / 10 / 15 / 30 dakika seçenekleri
- Android 13+ için `POST_NOTIFICATIONS` izni

### 4.8 Dil Desteği (YENİ)
- **Türkçe** (varsayılan)
- **İngilizce**
- Ayarlar'dan değiştirilebilir
- **"Sistem varsayılanı"** seçeneği: telefon diline göre otomatik
- DataStore ile persist edilir — uygulama kapanıp açılsa da hatırlanır
- `values/strings.xml` (TR) + `values-en/strings.xml` (EN)

### 4.9 Tema (YENİ)
- **Sistem varsayılanı** (telefon temasını takip eder)
- **Açık tema**
- **Koyu tema**
- Ayarlar'dan değiştirilebilir
- DataStore ile persist edilir

---

## 5. Ekranlar ve Akışlar

### SplashScreen
- Supabase session kontrolü yapar
- Session varsa → Home, yoksa → Login

### LoginScreen
- Email / şifre girişi
- Hilt LoginViewModel üzerinden AuthRepository
- Başarıda Home'a yönlendirir

### HomeScreen
- Kullanıcı karşılama (Ad, soyad, rol)
- Rol bazlı quick stat kartlar
  - Admin/DeptHead: Bekleyen taslak sayısı, bekleyen talep sayısı
  - Lecturer: Ders programı kısayolu, talepler kısayolu
  - Student: Bölüm programı bilgilendirmesi

### CalendarScreen (Takvim)
- Rol bazlı:
  - Admin/DeptHead: Haftalık program grid'i + program oluştur butonu
  - Lecturer: Kendi ders grid'i + müsaitlik grid'i
  - Student: Bölüm ders programı (read-only)
- Saat dilimleri: Config'e göre dinamik
- Renkli kart yapısı (courseColorHex)

### ScheduleConfigScreen
- Bölüm bazlı zaman dilimi ayarı
- Başlangıç/bitiş saati, slot süresi, aktif günler seçimi

### AlternativesScreen
- CSP algoritmasından gelen alternatif çözümler
- Her alternatifin soft score gösterimi
- Seçilerek aktif program olarak kaydedilir

### DataScreen (Veri Yönetimi)
- Excel içe aktarma butonu
- Program/müsaitlik/kimlik dışa aktarma
- Hoca listesi (LecturerAccordion ile collapse/expand)
  - Her hocanın derslerini gösterir

### ImportPreviewScreen
- İçe aktarılan Excel satırları önizleme
- Onaylayıp kaydet

### DraftListScreen
- Bölümün tüm taslak programları
- Durum badge (Taslak / Onay Bekliyor / Onaylandı / Reddedildi)
- DeptHead: Oluştur/Düzenle butonları
- Admin: İnceleme butonları

### DraftEditorScreen
- Taslak başlığı + atama listesi
- Kaydedebilir, onaya gönderebilir (Submit)

### DraftReviewScreen
- Draft detayı ve atamalar
- Admin: Not girerek Onayla / Reddet

### RequestListScreen
- Rol bazlı liste:
  - Lecturer: Kendi talepleri
  - DeptHead: Bölüm talepleri
  - Admin: Tüm bekleyen talepler
- Durum renk kodlaması

### RequestDetailScreen
- Talep türü, mevcut/istenen durum, gerekçe
- İki kademe onay durumu
- Admin/DeptHead: Not ile Onayla/Reddet

### CreateRequestScreen
- Talep türü dropdown (Zaman/Sınıf/Ders/Diğer)
- Mevcut durum, istenen değişiklik, gerekçe
- Gönder butonu

### SettingsScreen (GÜNCELLENDİ)
- Hesap bilgileri (Ad, email, rol)
- **Görünüm kartı:** Tema seçici (Sistem/Açık/Koyu)
- **Dil kartı:** Dil seçici (Sistem/Türkçe/English)
- **Bildirimler kartı:** Açma/kapama toggle + hatırlatma süresi
- **[ADMIN ONLY] Bölüm yetkileri kartı**
- Çıkış butonu

---

## 6. CSP Algoritması

```
GenerateScheduleUseCase
    ↓
CSPSolver.solve(courses, lecturers, config, availability)
    ↓
MRVHeuristic.getNextVariable()   ← En kısıtlı dersi seç
    ↓
ConstraintChecker.isValid(assignment)  ← Sert kısıtları kontrol et
    ↓
[Geçerliyse] Atamayı yap
[Değilse]    Backtrack
    ↓
Tüm dersler atanana ya da seçenekler tükenene kadar devam et
    ↓
SoftScorer.score(solution)   ← Her çözüme puan ver
    ↓
Top-N alternatif → AlternativesScreen'e aktar
```

**Sert Kısıtlar:**
1. Lecturer çakışma yasağı (aynı slot, farklı ders)
2. Classroom çakışma yasağı (aynı slot, aynı sınıf)
3. Kilitli ders immutability
4. Hoca müsaitlik sınırı

**Yumuşak Skor Faktörleri:**
- Tercih edilen slot = +puan
- Müsait olmayan ama mecburen atanan = -puan
- Ardışık ders boşlukları = -puan
- Gün başı/sonu doluluk dengesi = +puan

---

## 7. Bildirim Sistemi

### Akış
```
UniSchedulerApp.onCreate()
    → NotificationHelper.createNotificationChannel()
    → WorkManager.enqueueUniquePeriodicWork("schedule_reminder", 15 dk)
        ↓
ScheduleReminderWorker.doWork() (her 15 dk)
    → DataStore'dan ayarları oku (notificationsEnabled, advanceMinutes)
    → Bildirim kapalıysa → Result.success() (hiçbir şey yapma)
    → Mevcut gün + saat hesapla
    → getAssignmentsByDepartment() ile dersleri getir
    → Her ders için: classStartMinutes - currentMinutes ≈ advanceMinutes?
        → Evet → NotificationHelper.showClassNotification()
    → Result.success()
```

### Kanal Bilgileri
- **Channel ID:** `class_reminders`
- **Öncelik:** HIGH (heads-up bildirim)
- **Titreşim:** Açık

### İzinler
```xml
<!-- API >= 33 için zorunlu -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

---

## 8. Dil ve Tema

### Dil Sistemi
- `AppLanguage` enum: SYSTEM / TURKISH / ENGLISH
- `SettingsRepositoryImpl`: DataStore'da `KEY_LANGUAGE` string key
- `MainActivity.applyLanguage()`: `Resources.updateConfiguration()` ile lokale uygulama
- String kaynakları:
  - `res/values/strings.xml` → Türkçe
  - `res/values-en/strings.xml` → İngilizce

### Tema Sistemi
- `AppTheme` enum: SYSTEM / LIGHT / DARK
- `SettingsRepositoryImpl`: DataStore'da `KEY_THEME` string key
- `UniSchedulerTheme(appTheme)`: `isSystemInDarkTheme()` ile birleşik karar
- Dynamic color (Material You) kaldırıldı → Tutarlı marka rengi korunuyor
- Primary: `#1565C0` (Açık) / `#9ECAFF` (Koyu)

---

## 9. Veri Katmanı

### DTO → Domain Mapper'lar (tam liste)

| Mapper | DTO | Domain |
|---|---|---|
| `CourseMapper` | `CourseDto` | `Course` |
| `LecturerMapper` | `LecturerDto` | `Lecturer` |
| `ScheduleAssignmentMapper` *(YENİ)* | `ScheduleAssignmentDto` | `ScheduleAssignment` |
| `ChangeRequestMapper` *(YENİ)* | `ChangeRequestDto` | `ChangeRequest` |
| `ScheduleDraftMapper` *(YENİ)* | `ScheduleDraftDto` | `ScheduleDraft` |
| `AvailabilitySlotMapper` *(YENİ)* | `AvailabilitySlotDto` | `AvailabilitySlot` |

### Repository Implementations

| Repo | Supabase Endpoint | Açıklama |
|---|---|---|
| `AuthRepositoryImpl` | `/auth/v1/` | Giriş/çıkış, session yönetimi |
| `CourseRepositoryImpl` | `/courses` | Ders CRUD |
| `LecturerRepositoryImpl` | `/lecturers`, `/departments` | Hoca + bölüm CRUD |
| `ScheduleRepositoryImpl` | `/schedule_assignments`, `/availability_slots`, `/schedule_configs` | Program + müsaitlik |
| `DraftRepositoryImpl` | `/schedule_drafts` | Taslak yaşam döngüsü |
| `RequestRepositoryImpl` | `/change_requests` | Değişiklik talepleri |
| `SettingsRepositoryImpl` *(YENİ)* | DataStore | Yerel uygulama tercihleri |

---

## 10. Supabase Şeması

### Tablolar

| Tablo | Satır Sayısı (seed) | Açıklama |
|---|---|---|
| `profiles` | auth.users 1:1 | Kullanıcı profilleri |
| `departments` | 3 | Bölümler |
| `lecturers` | N | Öğretim üyeleri |
| `courses` | N | Dersler |
| `course_lecturers` | N | Ders-Hoca M:N |
| `schedule_configs` | per dep. | Zaman dilimi ayarları |
| `schedule_assignments` | N | Canlı program atamaları |
| `availability_slots` | N | Hoca müsaitlik grid |
| `schedule_drafts` | N | Program taslakları |
| `change_requests` | N | Değişiklik talepleri |
| `import_logs` | N | Excel import geçmişi |

### RLS Özeti

| Tablo | ADMIN | DEPT_HEAD | LECTURER | STUDENT |
|---|---|---|---|---|
| profiles | FULL | Kendi bölümü | Kendi | — |
| departments | FULL | SELECT | SELECT | — |
| lecturers | FULL | Kendi bölümü CRUD | Kendi SELECT | Kendi bölümü SELECT |
| courses | FULL | Kendi bölümü CRUD | SELECT | SELECT |
| schedule_assignments | FULL | FULL_ACCESS izni | SELECT | SELECT |
| availability_slots | FULL | Bölümü | Kendi CRUD | — |
| schedule_drafts | FULL | Kendi CRUD | — | — |
| change_requests | FULL | Bölümü SELECT+UPDATE | Kendi INSERT+SELECT | — |

---

## 11. Hata ve Eksiklik Analizi

### Kritik Sorunlar (Düzeltildi)

| # | Sorun | Çözüm |
|---|---|---|
| 1 | UI'da %100 hardcoded Türkçe string | `strings.xml` + `stringResource()` ile i18n |
| 2 | Bildirim sistemi tamamen yoktu | WorkManager + NotificationHelper |
| 3 | SettingsViewModel'de DataStore yok, tercihler persist edilmiyordu | DataStore Preferences entegrasyonu |
| 4 | `ScheduleAssignment`, `ChangeRequest`, `Draft`, `AvailabilitySlot` mapper'ları yoktu | 4 yeni mapper oluşturuldu |
| 5 | Theme.kt: `dynamicColor=true` varsayılan, kullanıcı tercihi yoktu | `AppTheme` enum + override seçeneği |
| 6 | `AppNavHost`'ta `splashVm` ismi tutarsız, userRole reactive değildi | `authVm` olarak yeniden adlandırıldı, reactive bağlantı kuruldu |
| 7 | `HomeScreen` signature'ında `userRole` parametresi yoktu | `userRole: UserRole` parametresi eklendi |
| 8 | `BottomNavBar`'da tüm label'lar hardcoded Türkçe string | `labelRes: Int` + `stringResource()` |
| 9 | `SettingsScreen` mesaj yönetimi: snackbar'dan sonra clear edilmiyordu | `clearMessage()` + `LaunchedEffect` ile düzeltildi |
| 10 | `AndroidManifest`'te `POST_NOTIFICATIONS` eksikti | Eklendi |
| 11 | `STUDENT` rolü HomeScreen'de kart yoktu | Mevcut HomeScreen'de zaten ekliydi, navigation'da aktif edildi |
| 12 | `SettingsViewModel` permission update sonrası `loadSettings()` + message race condition | Optimistic local state update ile çözüldü |

### Mimari Gözlemler (Bilgi)

| Gözlem | Durum |
|---|---|
| Lecturer ve Settings BottomNav | Lecturer'ın ayarlar sekmesi eklendi (iyi tercih) |
| `createLecturerAccount` AuthRepository'de | Supabase Admin SDK gerektiriyor — sadece server-side yapılabilir |
| Realtime subscription Kotlin'de | SQL'de tanımlı ama client'ta abone yok — ileriki geliştirme |
| Password plain-text `lecturers` tablosunda | Güvenlik riski — ileriki geliştirmede hash veya vault kullanılabilir |

---

## 12. Yapılan Değişiklikler

### Yeni Dosyalar

| Dosya | Açıklama |
|---|---|
| `domain/model/AppSettings.kt` | Tema + Dil + Bildirim model |
| `domain/repository/SettingsRepository.kt` | Ayar tercihleri interface |
| `data/repository/SettingsRepositoryImpl.kt` | DataStore ile gerçekleme |
| `di/DataStoreModule.kt` | DataStore Hilt module |
| `util/NotificationHelper.kt` | Kanal oluşturma + bildirim gösterme |
| `worker/ScheduleReminderWorker.kt` | Arka plan bildirim worker'ı |
| `data/remote/mapper/ScheduleAssignmentMapper.kt` | Eksik mapper (yeni) |
| `data/remote/mapper/ChangeRequestMapper.kt` | Eksik mapper (yeni) |
| `data/remote/mapper/ScheduleDraftMapper.kt` | Eksik mapper (yeni) |
| `data/remote/mapper/AvailabilitySlotMapper.kt` | Eksik mapper (yeni) |
| `res/values-en/strings.xml` | İngilizce string kaynakları |
| `ENDPOINTS.md` | Supabase endpoint dokümantasyonu |
| `APP_DOCUMENTATION.md` | Bu dosya |

### Güncellenen Dosyalar

| Dosya | Değişiklik |
|---|---|
| `gradle/libs.versions.toml` | DataStore, WorkManager, hilt-work versiyonları eklendi |
| `app/build.gradle.kts` | DataStore, WorkManager bağımlılıkları eklendi |
| `AndroidManifest.xml` | POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, WorkManager provider |
| `UniSchedulerApp.kt` | HiltWorkerFactory + WorkManager init + NotificationChannel |
| `presentation/MainActivity.kt` | SettingsViewModel inject, applyLanguage() |
| `presentation/theme/Theme.kt` | `AppTheme` parametreli, dynamic color kaldırıldı |
| `presentation/navigation/AppNavHost.kt` | splashVm→authVm, HomeScreen userRole parametresi |
| `presentation/navigation/BottomNavBar.kt` | `labelRes: Int`, Lecturer'a Settings+Requests eklendi |
| `presentation/home/HomeScreen.kt` | `userRole: UserRole` parametresi eklendi |
| `presentation/settings/SettingsViewModel.kt` | SettingsRepository inject, DataStore reaktif gözlem |
| `presentation/settings/SettingsScreen.kt` | Tema/Dil/Bildirim kartları, `stringResource()` dönüşümü |
| `di/RepositoryModule.kt` | `SettingsRepository` binding eklendi |
| `res/values/strings.xml` | Yeni string key'leri eklendi (tema/dil/bildirim/rol/hata) |

---

## 13. Kurulum

### 1. local.properties
```
sdk.dir=C:\Users\...\Android\Sdk
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=eyJhbGci...
```

### 2. Supabase SQL (sırayla)
```
supabase/01_tables.sql
supabase/02_rls_policies.sql
supabase/03_functions_triggers.sql
supabase/04_storage.sql
supabase/05_realtime.sql
supabase/06_seed.sql
```

### 3. Build
```bash
./gradlew assembleDebug
```

### 4. İlk Admin Hesabı
Supabase Dashboard → Authentication → Users → Invite user
Ardından `profiles` tablosuna:
```sql
UPDATE public.profiles SET role = 'ADMIN' WHERE id = '<user-uuid>';
```

---

*Oluşturulma tarihi: Mart 2026 — UniScheduler v1.0.0*
