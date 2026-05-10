# UniScheduler — Çok Kiracılı Üniversite Ders Programı Yönetim Sistemi

> **Sürüm:** v1.2.6
> **Bileşenler:** Android mobil uygulama · Süper-admin web paneli · Supabase (PostgreSQL + Auth + Realtime)

UniScheduler, üniversitelerin ders programlarını planlamak, yayımlamak ve hocaların müsaitliğini takip etmek için tasarlanmış uçtan-uca bir sistemdir. **Çok kiracılı (multi-tenant)** mimarisiyle aynı veritabanında birden fazla kurumun verilerini birbirinden tamamen yalıtılmış olarak barındırır.

---

## İçindekiler

1. [Sistem Mimarisi](#1-sistem-mimarisi)
2. [Roller ve Sorumluluklar](#2-roller-ve-sorumluluklar)
3. [Süper-Admin Web Paneli](#3-süper-admin-web-paneli)
4. [Mobil Uygulama — Admin Rolü](#4-mobil-uygulama--admin-rolü)
5. [Mobil Uygulama — Hoca Rolü](#5-mobil-uygulama--hoca-rolü)
6. [Veri Alışverişi (Excel · PDF · iCal · JSON)](#6-veri-alışverişi-excel--pdf--ical--json)
7. [Kurulum](#7-kurulum)
8. [Güvenlik](#8-güvenlik)
9. [Sıkça Karşılaşılan Sorunlar](#9-sıkça-karşılaşılan-sorunlar)

---

## 1. Sistem Mimarisi

```
┌─────────────────────┐         ┌─────────────────────┐
│  Mobil App          │         │  Süper-Admin Panel  │
│  (Android — Kotlin) │         │  (Node.js / Express)│
│                     │         │                     │
│  Roller:            │         │  Sistem sahibinin   │
│  • admin            │         │  kullanır           │
│  • lecturer (hoca)  │         │                     │
└──────────┬──────────┘         └──────────┬──────────┘
           │                               │
           │ JWT + ANON_KEY                │ SERVICE_ROLE_KEY
           │ (RLS uygulanır)               │ (RLS bypass)
           │                               │
           └───────────────┬───────────────┘
                           │
                  ┌────────▼────────┐
                  │   Supabase      │
                  │ • PostgreSQL    │
                  │ • Auth (GoTrue) │
                  │ • Realtime      │
                  └─────────────────┘
```

**Çok kiracılı yalıtım:** Her veri satırında `org_id` kolonu vardır. PostgreSQL Row-Level Security (RLS) politikaları kullanıcının `org_id`'sine göre erişimi sınırlar. Mobil uygulama `anon_key` ile çalışır (RLS uygulanır), web paneli `service_role` key ile çalışır (RLS atlanır — yalnızca süper-admin için).

**Teknoloji yığını:**
- Android: Kotlin · MVVM · Coroutines · ViewBinding · Material Components 3
- Backend: Supabase (PostgreSQL 15 · GoTrue Auth · Realtime · Storage)
- Web Panel: Node.js + Express · Helmet + CORS + Rate Limiter · vanilla JS
- Şema: Tek dosyalık `supabase/schema.sql` (tablo · RLS · trigger · RPC)

---

## 2. Roller ve Sorumluluklar

| Rol            | Nereye Girer                  | Ne Yapabilir |
|----------------|-------------------------------|--------------|
| **Süper-Admin** | Web paneli                    | Tüm organizasyonları yönetir; admin hesabı oluşturur, şifre sıfırlar; tüm sistemin hata loglarını ve giriş denemelerini izler |
| **Admin**      | Mobil app (alt sekmeler)      | Kendi kurumunun bölüm/ders/hoca/derslik kayıtlarını yönetir; ders atar; otomatik program üretir; PDF/Excel/JSON çıkartır |
| **Hoca**       | Mobil app (alt sekmeler)      | Kendi haftalık programını görür; meşgul saatlerini işaretler; PDF / iCal olarak indirir; takvim uygulamasına aktarır |

---

## 3. Süper-Admin Web Paneli

> Sistem sahibinin kullandığı yönetim arayüzü. Her kurumu (organizasyon) eklemek, ona admin atamak, sistemin sağlık durumunu izlemek için.

### 3.1 Giriş

Tarayıcıdan `http://<panel-url>` (varsayılan: `http://localhost:3000`).

`.env`'de tanımlı `ADMIN_USERNAME` / `ADMIN_PASSWORD` ile giriş yapılır. Üretim ortamında **16+ karakter güçlü şifre** zorunludur (zayıf default'lar reddedilir, sunucu açılmaz).

📷 *`docs/screenshots/panel-login.png`*

### 3.2 Organizasyon Yönetimi

- Yeni organizasyon ekle (`name`, `code`)
- Kod **2-20 karakter** alfanumerik + `_`/`-`; otomatik büyük harfe çevrilir, Türkçe karakterler ASCII'ye dönüştürülür (örn. "İTÜ" → "ITU")
- Liste, arama ve silme

📷 *`docs/screenshots/panel-orgs.png`*

### 3.3 Admin Yönetimi

Her organizasyona admin (mobil app yöneticisi) eklenir:
- Otomatik kullanıcı adı (örn. `cem.acar`) ve **6 karakterlik geçici şifre** (A-Z + 1-6) üretilir
- Şifre tek seferlik gösterilir, kopyalanabilir
- Admin ilk girişte zorunlu olarak şifresini değiştirir (`must_change_password` flag)
- Toplu şifre sıfırlama: tek tıkla bir org'taki tüm admin'lerin parolasını sıfırla

📷 *`docs/screenshots/panel-admins.png`*

### 3.4 İzleme — CTI / Login Attempts

Her giriş denemesi (başarılı + başarısız) cihaz, IP, user-agent, hata aşaması bilgisiyle kaydedilir:
- Risk skoru — eşik aşıldığında satır kırmızı görünür (varsayılan: 10 başarısız deneme)
- Filtreleme: org, kullanıcı, tarih, başarı/başarısız
- Sadece **analiz**: aktif IP/hesap bloklama yok (kullanıcı talebi)

📷 *`docs/screenshots/panel-cti.png`*

### 3.5 İzleme — Hata Logları

Mobil uygulamadan gelen client-side hatalar gerçek zamanlı görünür:
- Cihaz modeli, Android sürümü, app sürümü
- Ekran adı, action, mesaj, **tam stack trace**
- Native crash'ler dahil (uygulama kapanmadan önce diske yazılır, sonraki açılışta DB'ye gönderilir)

📷 *`docs/screenshots/panel-error-logs.png`*

### 3.6 Veri Görüntüleme

Org bazında okunan veriler:
- Hocalar (durumu: `Geçici` = ilk girişini henüz yapmamış / `Aktif`)
- Dersler · Derslikler · Bölümler
- Haftalık programlar (admin'in onayladığı atamalar)
- Hocaların müsaitlik girdileri

---

## 4. Mobil Uygulama — Admin Rolü

Admin alt-bardaki 5 sekmede çalışır: **Home · Calendar · Assign · Data · Settings**.

📷 *`docs/screenshots/mobile-login.png`* — Login ekranı

### 4.1 Home Sekmesi

- Kurum istatistikleri (hoca/ders/derslik/atama sayıları)
- Hızlı erişim kartları
- Çıkış butonu

### 4.2 Calendar Sekmesi (Haftalık Genel Program)

Tüm kurum programını tek bakışta gör:
- **Filtreleme** (kombinlenebilir): Bölüm + Sınıf yılı + Hoca + Derslik
- Aktif filtreler chip olarak görünür ("Bilgisayar / 2. Sınıf / Hoca: Dr. X")
- ✕ ile filtreyi tek tek kaldır
- "Tümü" → tüm filtreleri sıfırla

Her kart bir derse karşılık gelir; rengi ders kimliğinin hash'inden üretilir (aynı ders her yerde aynı renk).

📷 *`docs/screenshots/mobile-admin-calendar.png`*

**PDF çıkartma:** Aktif filtrelere göre dosya adı otomatik şekillenir:
- Filtre yok → `program-tumu-2026-05-10.pdf`
- Bölüm + sınıf → `program-bilgisayar-2sinif-2026-05-10.pdf`
- Hoca + derslik → `program-dr-emre-aydin-l202-2026-05-10.pdf`

PDF başlığı aktif filtreyi gösterir; içerik filtre uygulanmış halidir.

### 4.3 Assign Sekmesi

Manuel ders atama formu:
- **Açılan Ders** (offering) / **Hoca** / **Derslik** seçimi (aranabilir dropdown)
- **Gün** + **Başlangıç saati** + **Bitiş saati** (saat picker)
- "ATA" → çakışma kontrolü (hoca veya derslik aynı saatte başka bir derse atandı mı?)
- Çakışma çıkarsa diyalog: çakışan dersleri (ders kodu, hoca, oda, saat) listeler
  → "Yine de Ata" (force) veya "İptal"
- Müsaitlik kontrolü: hoca o saati "müsait değilim" diye işaretlemişse uyarı

**"Mevcut Atamalar"** kart listesi:
- Tüm atamaları kompakt gösterir
- **Uzun bas → düzenle modu** açılır:
  - Form üstünde turuncu banner ("BIL102 düzenleniyor — formu güncelleyip Güncelle'ye basın")
  - "ATA" butonu "GÜNCELLE" olur
  - Çakışma kontrolü düzenlenen entry'yi hariç tutar
  - "İptal" butonu edit modundan çıkar
- "SİL" → snackbar UNDO ile 5 sn geri alma süresi

📷 *`docs/screenshots/mobile-admin-assign.png`*

**Otomatik Programlama:** Sağ üstteki "Otomatik" butonu açılan dersleri, müsaitlik ve çakışma kurallarına göre sıralı olarak yerleştirir. Çakışma çözücü, gün dengeleme parametresi seçilebilir.

### 4.4 Data Sekmesi (Veri Yönetimi)

Akordeonlar:
1. **Hocalar** — listele/ekle/düzenle/sil
2. **Dersler** — listele/ekle/düzenle/sil
3. **Ders Açma (Offering)** — bir dersin belli yarıyıl/sınıf/şube için açılması

#### Hoca Kartı

Her kart üstünde durum rozeti:
- **Geçici** — şifresini henüz değiştirmemiş (turuncu)
- **Aktif** — kendi şifresiyle giriş yapmış (yeşil)

Kartın sağındaki **⋮** veya kart üzerinde uzun basma → menü:
1. **Program ve Müsaitlik** → BottomSheet'te haftalık takvim açılır
   - Atanmış dersler renkli kartlar
   - Hocanın "müsait değilim" işaretlediği saatler **gri** "Müsait Değil" blokları
   - İkisi tek bakışta görünür → admin "şu saat boş mu?" sorusunu hızla yanıtlar
2. **Düzenle** → unvan, ad, soyad, bölüm, e-posta
3. **Şifre Sıfırla** → yeni 6 karakterlik şifre üretilir, ekranda gösterilir, kopyalanabilir
4. **Sil** → onay + UNDO snackbar

📷 *`docs/screenshots/mobile-admin-data.png`*

#### Hocalar Bölümünde Excel Aktarımı

- **İçe Aktar** → `.xlsx` dosyası seç, önizleme diyalogu açılır:
  - Her satır check'lenebilir
  - Hatalı satırlar (eksik kolon, geçersiz veri) kırmızı işaretli
  - "İçe Aktar" → seçili satırlar veritabanına yazılır, üretilmiş kullanıcı adı + şifreler tek dosya olarak indirilir
- **Dışa Aktar** → mevcut hoca listesi `.xlsx` olarak indirilir (paylaşıma uygun)

> **Not:** Excel parser'ı projede yazılmıştır (Apache POI **kullanılmıyor**). MiniXlsxReader/Writer ~400 satır self-contained kod; Apache POI'nin Android'de neden olduğu R8/ServiceLoader sorunlarını sıfırlar.

#### Dersler ve Derslikler

Ayrı akordeon ve sayfada aynı pattern: ekle/düzenle/sil + Excel içe-dışa aktar + arama + UNDO.

### 4.5 Settings Sekmesi (Yönetim)

- **Bölüm Yönetimi** — kurumdaki bölümlerin listesi
- **Org Ayarları** — gün başlangıç/bitiş saati (örn. 08:00 — 18:00), aktif günler (Pzt-Cum vs Pzt-Cmt), zaman adımı (15/30/60 dk)
- **Veri Yedekle** — tüm org datasını tek `.json` dosyasına aktarır
- **Geri Yükle** — `.json` yedek dosyasından `lookup data`'yı geri yükler:
  - Bölümler · derslikler · dersler · hocalar
  - Açılan dersler · atamalar · müsaitlik · org ayarları
  - Önizleme diyalogu özet gösterir → onay → uygulanır
- **Dil seçici** — Türkçe / İngilizce / Sistem
- **Tema seçici** — Açık / Karanlık / Sistem
- **Bildirim Tercihleri** — ders hatırlatıcı (lecturer için, admin'de gizli)
- **Çıkış**

📷 *`docs/screenshots/mobile-admin-settings.png`*

---

## 5. Mobil Uygulama — Hoca Rolü

Hoca alt-bardaki 3 sekmede çalışır: **Home · Availability · Schedule**.

### 5.1 Home Sekmesi

- Hoş geldin mesajı (unvan + ad)
- Bölüm bilgisi
- Bu hafta atanmış ders sayısı (büyük rakam)
- **Programı PDF olarak indir** → A4 yatay PDF çıktısı
- **Programı iCal olarak indir** → `.ics` dosyası → telefonunun takvim uygulamasında 14 hafta tekrarlı olay olarak görünür
- Bildirim kartı (master switch + zamanlama + sessiz saatler)
- Dil + tema + Çıkış

📷 *`docs/screenshots/mobile-lecturer-home.png`*

### 5.2 Availability Sekmesi (Müsaitlik)

Hocanın "ben şu saatte müsait DEĞİLİM" diye işaretlediği bloklar:
- Pazartesi-Cuma sütunları, saat dilimleri ızgarası
- Boş = müsait (varsayılan)
- Mavi blok = işaretli (meşgul)
- Tıkla → blok ekle/kaldır
- Saat aralığı belirle (10:00–12:00 gibi)

Atama ekranında admin bir saat seçtiğinde, bu blok varsa "öğretim üyesi bu saati müsait olarak işaretlememiş" uyarısı çıkar.

📷 *`docs/screenshots/mobile-lecturer-avail.png`*

### 5.3 Schedule Sekmesi

Hocanın **kendi** haftalık programı (admin'in atadığı dersler):
- Aynı `WeeklyScheduleView` (ortak component) — Pzt-Cum × saat ızgarası
- Her ders kartı: kod, saat, sınıf
- Pull-to-refresh ile yenile
- Kart üzerine basınca detay diyalogu (ders adı, hoca, sınıf, saat, bölüm)
- "Şimdi" çizgisi (geçerli güne ve saate gri yatay çizgi)

📷 *`docs/screenshots/mobile-lecturer-sched.png`*

### 5.4 Otomatik Bildirimler (Reminder)

Hocanın haftalık programı her gece 23:00'te taranır; ertesi günkü dersler için **AlarmManager** üzerinden tek-seferlik bildirimler kurulur:
- Ders başlamadan **15 / 30 / 60 / 120 dakika önce** (kullanıcı seçer)
- Sessiz saatler aralığında bildirim suskun (Doze whitelist'i kullanır)
- Çıkış yapıldığında tüm pending bildirimler iptal edilir → başka kullanıcıya sızmaz

---

## 6. Veri Alışverişi (Excel · PDF · iCal · JSON)

### 6.1 Excel İçe / Dışa Aktarma

| Yer | Tip | Beklenen Kolonlar |
|-----|-----|-------------------|
| Data → Hocalar | `.xlsx` | unvan, ad, soyad, bölüm, e-posta (opsiyonel) |
| Data → Dersler | `.xlsx` | kod, ad, teori, lab, kredi, bölüm |
| Data → Derslikler | `.xlsx` | kod, kapasite, tip, bölüm (opsiyonel) |

Örnek dosyalar APK içinde `assets/samples/` altında bundle'lı (panel: `Ornek_Exceller/`).

**İçe aktarmada:**
- Kolon eşleştirme **kafiye-tolerant** (Türkçe karakterler ve büyük/küçük harf normalize edilir)
- Önizleme diyalogu: hatalı satırlar kırmızı, geçerli satırlar checkbox ile seçilebilir
- Hocalar için: yeni hesaplar oluşturulur, üretilen şifreler tek `.txt` dosyası olarak indirilir (paylaşım için)

**Dışa aktarmada:**
- Mevcut liste seçili kolonlarla `.xlsx` olarak yazılır
- Bold başlık satırı + paylaşıma açık format

### 6.2 PDF Çıktısı

Admin Calendar'dan veya Lecturer Home'dan tek-tıkla A4 yatay PDF üretilir:
- Üstte filtreye göre başlık ("Bilgisayar / 2. Sınıf / Hoca: Dr. X")
- Pzt-Cum × saat ızgarası
- Renkli ders kartları
- Tarih + sayfa altbilgisi
- Filtreye göre **otomatik dosya adı** (örn. `program-bilgisayar-2sinif-2026-05-10.pdf`)
- Üretildikten sonra **paylaş** butonu (e-posta, WhatsApp, Drive vs.)

📷 *`docs/screenshots/pdf-output.png`*

### 6.3 iCal (.ics) Dışa Aktarma

Hoca Home → "Programımı iCal olarak indir":
- RFC 5545 uyumlu `.ics`
- Floating local time (zaman dilimi belirsizliği önlenir)
- Her ders 14 hafta haftalık tekrar (`RRULE:FREQ=WEEKLY;COUNT=14`)
- Telefonun varsayılan takvim uygulaması bunu açar → "Takvime ekle" → bütün dersler bir kerede eklenir

### 6.4 JSON Yedekleme / Geri Yükleme

Settings → "Veri Yedekle" → tüm org datası tek `.json` dosyası:
- Bölümler · dersler · derslikler · hocalar · açılan dersler · atamalar · müsaitlik · org ayarları
- App sürümü + tarih damgası
- Format human-readable, gerekirse manuel düzenlenebilir

Settings → "Geri Yükle" → bu `.json`'u seç:
- Önizleme diyalogu kayıt sayılarını gösterir
- Onay → veriler veritabanına yazılır
- **Önemli sınır:** Hocaların auth (giriş) hesapları yeniden oluşturulmaz — yedeklemeden önce var olan hocalar varsa restore yalnızca eksik lookup verisini doldurur. `user_id`'ler panel/auth tarafında tek kaynaktır.

---

## 7. Kurulum

### 7.1 Önkoşullar

| Bileşen | Sürüm |
|---------|-------|
| Node.js | ≥ 18 |
| JDK | 17 |
| Android Studio | ≥ Hedgehog |
| Android SDK | API 34 |
| Supabase hesabı | ücretsiz tier yeterli |

### 7.2 Supabase

1. https://app.supabase.com → **New project**
2. Authentication → Providers → Email:
   - ✅ Email provider AÇIK
   - ❌ Confirm email **KAPALI** (kritik — açıksa kullanıcı oluşturma akışı çalışmaz)
   - ❌ Secure email change KAPALI
3. Authentication → Settings → ✅ Enable Signups AÇIK
4. SQL Editor → `supabase/schema.sql` dosyasının tamamını yapıştır → **Run**
   > Bu tek dosya tüm tabloları, RLS politikalarını, trigger'ları, RPC'leri kurar.
   > İlk kurulum veya tam reset dışında çalıştırma — tüm veriyi siler (`DROP IF EXISTS`).
5. Project Settings → API → şu üç değeri kaydet:
   - `Project URL`
   - `anon` public key (mobil app için)
   - `service_role` secret key (web paneli için, **asla mobil app'e koyma**)

### 7.3 Süper-Admin Web Paneli

```bash
cd super-admin-paneli
cp .env.example .env
# .env içinde SUPABASE_URL, SUPABASE_SERVICE_KEY, ADMIN_PASSWORD doldur
npm install
npm start
```

Tarayıcıdan `http://localhost:3000` aç, `.env`'deki kullanıcı adı/şifreyle giriş yap.

İlk organizasyonu paneldeki "Organizasyonlar" sayfasından oluştur, ardından "Adminler" sayfasından bu org'a admin ekle. Üretilen kullanıcı adı/şifreyi bir yere not al — admin'e elden ileteceksin.

### 7.4 Mobil Uygulama

```bash
cd UniScheduler
cp local.properties.example local.properties
# local.properties içinde SUPABASE_URL ve SUPABASE_ANON_KEY doldur
./gradlew assembleDebug
```

Çıkan APK: `app/build/outputs/apk/debug/app-debug.apk`. Android telefona kopyalayıp kur.

#### Production / Release Build

Release imzalı APK için (test ve dağıtım amaçlı):

```bash
keytool -genkey -v -keystore unischeduler-release.jks \
        -keyalg RSA -keysize 2048 -validity 10000 -alias unischeduler
```

`local.properties`'e ekle:
```
KEYSTORE_FILE=C:\\path\\to\\unischeduler-release.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=unischeduler
KEY_PASSWORD=...
```

Sonra:
```bash
./gradlew assembleRelease
```

> Keystore dosyasını **kaybetme**. İki ayrı yedek tut. Kaybedersen aynı uygulamayı bir daha güncelleyemezsin.

### 7.5 İlk Kurulum Sonrası Doğrulama

1. Web panel: `http://localhost:3000` → giriş → org listesi geldi
2. Yeni org oluştur → admin ekle → kullanıcı adı + şifre kopyala
3. Mobil APK kur → bu admin'le giriş → Settings → Bölüm ekle → 5 saniye içinde liste güncellendi
4. Data → Hoca ekle → kullanıcı adı + şifre dialogu açıldı → kopyala
5. Üretilen şifreyle hocayı login et → Home'da hoş geldin mesajı + ders sayısı görünür

---

## 8. Güvenlik

| Katman | Önlem |
|--------|-------|
| Auth | Supabase GoTrue (JWT) · Otomatik kullanıcı adı + güçlü 6 karakterlik şifre · `must_change_password` ilk girişte zorunlu değişim |
| RLS | Her tabloda `org_id = current_org_id()` policy · admin/lecturer ayrımı `is_admin()` / `is_lecturer()` SECURITY DEFINER fonksiyonlarıyla |
| Şifre sıfırlama | `admin_reset_lecturer_password` SECURITY DEFINER RPC — admin'in kendi org'undaki hocaya pgcrypto bcrypt hash ile yeni şifre yazar; mobil app'e service_role gerekmez |
| Web paneli | Helmet + CORS + 100 req/dk rate limiter · Default şifre / kısa şifre prod'da reddedilir · `service_role` sadece sunucuda |
| Mobil app | `local.properties` git'e gitmez · `EncryptedSharedPreferences` (AES256-GCM) ile session JWT · `allowBackup=false` · `usesCleartextTraffic=false` · network security config |
| Audit | Her INSERT/UPDATE/DELETE `audit_log` tablosuna trigger ile yazılır (kim, ne zaman, eski → yeni JSON) |
| CTI | Her giriş denemesi (başarılı + başarısız) `login_attempts` tablosuna cihaz/IP/UA/aşama bilgisiyle yazılır — risk skorlaması panelde |
| Crash raporlama | Yakalanmamış UI thread crash'leri `cacheDir/crashes/` altına diske yazılır → sonraki login'de DB'ye gönderilir → panelde stack trace görünür |
| Dual-write koruma | `prevent_schedule_overlap` trigger'ı: iki admin aynı milisaniyede atama yaparsa transaction içinde ikisinden biri reddedilir |

---

## 9. Sıkça Karşılaşılan Sorunlar

### "Hocaya ait kayıt bulunamadı" — login sonrası hata

Lecturer profilinin DB'de eksik veya tutarsız olduğunu gösterir. Supabase SQL Editor'da:

```sql
SELECT u.id, u.username, u.role, u.is_active, u.deleted_at,
       l.id AS lecturer_id, l.deleted_at AS lec_deleted
FROM public.users u
LEFT JOIN public.lecturers l ON l.user_id = u.id
WHERE u.username = '<KULLANICI_ADI>';
```

- `lecturer_id` NULL → admin paneli üzerinden hocayı yeniden ekle
- `lec_deleted` doldu → `UPDATE public.lecturers SET deleted_at=NULL WHERE id=...`
- `is_active=FALSE` veya `u.deleted_at` doldu → kullanıcı pasif, panel üzerinden tekrar aktif et

### "User already registered" — hoca eklerken

Önceki bir kurulumdan `auth.users` tablosunda aynı email kalmış olabilir. `schema.sql`'in en üstündeki cleanup bloğu bu tip orphan'ları siler — ama şema **çalıştırıldığında veriyi siler**, dikkat.

### Excel import "tüm satırlar hatalı" gösteriyor

Kolon başlıklarının doğru olduğundan emin ol. Türkçe karakter farkı önemsizdir ("Ünvan" / "Unvan" / "title" hepsi çalışır). Beklenmedik bir karakter (örn. unicode tire `–` yerine ASCII `-`) varsa hata verebilir.

### Android 16 (SDK 36) — eski APK'da TextView NPE

v1.2.0 öncesi sürümlerde `<TextView>` ve `<MaterialSwitch>` widget'ları `android:text` / `android:textOn` / `android:textOff` olmadığında Android 16 `StaticLayout` constructor'da null pointer atıyordu. v1.2.6 bu davranışı tüm text-bearing widget'larda kapsayan default değerlerle çözdü.

### Realtime güncelleme gelmiyor

Mobil app şu sürümde `Supabase Realtime` subscriber kullanmıyor — pull-to-refresh + `onResume` reload pattern'i tercih edildi. Tüm liste ekranlarında pull-to-refresh ile güncelleme gelir.

### Süper-admin panel sunucu açılmıyor: "ADMIN_PASSWORD too short"

`.env` içinde `ADMIN_PASSWORD` 12 karakterden kısa veya tipik bir default ("admin", "password", "SuperAdmin123!") olduğu için reddedildi. **16+ karakter random** koy.

---

## Lisans ve Telif

Bu proje akademik amaçla geliştirilmiş bir bitirme/dönem ödevidir. Üçüncü taraf lisanslar:
- Supabase: MIT
- Material Components: Apache 2.0
- AndroidX: Apache 2.0
- Kotlin: Apache 2.0

---

## Hızlı Referans

```
UniScheduler/
├── README.md                     ← bu dosya
├── supabase/schema.sql           ← tek-dosya DB kurulumu
├── super-admin-paneli/           ← Node.js web panel
│   ├── README.md                 ← panel özelleştirme detayı
│   ├── server.js
│   └── public/
├── app/                          ← Android mobil app
│   ├── src/main/
│   │   ├── java/com/unischeduler/
│   │   ├── res/layout/
│   │   └── res/values/strings.xml
│   └── build.gradle.kts
├── local.properties.example      ← mobil app config şablonu
└── docs/screenshots/             ← README görselleri için
```

Soru / hata / öneri için: kaynak kod yorumları + bu README + `docs/` klasörü hep birlikte tutarlı tutuluyor. Yeni ekran ekleyince ekran görselini `docs/screenshots/`'a koyup README'de referans ver.
