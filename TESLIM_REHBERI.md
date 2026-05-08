# UniScheduler — Teslim ve Kurulum Rehberi

Bu doküman sistemin **sıfırdan kurulumu**, **deployment'ı** ve **test edilmesi** için adım adım anlatımdır. Hiçbir adımı atlamayın; sıralama önemlidir.

---

## 1. Sistem Mimarisi (Hızlı Bakış)

```
┌──────────────────┐         ┌──────────────────┐
│  Mobil App       │         │  Web Admin Panel │
│  (Android APK)   │         │  (Node/Express)  │
│                  │         │                  │
│  Roller:         │         │  Süper admin →   │
│  • admin         │         │  org & admin     │
│  • lecturer      │         │  yönetimi        │
└────────┬─────────┘         └────────┬─────────┘
         │                            │
         │  ANON_KEY + JWT            │  SERVICE_ROLE_KEY
         │  (RLS enforced)            │  (RLS bypass)
         │                            │
         └────────────┬───────────────┘
                      │
                ┌─────▼──────┐
                │  Supabase  │
                │  (Postgres │
                │   + Auth + │
                │   Realtime)│
                └────────────┘
```

**Multi-tenant:** Her veri satırında `org_id` kolonu bulunur. RLS politikaları kullanıcının `org_id`'sine göre erişimi sınırlar. Web panel `service_role` key kullanır (tüm RLS'i bypass eder), mobil app `anon_key` kullanır (RLS enforce edilir).

---

## 2. Önkoşullar

### Yazılım

| Bileşen          | Versiyon    | Nereden                         |
|------------------|-------------|---------------------------------|
| Node.js          | ≥ 18        | https://nodejs.org             |
| JDK              | 17          | https://adoptium.net           |
| Android Studio   | ≥ Hedgehog  | https://developer.android.com/studio |
| Android SDK      | API 34      | Android Studio SDK Manager      |
| Git              | herhangi    | https://git-scm.com            |

### Servis Hesapları

- **Supabase** projesi — https://supabase.com (ücretsiz tier yeterli)

---

## 3. Supabase Kurulumu

### 3.1 Yeni proje oluştur

1. https://app.supabase.com → **New project**
2. Project name: `unischeduler` (veya istediğin)
3. Database password: güçlü bir şifre belirle ve **kaydet**
4. Region: kullanıcılarına en yakın bölge

### 3.2 Auth ayarları

Supabase Dashboard → **Authentication** → **Providers** → **Email**:

- ✅ **Enable Email provider**: AÇIK
- ❌ **Confirm email**: KAPALI (kritik — açıksa kullanıcı oluşturma akışı çalışmaz)
- ❌ **Secure email change**: KAPALI

Authentication → **Settings**:
- **Enable Signups**: AÇIK (web panel admin oluşturma için gerekli)

### 3.3 Şemayı çalıştır

Supabase Dashboard → **SQL Editor** → **New query** → `supabase/schema.sql`'in tamamını yapıştır → **Run**.

Bu **tek dosya** her şeyi kurar: tablolar, RLS, trigger'lar, helper fonksiyonlar, index'ler, multi-tenant izolasyon, panel hata log altyapısı. Migration dosyalarını (004, 005) **çalıştırmana gerek yok** — bunlar yalnızca eski kurulumları upgrade etmek içindir, yeni kurulumda hepsi schema.sql'e dahildir.

> ⚠️ **schema.sql tüm veriyi siler** (DROP IF EXISTS). Sadece ilk kurulumda veya tam reset gerektiğinde çalıştır.

### 3.4 İlk organizasyonu ve admin'i oluştur

İki yöntem var:

**A) Web panel üzerinden (önerilir)** — Adım 4'e geç, panel kurulduktan sonra UI'dan oluştur.

**B) SQL ile manuel** — SQL Editor'da:

```sql
-- 1. Org oluştur
INSERT INTO organizations (name, code) VALUES ('Bilgi Üniversitesi', 'BILGI') RETURNING id;
-- (dönen id'yi not et, örn. 1)

-- 2. Auth Admin API üzerinden ilk admin'i oluşturmak için Supabase Dashboard → Authentication → Users → Add user
-- Email: superadmin@unischeduler.app  (örnek)
-- Password: Geçici1234
-- "Auto Confirm User" → AÇIK

-- 3. public.users'a profil ekle (auth.users.id'yi yukarıdaki user'dan kopyala)
INSERT INTO users (id, org_id, username, role, must_change_password)
VALUES ('AUTH_USER_UUID_BURAYA', 1, 'superadmin', 'admin', true);
```

### 3.5 API anahtarlarını topla

Project Settings → **API**:

- `Project URL` → `SUPABASE_URL`
- `anon` (public) key → `SUPABASE_ANON_KEY` (mobil app için)
- `service_role` (secret) key → `SUPABASE_SERVICE_KEY` (web panel için, gizli tut!)

---

## 4. Web Admin Panel Kurulumu

### 4.1 Bağımlılıkları yükle

```bash
cd UniScheduler/super-admin-paneli
npm install
```

### 4.2 .env dosyasını oluştur

`super-admin-paneli/.env` (yoksa oluştur):

```env
SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
SUPABASE_SERVICE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

ADMIN_USERNAME=superadmin
ADMIN_PASSWORD=GüçlüBirŞifre!2026

PORT=3000
```

> ⚠️ `.env` git'e commit edilmemeli. `.gitignore`'da zaten var, ama production'da farklı bir secrets manager (Docker secrets, Heroku config vars vs.) kullanın.

### 4.3 Local'de test et

```bash
cd UniScheduler/super-admin-paneli
node server.js
```

Tarayıcıda `http://localhost:3000` aç. Süper admin login: `.env`'deki `ADMIN_USERNAME` / `ADMIN_PASSWORD`.

### 4.4 Panel üzerinden ilk kurulum

1. Login → **Organizations** → Yeni org ekle (örn. "Bilgi Üniversitesi", "BILGI")
2. **Admins** → Yeni admin ekle (kullanıcı adı, şifre seç + org seç)
3. Bu kullanıcı bilgilerini mobil app'e gireceksin.

### 4.5 Production deployment seçenekleri

| Platform | Adımlar |
|----------|---------|
| **Heroku** | `heroku create`, `heroku config:set SUPABASE_URL=... SUPABASE_SERVICE_KEY=... ADMIN_PASSWORD=...`, `git push heroku main` |
| **Railway / Render** | GitHub repo bağla, env değişkenleri ekle, otomatik deploy |
| **VPS (Ubuntu)** | `pm2 start server.js --name unischeduler-panel`, nginx reverse proxy + Let's Encrypt SSL |
| **Docker** | (Dockerfile yok ama kolayca eklenebilir; `node:20-alpine` base, `EXPOSE 3000`) |

---

## 5. Mobil App Build

### 5.1 local.properties oluştur

`UniScheduler/local.properties.example`'i kopyala → `local.properties`. Düzenle:

```properties
sdk.dir=C\:\\Users\\<KULLANICI>\\AppData\\Local\\Android\\Sdk

SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

> Release build için aynı dosyaya keystore bilgileri de eklenmeli (aşağıda 5.4).

### 5.2 Debug APK build

```bash
cd UniScheduler
./gradlew assembleDebug
```

Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

Bu APK'yı doğrudan kullanıcı telefonuna kurabilirsin (Settings → Security → Install unknown apps).

### 5.3 Release keystore oluştur (bir kez)

```bash
keytool -genkey -v \
  -keystore unischeduler-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias unischeduler
```

Sorulara cevap ver, ŞİFRELERİ NOT AL VE GÜVENLE SAKLA. Bu dosyayı kaybedersen aynı app'in güncellemesini yayınlayamazsın (kullanıcılar uninstall + reinstall yapmak zorunda kalır).

### 5.4 Release APK build

`local.properties`'e ekle:

```properties
KEYSTORE_FILE=C\:\\path\\to\\unischeduler-release.jks
KEYSTORE_PASSWORD=keystore-şifresi
KEY_ALIAS=unischeduler
KEY_PASSWORD=key-şifresi
```

```bash
cd UniScheduler
./gradlew assembleRelease
```

Çıktı: `app/build/outputs/apk/release/app-release.apk` (imzalı, minify+shrink yapılmış, ProGuard uygulanmış)

### 5.5 Play Store yayını (opsiyonel)

1. https://play.google.com/console → **Create app**
2. App bundle yükle: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
3. İçerik derecelendirmesi, gizlilik politikası, ekran görüntüleri vb. doldur.

---

## 6. Test Senaryoları (Kabul Testleri)

### 6.1 Multi-tenant izolasyon

1. Web panelde **Org A** oluştur, içine `adminA` kullanıcısı ekle.
2. Web panelde **Org B** oluştur, içine `adminB` kullanıcısı ekle.
3. Mobil APK'yı bir cihaza kur. `adminA` ile giriş yap. Birkaç ders/hoca ekle.
4. Çıkış yap, `adminB` ile giriş yap. **Org A'nın verisi GÖRÜNMEMELİ.**
5. ✅ PASS = Org B boş görünüyor. ❌ FAIL = Org A verileri sızıyor → migration 004 çalıştırılmamış olabilir.

### 6.2 Logout sonrası temizlik

1. `adminA` ile giriş yap, Data sekmesine git, hocaları gör.
2. Üst menüden **Çıkış Yap**.
3. `adminB` ile giriş yap.
4. Data sekmesine git → **`adminA`'nın hocaları görünmemeli, anlık veri Org B'ye ait olmalı.**

### 6.3 Büyük liste görüntüleme

1. Web panelden Org A'ya **50+ ders** import et (Excel).
2. Mobil app `adminA` ile giriş yap → Data → Dersler akordiyonunu aç.
3. ✅ PASS = Tüm dersler scroll ile görünüyor. ❌ FAIL = Sadece 2-3 ders görünüyor → adapter refactor uygulanmamış.

### 6.4 Excel import

1. `deneme import dosyaları/courses.xlsx` mobil app'e import et (Data → Dersler → Import).
2. Önizleme dialogunda satır sayısı doğru görünmeli.
3. Import → Listede yeni dersler görünmeli (UI otomatik yenilenir).

### 6.5 Realtime güncelleme

1. Mobil app açık, Lecturer → Calendar görünür durumda.
2. Web panelden o lecturer için yeni schedule entry ekle.
3. Mobil ekranda **birkaç saniye içinde** yeni entry görünmeli (Realtime channel).

### 6.6 Çevrimdışı davranış

1. Mobil cihazın internetini kapat.
2. App'te üstte **"İnternet bağlantısı yok"** banner görünmeli.
3. Bağlantıyı aç → banner kaybolmalı.

### 6.7 Uçtan uca kullanıcı yolculuğu

**Yeni hoca senaryosu:**
1. Süper admin web paneli → org seç → **Akademisyenler** → Yeni hoca ekle. Kullanıcı adı + geçici şifre üretilir, ekranda gösterilir, kopyalayıp hocaya verirsin.
2. Hoca mobil app'i kurar, kullanıcı adı + geçici şifre ile giriş yapar.
3. App **şifre değiştir** ekranına yönlendirir (must_change_password=true).
4. Yeni şifresini koyar → giriş tamamlanır → Lecturer Home görünür.

---

## 7. Yaygın Sorunlar ve Çözümler

| Belirti | Sebep | Çözüm |
|---------|-------|-------|
| Login'de "Kullanıcı adı veya şifre hatalı" ama doğru | Supabase Auth'da Confirm email AÇIK | Auth → Providers → Email → Confirm email = KAPALI |
| Web panelden hoca eklerken "User already registered" | Aynı kullanıcı adı başka org'da var | Farklı kullanıcı adı seç (sistem otomatik suffix ekler) |
| Mobil app açılışta crash | local.properties'de SUPABASE_URL boş | local.properties'i kontrol et, build temizle: `./gradlew clean assembleDebug` |
| Web panelde organizasyonlar listesi boş | Service key yanlış | .env'de SUPABASE_SERVICE_KEY'i kontrol et (anon değil, service_role olmalı) |
| Yeni admin oluşturulduğunda "stack depth exceeded" | Eski RLS recursion bug'ı | Migration 003 ve 004'ü çalıştır |
| "Bu işlem için yetkiniz yok" hatası | RLS policy admin rolünü tanımıyor | public.users'da role='admin' mi kontrol et |
| Realtime güncellemeler gelmiyor | Realtime publication tabloyu içermiyor | SQL: `ALTER PUBLICATION supabase_realtime ADD TABLE schedule_entries;` (zaten schema'da var) |
| Schedule eklerken "Schedule conflict for lecturer" | Aynı saat aralığında çakışma | Saati değiştir veya çakışan entry'yi sil |

---

## 8. Bakım ve İzleme

### 8.1 Log inceleme

- **Mobil app crash log'ları**: Supabase tablosu `client_error_logs` (admin'ler erişebilir; web panelde "Hata Logları" sekmesi var)
- **Web panel log'ları**: `pm2 logs unischeduler-panel` veya hosting platformunun log paneli
- **Supabase log'ları**: Dashboard → Logs → Postgres / Auth / API

### 8.2 Yedekleme

- Supabase free tier'da **otomatik günlük yedek** var (7 gün geriye)
- Pro tier: **PITR (point-in-time recovery)**
- Manuel yedek: `pg_dump` SQL Editor üzerinden Connection string ile

### 8.3 Versiyon yükseltme

- Mobil app `versionCode` ve `versionName` `app/build.gradle.kts` içinde. Yeni release için artır:
  ```kotlin
  versionCode   = 2  // her release'de +1
  versionName   = "1.1"
  ```

### 8.4 Yeni org ekleme (operasyonel)

1. Süper admin → Organizations → Yeni org
2. Admins → Yeni admin (org'a bağlı)
3. Yeni admin'e kullanıcı adı + geçici şifre paylaş
4. Bu admin login'den sonra Settings → Bölümleri ekler, sonra Data → Hoca/Ders ekler.

---

## 9. Güvenlik Kontrol Listesi

- [ ] `.env` git'e commit edilmedi
- [ ] `local.properties` git'e commit edilmedi
- [ ] Keystore dosyası **birden fazla yerde yedekli** (kaybedersen app güncelleme imkânsız)
- [ ] Web panel HTTPS arkasında (Heroku/Railway otomatik, VPS'de Let's Encrypt)
- [ ] `ADMIN_PASSWORD` 16+ karakter, default değil ve düzenli değiştiriliyor
- [ ] Web panel `NODE_ENV=production` ile çalışıyor (default şifre reddi devrede)
- [ ] Web panel için `ALLOWED_IPS` set edilmiş (ofis/VPN IP allowlist) — defense in depth
- [ ] Supabase Auth'da **rate limit** açık
- [ ] Supabase **MFA for admins** etkin (Pro tier)
- [ ] `schema.sql` (tek dosya) production DB'de çalıştırıldı; legacy migration'lar **çalıştırılmadı**
- [ ] Mobil APK'da `android:allowBackup="false"` (manifest'te zaten kuruldu)
- [ ] Mobil APK'da `network-security-config.xml` cleartext'i kapatıyor (zaten kuruldu)

---

## 10. Production Launch Checklist (FINAL — yayından önce)

Bu listenin **TÜM** maddeleri ✅ olmadan production'a gönderme.

### 10.1 Backend (Supabase)
- [ ] Production Supabase projesi ayrı (dev/staging karışmamalı)
- [ ] `schema.sql` tek seferlik çalıştırıldı
- [ ] En az bir organization oluşturuldu (`organizations` tablosu)
- [ ] En az bir admin oluşturuldu ve `must_change_password=true` flag'i set
- [ ] Auth → Confirm email **KAPALI**
- [ ] Auth → Enable Signups → **AÇIK** (panel admin oluşturma için)
- [ ] Anon key + Service role key güvenli yerde (1Password vb.)

### 10.2 Web Panel (super-admin)
- [ ] Ayrı sunucu/VPS'te (mobil APK ile aynı dağıtımda değil)
- [ ] HTTPS arkasında (sertifika geçerli)
- [ ] `NODE_ENV=production`
- [ ] `ADMIN_PASSWORD` güçlü (16+ karakter, default değil)
- [ ] `ALLOWED_IPS` veya en azından firewall ile erişim kısıtlı
- [ ] Service role key sadece bu sunucuda, başka hiçbir yerde değil
- [ ] `.env` git'e veya cloud bucket'a sızmadı

### 10.3 Mobil APK
- [ ] `local.properties`'te SUPABASE_URL/ANON_KEY production değerleri
- [ ] Production keystore oluşturuldu (`unischeduler-release.jks`)
- [ ] **Keystore dosyası iki ayrı yedek lokasyonda** (kaybedersen Play Store'da uygulamayı bir daha güncelleyemezsin)
- [ ] `KEYSTORE_PASSWORD`, `KEY_PASSWORD` password manager'da
- [ ] `versionCode` ve `versionName` doğru bir sonraki sürüme bumplandı
- [ ] `./gradlew assembleRelease` başarılı (R8 + ProGuard temiz)
- [ ] Release APK gerçek bir cihaza yüklenip 5 dakika tüm ekranlar test edildi
- [ ] Excel import release'de çalıştığı doğrulandı (ana release crash sebebiydi)

### 10.4 Hızlı Smoke Test (5 dakika)
1. Telefonda **production** APK'yı sıfırdan kur
2. Login → admin hesabı
3. Settings → Departman ekle → 5 saniye içinde listede görünmeli
4. Data → Hoca ekle → Credentials dialogu açılmalı
5. Excel ile 50 ders import → liste tam dolmalı
6. Auto Schedule → Generate → en az bir alternatif çıkmalı
7. Çıkış → tekrar giriş → veriler korunuyor olmalı
8. Pull-to-refresh → her sekmede düzgün çalışmalı

Hepsi ✅ ise yayına hazır.

---

## 10. Geliştirici İletişimi

Sorun çıkarsa:

1. Önce bu rehberin **Yaygın Sorunlar** tablosuna bak
2. Mobil app log'u Android Studio Logcat'ten oku (filter: `LecturerRepo|DataFragment|MainActivity`)
3. Supabase Logs → SQL hatası varsa policy ya da column eşleşmiyor olabilir
4. Web panel hatası → tarayıcı DevTools → Network sekmesi, başarısız request'in response'u bak

---

**Bu rehberi takip ederek sıfırdan kurulum yapabilen yeni bir geliştirici 1-2 saat içinde sistemi ayağa kaldırabilir.**
