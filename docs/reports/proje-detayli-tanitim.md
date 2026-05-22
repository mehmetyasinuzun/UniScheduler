---
title: "UniScheduler — Detaylı Proje Tanıtım Rehberi"
subtitle: "Sunum Notları + Klasör/Dosya Bazlı Açıklamalar"
date: "Mayıs 2026"
fontsize: 11pt
geometry: margin=2.2cm
---

# UniScheduler — Detaylı Proje Tanıtım Rehberi

> Bu belge, projeyi hocaya 10 dakikalık bir sunumda anlatırken kullanmak ve sonrasında gelebilecek sorulara hazır olmak için hazırlandı. Önce **konuşma akışı**, sonra **klasör/dosya haritası**, en sonda **mimariye ait kritik kararlar** var. Bu üçünü okuduktan sonra projenin nerede ne olduğunu net biliyor olacaksın.

## 1. Sunum Akışı (10 Dakika)

### Dakika 0–1: Proje Nedir?

> "UniScheduler, üniversitelerin ders programlarını yönetmek için üç parçalı bir sistem. Android mobil uygulama, Node.js tabanlı süper-admin web paneli, ve Supabase üzerinde PostgreSQL veritabanı. Aynı veritabanında birden fazla üniversitenin verisi yan yana saklanır ama RLS politikaları sayesinde tamamen yalıtılmış olarak çalışır."

### Dakika 1–2: Üç Rol

- **Süper-admin** → Web panelinden organizasyon ekler, admin oluşturur, hata loglarını ve giriş denemelerini izler.
- **Admin** → Mobil uygulamada kendi kurumunun hoca/ders/derslik kayıtlarını yönetir, atama yapar.
- **Hoca** → Mobil uygulamada kendi programını görür, müsaitlik işaretler, PDF/iCal indirir.

Her rolün ne yapabileceğini RLS satır satır kontrol ediyor — kod tarafında "if admin yapabilir" diye yazmaya gerek yok, veritabanı reddediyor.

### Dakika 2–4: Veritabanı

14 tablo var ama ana çekirdek 9 tane:
`organizations → users → lecturers → departments → courses → classrooms → offerings → schedule_entries → lecturer_availability`

Diğer 4 tablo gözlemleme için: `audit_log`, `login_attempts`, `client_error_logs`, `super_admins`.

**RLS örneği:**

```sql
CREATE POLICY lect_select ON lecturers
    FOR SELECT TO authenticated
    USING (org_id = public.current_org_id());
```

Kullanıcı kendi `org_id`'sini taşıyan JWT ile gelir; `current_org_id()` fonksiyonu bunu okur. SELECT sadece o satırları döner. Mobil uygulama hata yapsa da farklı kurumun verisi sızmaz.

### Dakika 4–6: Çakışma Kontrolü (Anahtar Konu)

İki admin aynı anda atama yapsa ne olur? Klasik "Time-of-Check to Time-of-Use" yarış koşulu. İki katmanlı çözüm:

1. **Uygulama katmanı:** `AssignmentFragment` form gönderilmeden önce `SELECT … WHERE lecturer_id = L AND day = D AND overlap(…)` çağırıyor. Çakışma varsa kullanıcıya diyalog gösteriyor.
2. **Veritabanı katmanı:** `prevent_schedule_overlap` trigger'ı `BEFORE INSERT/UPDATE` çalışıyor. Transaction içinde aynı sorguyu tekrar yapıyor — TX_A commit ettiyse TX_B'nin trigger'ı RAISE EXCEPTION fırlatıyor.

Yani uygulama bug'lı olsa bile veritabanı çakışmayı kabul etmiyor.

### Dakika 6–8: Otomatik Program Üretici

Açılmamış dersleri otomatik program haline getirmek için bir algoritma yazdım. Greedy + skor tabanlı:

1. **Slot üret:** `activeDays × dayStart..dayEnd / timeStep` ile tüm boş zaman dilimlerini listele.
2. **Skor ver:** Her slot için hoca müsaitliği, derslik dolu mu, öğrenci grubu çakışması, tercihler (compact / spread, max daily, preferred hours) ile bir skor hesapla.
3. **En esnek olmayan dersi önce yerleştir:** Belli bir hocanın özel olarak müsait olduğu az slot varsa, o ders önce atansın.
4. **5 alternatif:** Aynı algoritma 5 farklı seed ile çalışıyor; admin alternatifler arasından seçim yapıyor.

Kod: `app/src/main/java/com/unischeduler/scheduler/ScheduleGenerator.kt`.

### Dakika 8–9: Süper-Admin Paneli + CTI

Süper-admin tarayıcıdan paneli açıyor. 8 sayfa: Organizasyonlar, Admin Kullanıcılar, Dashboard, Açılan Dersler, Haftalık Çizelge, Müsaitlik, Hata Logları, Giriş Denemeleri.

Giriş Denemeleri sayfası en gelişmiş: her giriş denemesi (başarı/başarısız, cihaz parmak izi, IP, ülke bayrağı, risk skoru) listeleniyor. Saatlik dağılım Chart.js bar grafiği, son 30 günün trendi line chart. CSV olarak indirilebiliyor. Eşik aşılınca Slack/Discord webhook'a alert gönderilebiliyor.

### Dakika 9–10: CI/CD ve Sürdürülebilirlik

- **GitHub Actions:** her `git push`'ta otomatik build + Robolectric test + lint çalışıyor.
- **Tag attığında** otomatik release APK üretiliyor (`v1.2.8` gibi).
- **Dependabot** haftalık dependency taraması yapıyor.
- **dbmate** ile şema migration'ları versiyonlu — production'da kolon eklemek için artık `schema.sql`'i sıfırlamaya gerek yok.

> "Toparlamak gerekirse: gereksinim çoklu cihazdan erişilebilir, rol bazlı, çakışma korumalı bir program sistemiydi. Bunun üstüne çok-kiracılı mimari, CTI, otomatik program üretici, ve CI/CD ekledim çünkü her biri öğrenmek istediğim ayrı bir konuydu."

---

## 2. Klasör/Dosya Haritası

### `app/` — Android Mobil Uygulama

Kotlin + MVVM mimarisi. Ana paket: `com.unischeduler`.

#### `app/src/main/java/com/unischeduler/`

| Klasör | İçerik | Önemli dosyalar |
|---|---|---|
| `data/model/` | Veritabanı tablolarının Kotlin karşılığı, hepsi `@Serializable` | `Lecturer.kt`, `Course.kt`, `Classroom.kt`, `ScheduleEntry.kt`, `Offering.kt`, `LecturerAvailability.kt`, `OrgSettings.kt`, `User.kt`, `Department.kt`, `InsertModels.kt` |
| `data/remote/` | Supabase bağlantısı (singleton) | `SupabaseClient.kt` — auth, postgrest, realtime modülleri tek noktadan |
| `data/repository/` | Supabase çağrılarını sarmalayan suspend fonksiyonlar | `AuthRepository.kt`, `LecturerRepository.kt`, `CourseRepository.kt`, `ClassroomRepository.kt`, `OfferingRepository.kt`, `ScheduleRepository.kt`, `AvailabilityRepository.kt`, `DepartmentRepository.kt`, `OrgSettingsRepository.kt`, `ErrorLogRepository.kt` |
| `notif/` | Bildirim sistemi (AlarmManager) | `ReminderScheduler.kt` — yarınki dersleri 23:00'te alarmla planlar · `ReminderReceiver.kt` — alarm geldiğinde notification gösterir · `BootCompletedReceiver.kt` — telefon yeniden başladığında alarmları kurar · `DailyReminderWorker.kt` — WorkManager periyodik iş · `NotificationHelper.kt` — kanal yönetimi |
| `scheduler/` | Otomatik program üretici | `ScheduleGenerator.kt` — algoritma (~500 satır) · `SchedulePreferences.kt` — kullanıcı tercih modeli (compact/spread, max daily, vb.) |
| `ui/admin/` | Admin rolü için fragment'lar + ViewModel'ler | `AdminHomeFragment.kt` + ViewModel, `AdminCalendarFragment.kt`, `AssignmentFragment.kt` + ViewModel, `AutoScheduleFragment.kt` + ViewModel, `ClassroomsFragment.kt` + ViewModel, `DataFragment.kt` + ViewModel, `SettingsFragment.kt` + ViewModel, `LecturerScheduleSheet.kt` |
| `ui/auth/` | Login ve şifre değiştirme | `LoginFragment.kt` + `LoginViewModel.kt`, `PasswordChangeFragment.kt` + `PasswordChangeViewModel.kt` |
| `ui/lecturer/` | Hoca rolü için ekranlar | `LecturerHomeFragment.kt` + ViewModel, `AvailabilityFragment.kt` + ViewModel, `CalendarFragment.kt` + ViewModel |
| `ui/onboarding/` | İlk açılış tanıtım ekranı (3 sayfa) | `OnboardingActivity.kt` |
| `ui/shared/` | Birden fazla yerde kullanılan custom view'lar | `WeeklyScheduleView.kt` — haftalık ızgara (Canvas üzerine custom çizim, pinch-to-zoom destekli) · `AvailabilityGridView.kt` — müsaitlik ızgarası |
| `util/` | Yardımcı sınıflar (~22 dosya) | `SessionManager.kt`, `CrashHandler.kt`, `ErrorReporter.kt`, `ErrorMessages.kt`, `MiniXlsxReader.kt`, `MiniXlsxWriter.kt`, `ExcelHelper.kt`, `CsvImporter.kt`, `JsonUtil.kt`, `BackupManager.kt`, `IcsExporter.kt`, `PdfExporter.kt`, `NetworkMonitor.kt`, `EmulatorDetector.kt`, `CredentialGenerator.kt`, `Extensions.kt` (showSnackbar, collectFlow), `UiState.kt` (sealed class: Idle/Loading/Success/Error), `DropdownController.kt` (filter-sız ExposedDropdownMenu), `FileTypeDetector.kt`, `ImportPreviewDialog.kt`, `PendingDelete.kt` (UNDO snackbar), `NotificationPreferences.kt` |
| `MainActivity.kt` | Tek aktivite — Navigation Component start destination, bottom nav setup, force-logout, session health check |
| `App.kt` | Application sınıfı, tema/dil tercihlerini boot'ta uygula |

#### `app/src/main/res/`

| Klasör | İçerik |
|---|---|
| `drawable/` | Material Icons vector drawable'ları (`ic_home`, `ic_edit`, `ic_delete`, `ic_expand_more`, `ic_logout`, `ic_save`, vb.) + empty state ikonları (`ic_empty_lecturers`, `ic_empty_courses`, `ic_empty_classrooms`, `ic_empty_schedule`) + login arka plan gradient |
| `drawable-night/` | Karanlık tema için drawable override'ları |
| `layout/` | 24 layout XML — her fragment, item card, dialog için ayrı dosya |
| `layout-sw600dp/` | Tablet (≥600dp) için yeniden düzenlenmiş layout'lar (şu an `fragment_admin_home.xml` 2-sütun) |
| `menu/` | Bottom nav menü tanımları — `menu_admin_bottom_nav.xml` (5 sekme), `menu_lecturer_bottom_nav.xml` (3 sekme) |
| `mipmap-*` | Uygulama ikonu |
| `navigation/nav_graph.xml` | Tek navigation graph — tüm fragment'lar arası geçişler, action tanımları |
| `values/` | Türkçe string'ler (396), renkler, dimens, themes |
| `values-en/` | İngilizce string'ler (396 — tam parite, CI bunu kontrol ediyor) |
| `values-night/` | Karanlık tema renk + theme override'ları |
| `values-sw600dp/` | Tablet için boyut override'ları (padding, tipografi, max width) |
| `xml/` | Network security config, backup rules, file provider paths |

#### `app/src/test/`

Robolectric ile yazılmış 16 JVM birim testi:
- `ScheduleGeneratorTest.kt` — algoritma doğruluğu
- `CsvImporterTest.kt` — Excel parse senaryoları
- `MiniXlsxReaderTest.kt` / `MiniXlsxRoundTripTest.kt`
- `BackupManagerTest.kt`, `IcsExporterTest.kt`, `JsonUtilTest.kt`
- `ErrorMessagesTest.kt`, `CredentialGeneratorTest.kt`
- `ReminderSchedulerTest.kt`, `NotificationPreferencesTest.kt`
- `OnboardingActivityTest.kt`, `AppSmokeTest.kt`
- `FileTypeDetectorTest.kt`, `UiStateTest.kt`, `TimeUtilsTest.kt`

### `super-admin-paneli/` — Web Paneli (Node.js)

| Yol | İçerik |
|---|---|
| `server.js` | Express sunucusu (~1700 satır): Helmet + CORS + rate-limit + session token auth + tüm REST endpoint'ler (organizations, admins, lecturers, courses, classrooms, offerings, schedule, availability, error-logs, login-attempts, CTI özet/heatmap/timeseries/export.csv, healthz, dashboard view, audit log, super-admin metadata) + alerting watcher (Slack/Discord webhook) + retention cleanup (gece silme job'u) |
| `package.json` | `npm start` → `node server.js` · Bağımlılıklar: `@supabase/supabase-js`, `express`, `helmet`, `cors`, `multer`, `xlsx`, `dotenv` |
| `public/index.html` | Tek sayfa uygulama (~570 satır) — sidebar nav, login overlay, 8 sayfa, 3 modal (schedule add/edit, auto schedule, entry detail), dil + tema toggle, CDN scriptleri (Bootstrap 5.3.3, Bootstrap Icons 1.11.3, Chart.js 4.4.1) **SRI hash** ile |
| `public/css/style.css` | 700+ satır CSS variable tabanlı stil — light + dark tema tokenları, weekly grid + availability grid, skeleton shimmer, empty state, credential banner, badge sistemi |
| `public/js/app.js` | Ana panel mantığı (~900 satır) — sayfa router, login, org CRUD, admin CRUD, dashboard akordeon, atama gridi, müsaitlik gridi, CTI dashboard, GeoIP entegrasyonu, CSV indirme, hesap dondurma |
| `public/js/i18n.js` | TR/EN i18n motoru — `data-i18n` attribute scanner, runtime `setLang`, localStorage persist |
| `public/js/theme.js` | Light/Dark/System tema toggle — `prefers-color-scheme` algılama, erken init (flash önlenir) |
| `public/js/geo.js` | `ip-api.com` batch IP → ülke lookup, 7 gün localStorage cache, bayrak emojisi (ISO alpha-2) |
| `public/i18n/tr.json` + `en.json` | 200+ key tam parite (CI yakalar) |

### `supabase/` — Veritabanı

| Yol | İçerik |
|---|---|
| `schema.sql` | Tek-dosya kurulum (847 satır): tüm tablolar, indeksler, RLS politikaları, 5 SECURITY DEFINER helper fonksiyon (`current_org_id`, `current_user_role`, `is_admin`, `is_lecturer`, `current_lecturer_id`), `prevent_schedule_overlap` trigger, `admin_reset_lecturer_password` RPC, `org_dashboard` view, Realtime publication |
| `migrations/20260521000000_baseline.sql` | dbmate baseline migration (marker) — `schema.sql` kurulduğunda bu satır applied olarak işaretlenir |
| `migrations/.dbmate.example.env` | DATABASE_URL şablonu, dbmate konfigürasyonu |
| `migrations/legacy/` | Eski parçalı SQL'ler (arşiv — çalıştırmayın) |
| `README.md` | Kurulum + production deploy runbook (Quick install yolu + dbmate yolu) |

### `docs/`

| Yol | İçerik |
|---|---|
| `diagrams/er-diagram.md` | 6 Mermaid diyagramı (tam ER, multi-tenant izolasyon, race-condition trigger, login sequence, atama sequence, bileşen mimarisi) |
| `diagrams/er-source.mmd` | Saf Mermaid kaynağı (PNG export için) |
| `reports/phase2-report.docx` + `.md` | Phase 2 teknik raporu (1-2 sayfa) |
| `reports/proje-detayli-tanitim.docx` | Bu doküman |
| `screenshots/` | README için yer tutucu (ekran görüntüleri buraya konulur) |

### `.github/` — CI/CD

| Yol | İçerik |
|---|---|
| `workflows/android-build.yml` | Push/PR'da APK build + Robolectric test + lint, artifact 7 gün |
| `workflows/panel-check.yml` | Node syntax + i18n parite (TR vs EN key count eşit mi?) + npm audit |
| `workflows/release.yml` | `v*.*.*` tag push'unda otomatik signed release APK + GitHub Release |
| `dependabot.yml` | Haftalık Gradle + npm + Actions taraması, AndroidX/Kotlin/Supabase/Express grup PR'ları |
| `PULL_REQUEST_TEMPLATE.md` | PR şablonu (özet, tür, etkilenen alanlar, test çek-listesi) |
| `ISSUE_TEMPLATE/bug_report.md` + `feature_request.md` + `config.yml` | Issue şablonları |

### Kök dizin

| Dosya | İçerik |
|---|---|
| `README.md` | Ana proje tanıtımı (badge'ler, mimari, akış şemaları, kurulum) |
| `LICENSE` | Proprietary lisans |
| `build.gradle.kts` (kök) | Multi-project Gradle ayarı |
| `app/build.gradle.kts` | Android module build config (compileSdk 34, minSdk 26, kotlin 1.9, R8 minify + resource shrink) |
| `local.properties` | Supabase URL + anon_key + (opsiyonel) release keystore — **gitignored** |
| `local.properties.example` | Yeni geliştirici için şablon |
| `gradle/libs.versions.toml` | Bağımlılık sürüm kataloğu |
| `gradle.properties` | Gradle JVM opts (artırılmış heap) |
| `.gitignore` | local.properties, .env, node_modules, IDE, .jks, log dosyaları |

---

## 3. Mimariye Ait Kritik Kararlar

### Neden Supabase ve Firebase Değil?

Faz 1'de SQLite ile çalışmıştım. Faz 2'ye geçerken ilişkisel modeli korumak hem Faz 1'deki yatırımı boşa harcamamak hem de join-yoğun bir tabloyu (`schedule_entries`) NoSQL'de denormalize etmemek için önemliydi. Supabase aynı zamanda RLS sağlıyor — Faz 2'nin "lecturer must only see their own data — enforce at query level" maddesini sorgu seviyesinde tutmak için Postgres'in RLS politikaları gibi bir alternatif yok.

### Neden Çok-Kiracılı? (Ödev İstemiyordu)

Tasarım sırasında "ya başka bir üniversite de kullanmak isterse?" diye düşündüm. Tüm tablolara bir `org_id` kolonu eklemek küçük bir karar gibi görünüyor ama büyük etkisi var: aynı veritabanı içinde N tane kurum tamamen yalıtılmış çalışabiliyor. Maliyeti: süper-admin panelini ayrı bir uygulama olarak yazmam gerekti çünkü orgs üstü görünüm `service_role` gerektiriyor, bu da mobile APK'ya konulamaz.

### Neden MVVM + Repository?

Ödev §7.1 zaten istiyordu. Ama benim için iki ek avantajı vardı: ViewModel'leri Robolectric ile test edebiliyorum (16 birim test yazdım), ve Fragment'ları yenileme/recreate senaryolarında state korunuyor (`StateFlow<UiState<T>>` ile).

### Neden Apache POI Değil, Kendim Excel Parser Yazdım?

Excel `.xlsx` aslında ZIP içinde XML. POI Android'de iki sorun çıkardı: R8 / ServiceLoader çakışması (release build kırılıyor) ve APK boyutuna 8-12 MB ek. Kendim yazdığım `MiniXlsxReader.kt` (~200 satır) + `MiniXlsxWriter.kt` (~200 satır) sadece projeye özel kolonları okuyor / yazıyor, generic değil. APK 4.4 MB'da kaldı, release build'de hiç sorun çıkmadı.

### Neden İki Katmanlı Çakışma Kontrolü?

Uygulama-katmanı kontrolü her zaman yarış koşulu (TOCTOU) bırakır: iki admin aynı milisaniyede formu gönderirse her ikisinin SELECT sorgusu boş döner ve her ikisinin INSERT'i başarılı olur. `prevent_schedule_overlap` trigger transaction içinde son durumu okuyup ikinci insert'i reddediyor. Mobile bug bile olsa veritabanı çakışmayı kabul etmiyor.

### Neden ExposedDropdownMenu için NoFilterArrayAdapter?

`MaterialAutoCompleteTextView` standart `ArrayAdapter` ile kullanıldığında her dropdown açılışında mevcut text'i constraint olarak filter ediyor. "Year 1" set edildikten sonra dropdown sadece "Year 1" gösteriyordu — Year 2/3/4 görünmüyordu. `NoFilterArrayAdapter` constraint'i göz ardı eder, tüm item'ları döner. Spinner davranışına eşdeğer.

### Neden Atomic `saveSession()`?

`EncryptedSharedPreferences` 5 ayrı `apply()` çağrısı bazen biri fail olunca "userId yazıldı ama lecturerId yazılmadı" gibi yarı yazılı session bırakıyordu. Sonuç: bottom nav görünmüyor, PDF export "aktif oturum yok" diyor. `saveSession()` tek `edit()` çağrısında `commit()` ile synchronous yazıyor — atomic. Plus `isHealthy()` kontrolü açılışta yarı yazılı session'ı tespit edip otomatik logout yapıyor.

---

## 4. Sürüm Geçmişi (Son Düzeltmeler)

| Sürüm | Ne Değişti |
|---|---|
| v1.2.7 | Material 3 tutarlılığı, i18n+dark mode panel, GeoIP/time series/CSV/alerting CTI |
| v1.2.8 (uncommitted) | DropdownController filter-sız (Year/Term/Day dropdown'ları artık tam liste), AdminHome string resource, atomic saveSession, session isHealthy check |

---

## 5. Hızlı Komut Referansı

```bash
# Mobile build
./gradlew :app:assembleRelease

# Mobile test
./gradlew :app:testDebugUnitTest

# Panel start
cd super-admin-paneli && npm start

# Yeni migration
cd supabase && dbmate new add_column_x

# Sürüm yayınla (CI otomatik APK üretir)
git tag v1.2.8 && git push origin v1.2.8
```
