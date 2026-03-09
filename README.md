# UniScheduler 📅

Üniversite ders programı hazırlama ve yönetim uygulaması.  
Android (Kotlin + Jetpack Compose) + Supabase (Auth, PostgreSQL, Storage).

## Özellikler

- **4 Rol Destekli RBAC**: Admin, Bölüm Başkanı, Öğretim Üyesi, Öğrenci
- **Otomatik Program Oluşturma**: CSP + Backtracking + MRV algoritması
- **Excel İçe/Dışa Aktarma**: Apache POI ile .xlsx desteği
- **Müsaitlik Yönetimi**: Öğretim üyeleri müsait saatlerini belirleyebilir
- **Taslak Onay Sistemi**: BB taslak oluşturur → Admin onaylar/reddeder
- **Değişiklik Talepleri**: Dual approval (BB + Admin) sistemi
- **Gerçek Zamanlı Güncelleme**: Supabase Realtime aboneliği

## Teknoloji Stack

| Katman | Teknoloji |
|--------|-----------|
| Android | Kotlin 2.0.21, Jetpack Compose (BOM 2024.11.00), Material 3 |
| Mimari | MVVM + Clean Architecture (3 katman) |
| DI | Hilt 2.53.1 + KSP |
| Navigation | Type-Safe Navigation Compose |
| Backend | Supabase (Auth + PostgREST + Storage + Realtime) |
| Excel | Apache POI 5.3.0 |
| Build | Gradle 8.9, AGP 8.7.3 |

## Kurulum

### 1. Supabase Projesi Oluşturma

1. [supabase.com](https://supabase.com) adresinden yeni bir proje oluşturun
2. Proje oluşturulduktan sonra **Settings → API** sayfasından şu bilgileri not edin:
   - **Project URL** (örn: `https://abcdefgh.supabase.co`)
   - **anon public key** (uzun JWT token)

### 2. SQL Migration'ları Çalıştırma

Supabase Dashboard → **SQL Editor** sayfasından aşağıdaki dosyaları **sırasıyla** çalıştırın:

```
supabase/migrations/001_create_tables.sql      → Tablolar ve indeksler
supabase/migrations/002_rls_policies.sql        → RLS güvenlik politikaları
supabase/migrations/003_functions.sql           → Trigger'lar ve fonksiyonlar
supabase/migrations/004_storage_buckets.sql     → Storage bucket'ları
```

### 3. İlk Admin Kullanıcısı

1. Supabase Dashboard → **Authentication → Users** → "Add user" ile bir kullanıcı oluşturun
2. SQL Editor'da admin yapın:
   ```sql
   SELECT public.make_admin('admin@ornek.edu.tr');
   ```

### 4. Android Projesi Yapılandırma

1. Projeyi Android Studio'da açın (Hedgehog veya üstü önerilir)
2. `local.properties.example` dosyasını `local.properties` olarak kopyalayın:
   ```
   copy local.properties.example local.properties
   ```
3. `local.properties` içine SDK yolunu ve Supabase bilgilerini girin:
   ```properties
   sdk.dir=C\:\\Users\\KULLANICI\\AppData\\Local\\Android\\Sdk
   SUPABASE_URL=https://YOUR-PROJECT-REF.supabase.co
   SUPABASE_ANON_KEY=YOUR-ANON-KEY-HERE
   ```

### 5. Build & Run

**Android Studio ile:**
- Projeyi açın → Sync Gradle → Run ▶

**Komut satırından Debug APK:**
```bash
cd UniScheduler
./gradlew assembleDebug
```
APK çıktısı: `app/build/outputs/apk/debug/app-debug.apk`

**Release APK (imzalı):**
```bash
./gradlew assembleRelease
```

## Proje Yapısı

```
UniScheduler/
├── app/src/main/java/com/unischeduler/
│   ├── UniSchedulerApp.kt              # Hilt Application
│   ├── MainActivity.kt                 # Single Activity
│   ├── di/                             # Hilt Modülleri
│   │   ├── SupabaseModule.kt
│   │   ├── AppModule.kt
│   │   └── RepositoryModule.kt
│   ├── domain/
│   │   ├── model/                      # Domain modelleri (15 dosya)
│   │   ├── repository/                 # Repository arayüzleri (6 dosya)
│   │   └── usecase/                    # Use case'ler (20 dosya)
│   │       ├── auth/
│   │       ├── import_data/
│   │       ├── export/
│   │       ├── schedule/
│   │       ├── availability/
│   │       ├── draft/
│   │       └── request/
│   ├── data/
│   │   ├── dto/                        # Supabase DTO'lar (8 dosya)
│   │   ├── mapper/                     # DTO ↔ Domain mapper'lar
│   │   └── repository/                 # Repository implementasyonları
│   ├── algorithm/                      # CSP Algoritması
│   │   ├── ConstraintChecker.kt
│   │   ├── SoftScorer.kt
│   │   ├── MRVHeuristic.kt
│   │   └── CSPSolver.kt
│   ├── presentation/
│   │   ├── navigation/                 # Screen, NavHost, BottomNavBar
│   │   ├── common/                     # UiState, LoadingIndicator, ErrorDialog
│   │   ├── splash/
│   │   ├── auth/
│   │   ├── home/
│   │   ├── calendar/
│   │   │   └── components/             # WeeklyGrid, TimeSlotCell, AvailabilityGrid
│   │   ├── data/
│   │   │   └── components/             # LecturerAccordion
│   │   ├── settings/
│   │   ├── drafts/
│   │   └── requests/
│   ├── util/                           # Yardımcı sınıflar
│   └── ui/theme/                       # Compose Theme
├── supabase/migrations/                # SQL dosyaları
├── build.gradle.kts                    # Root build
├── app/build.gradle.kts                # App build
├── gradle/libs.versions.toml           # Version catalog
└── local.properties.example            # Örnek konfigürasyon
```

## Roller ve Yetkiler

| Özellik | Admin | BB | Öğretim Üyesi | Öğrenci |
|---------|-------|----|----------------|---------|
| Program görüntüleme | ✅ | ✅ | ✅ | ✅ |
| Program oluşturma | ❌ | ✅ | ❌ | ❌ |
| Veri içe/dışa aktarma | ✅ | ✅ | ❌ | ❌ |
| Müsaitlik belirleme | ❌ | ❌ | ✅ | ❌ |
| Taslak onaylama | ✅ | ❌ | ❌ | ❌ |
| Talep oluşturma | ❌ | ❌ | ✅ | ❌ |
| Talep onaylama | ✅ | ✅ | ❌ | ❌ |
| Ayarlar yönetimi | ✅ | ❌ | ❌ | ❌ |

## Notlar

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35
- Excel dosyaları cihazın `Android/data/com.unischeduler/files/exports/` klasörüne kaydedilir
- İlk çalıştırmada internet bağlantısı gereklidir (Supabase bağlantısı)
- Supabase RLS politikaları tüm veri erişimlerini rol bazlı filtreler
