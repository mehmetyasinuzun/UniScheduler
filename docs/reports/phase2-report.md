---
title: "UniScheduler — Phase 2 Teknik Raporu"
subtitle: "Mobile Programming · Phase 2 · Course Project"
date: "Mayıs 2026"
fontsize: 11pt
geometry: margin=2.2cm
---

# UniScheduler — Phase 2 Teknik Raporu

## Proje Özeti

Faz 1'de tek cihazda çalışan, SQLite tabanlı bir ders programı uygulaması yazmıştım. Faz 2'nin temel amacı bu uygulamayı çoklu cihazdan erişilebilir, rol bazlı kimlik doğrulamalı ve gerçek bir backend ile çalışan bir sisteme dönüştürmekti. Sonuçta ortaya çıkan UniScheduler şu üç parçadan oluşuyor:

- **Android mobil uygulama** (Kotlin, MVVM mimarisi): admin ve hoca olmak üzere iki rol, her biri kendi alt-bar navigasyonuyla.
- **Süper-admin web paneli** (Node.js + Express + Bootstrap 5): birden fazla kurumu (organizasyon) yönetmek için. Faz 2 doğrudan istemiyordu ama mimariyi çok-kiracılı kurguladığım için ek bir yönetim katmanı gerekti.
- **Supabase backend** (PostgreSQL 15 + GoTrue Auth + Realtime): tüm veriyi tutan ve RLS politikaları ile multi-tenant izolasyonu sağlayan katman.

Faz 1'den hiçbir ekranı kaldırmadım. Mevcut akışlar (kayıt görüntüleme, Excel içe aktarma, ayarlar) korundu; üzerine login ekranı, şifre değiştirme akışı, hoca takvimi, derslik yönetimi ve atama ekranı geldi.

## Veritabanı Teknoloji Seçimi

Ödevde üç seçenek vardı: Firebase, hosted PostgreSQL ve LAN üstünden SQLite. **Supabase (Option B)** ile devam etmeye karar verdim. Karar süreci özetle şöyle ilerledi:

| Karşılaştırma | Firebase | Supabase | LAN SQLite |
|---|---|---|---|
| Çoklu cihaz | ✓ | ✓ | Kısıtlı (aynı ağ) |
| İlişkisel sorgu | NoSQL — manuel join | SQL doğrudan | SQL |
| Row-Level Security | Security Rules JSON | PostgreSQL RLS (SQL) | Kendin yazarsın |
| Auth provider | Firebase Auth | GoTrue (e-posta + şifre) | Kendin yazarsın |
| Ücretsiz katman | Yeterli | Yeterli (500 MB) | Sunucu maliyeti |
| Vendor lock-in | Yüksek | Düşük (PostgreSQL standart) | Yok |

Faz 1'de zaten SQLite ile çalışmıştım; ilişkisel modeli devam ettirmek hem öğrendiklerimi atmamak hem de daha az dönüşüm kodu yazmak için iyiydi. Firebase'in NoSQL yapısında schedule_entries gibi join-yoğun bir tabloyu sorgulamak ekstra denormalization gerektiriyordu — bu da Faz 1'deki tablo yapımı baştan kurmak demekti. Supabase ile schema.sql'i tek seferde çalıştırıp 14 tabloyu, RLS politikalarını ve trigger'ları kurabiliyorum.

Bir diğer kritik avantaj: Supabase'in RLS'i sorgu seviyesinde izolasyonu zorluyor. Faz 2 raporunun §7.3 maddesinde "lecturer must only be able to see their own data — enforce this at the query level, not just the UI level" diye yazıyordu. Bunu Postgres'in RLS politikalarıyla yapmak çok daha sağlam: mobil uygulama hata yapsa da Supabase satır göndermiyor.

## Mimari Kararlar

### MVVM + Repository Katmanı

Faz 2'nin §7.1 maddesi MVVM ve Repository pattern istiyor. Her ekran için üç parça yazdım:

- **Fragment**: yalnızca UI binding ve event bağlantısı, iş mantığı yok.
- **ViewModel**: Coroutines ile background iş, `StateFlow<UiState<T>>` ile reactive state. `UiState` sealed class: `Idle`, `Loading`, `Success`, `Error`.
- **Repository**: Supabase çağrılarını saran suspend fonksiyonlar. ViewModel doğrudan SDK çağırmıyor.

Bu sayede dialog'ları test etmek için Robolectric ile 16 birim test yazabildim (ScheduleGenerator algoritması, CsvImporter, BackupManager, ErrorMessages, MiniXlsxReader vb.).

### Çok-Kiracılı Olmasının Sebebi

Faz 2 tek kurum varsayıyor ama tasarım sırasında "bir gün başka üniversite de kullanmak isterse?" sorusunu düşündüm. Her tabloya `org_id` kolonu ekledim ve RLS politikalarını şu kalıpla yazdım:

```sql
CREATE POLICY lect_select ON lecturers
    FOR SELECT TO authenticated
    USING (org_id = public.current_org_id());
```

`current_org_id()` SECURITY DEFINER fonksiyonu JWT'den kullanıcının `org_id`'sini çıkarır. Sonuç: aynı veritabanı içinde birden fazla üniversite tamamen yalıtılmış durumda çalışabilir. Ek bir tablo veya schema gerekmiyor — Postgres motorunun kendi mekanizması bu işi yapıyor.

Maliyeti: süper-admin panelini ayrı bir uygulama olarak yazmam gerekti çünkü kurumlar arası bir görünüm ancak `service_role` ile mümkün ve bu anahtarın mobile APK'ya gömülmesi güvenlik riski. Bunu Node.js + Express ile çözdüm.

### Çakışma Kontrolünde İki Katman

Aynı saatte iki admin atama yaparsa ne olur? Faz 2 §5.2 "prevent double-booking" istiyor. Sadece uygulama tarafında kontrol etmek yetmiyor — iki admin aynı anda formu doldurup gönderirse her ikisinin `SELECT` sorgusu boş döner ve her ikisi de `INSERT` eder.

Bu yarış koşulunu (TOCTOU) çözmek için iki katman yazdım:

1. **Uygulama katmanı**: AssignmentFragment, atama öncesi `SELECT WHERE lecturer_id=L AND day=D AND overlap(...)` yapıyor. Çakışma varsa kullanıcıya diyalog gösteriyor.
2. **Veritabanı katmanı**: `prevent_schedule_overlap` adında BEFORE INSERT/UPDATE trigger'ı, transaction içinde aynı sorguyu tekrar çalıştırıyor. İki admin aynı milisaniyede commit etse bile ikincisinin trigger'ı `RAISE EXCEPTION` ile reddediyor.

Trigger katmanı uygulama bug'larına karşı son savunma hattı. Mobil developer yanlışlıkla kontrolü atlasa bile veritabanı atlamaz.

### Excel Parser'ı Kendim Yazmak

Faz 2 Excel import istiyor (lecturers + courses + classrooms). İlk yaklaşımım Apache POI'ydi — Java standart kütüphanesi, milyonlarca proje kullanıyor. Ama Android'de POI iki sorun çıkardı:

- `ServiceLoader` mekanizması R8/ProGuard'la çatışıyor; release build kırılıyor
- APK boyutuna 8-12 MB ekliyor (POI'nin XML/schema bağımlılıkları)

Bunun yerine `MiniXlsxReader.kt` (~200 satır) ve `MiniXlsxWriter.kt` (~200 satır) yazdım. `.xlsx` formatı aslında ZIP içinde XML dosyaları — `java.util.zip` ve `XmlPullParser` ile ham olarak parse ediliyor. Sadece okumam gereken kolonlar için yazıldı, generic değil. APK boyutu 4.4 MB'da kaldı; release build'de hiç sorun çıkmadı.

## Faz 2 Gereksinimleri ve Karşılanma

Ödev şartlarını madde madde takip ettim:

| Madde | Durum |
|---|---|
| Çoklu cihaz erişimi (§2) | Supabase RLS ile, iki emülatörden eşzamanlı test ettim |
| Otomatik üretilen kullanıcı adı (§3.1) | `halit_bakir` formatı, Türkçe karakter normalize (ş→s, ç→c, ü→u) |
| 6 karakterlik geçici şifre (§3.1) | `generatePassword()` — `[A-Za-z0-9]{6}` |
| İlk girişte şifre değiştirme (§3.2) | `must_change_password` flag, PasswordChangeFragment'a zorunlu yönlendirme |
| Rol bazlı login routing (§3.3) | Admin → AdminHome, Lecturer → LecturerHome |
| Hoca takvim görünümü (§4.2) | Pzt-Cum × saat ızgarası, ders kartları, "şimdi" çizgisi |
| Derslik yönetimi (§5.1) | ClassroomsFragment + Excel import |
| Çakışma engelli atama (§5.2) | İki katmanlı (uygulama + DB trigger) |
| Admin dashboard panelleri (§6) | Atanmamış hoca/ders/derslik panelleri, real-time |
| MVVM + Repository (§7.1) | Her ekran üç katmanda |
| Şifre hashleme (§7.1) | Supabase Auth bcrypt (pgcrypto) |
| Yükleniyor + hata UX (§7.2) | Skeleton shimmer + offline banner + UNDO snackbar |
| Şifreli oturum (§7.3) | EncryptedSharedPreferences AES256-GCM |
| Query-level izolasyon (§7.3) | RLS policy'leri |

Bunların üzerine ödevin istemediği ama eklemeyi tercih ettiğim parçalar oldu: otomatik program üretici, iCal/PDF/JSON çıkışı, çok-kiracılı süper-admin paneli, CTI tehdit izleme, TR/EN dil desteği ve karanlık tema.

## Bilinen Sınırlamalar

Dürüst olmak gerekirse hâlâ tamamlamadığım birkaç şey var:

**Supabase Realtime mobile'da kullanılmıyor.** Schema'da `supabase_realtime` publication tanımlı ama mobil uygulama hâlâ `onResume` + pull-to-refresh deseniyle veri yüklüyor. WebSocket entegrasyonunu test ortamında sorunsuz çalıştırdım ama production'da connection lifecycle yönetiminden emin olamadığım için release'e dahil etmedim. İleride ekleyeceğim.

**Tablet adaptasyonu yarım.** `values-sw600dp/dimens.xml` ile padding ve tipografi büyütüldü, ayrıca admin home için `layout-sw600dp/fragment_admin_home.xml` 2-sütun düzeninde. Diğer ekranlar tablette tek sütun kalıyor — okunaklı ama optimal değil.

**Şifre değişikliği akışında "current password" alanı server tarafından doğrulanmıyor.** Supabase Auth'un `updateUser(password=...)` çağrısı zaten oturumun açık olduğunu varsayar; doğrudan eski şifreyi sormaz. UI'da alan var ama "doğru mu" diye check etmiyorum. Gerçek üretimde bunu bir Edge Function ile yapmak gerekir.

**Auth migration sırasında veri kaybı.** `schema.sql` `DROP IF EXISTS` ile başlıyor, yani şemayı her güncellediğimde tüm veri uçuyor. Bu Faz 2 için sorun değildi (zaten test datası), ama gerçek üretim için **dbmate** ile versiyonlu migration sistemini de ekledim (supabase/migrations/ klasörü). Şu an boş baseline'da; ilerideki kolon değişiklikleri buraya yazılacak.

**KVKK / Gizlilik politikası belgesi yok.** Şu an mobil uygulamada toplanan veri (kullanıcı adı, cihaz fingerprint hash'i, IP, hata stack trace) için resmi bir aydınlatma metni yok. Gerçek bir kuruma teslim öncesi avukat onaylı bir metin gerekiyor.

## Bonus Olarak Eklediğim Şeyler

Ödevin scope'u dışında olduğunu bildiğim ama yine de eklemek istediğim parçalar:

- **Otomatik program üretici** (`ScheduleGenerator.kt`): Greedy + skor tabanlı algoritma. 5 alternatif üretiyor, gün dengeleme + sıkıştır/yay tercihleri + max günlük ders sayısı kısıtları destekli.
- **CTI dashboard**: süper-admin panelde başarısız giriş analitiği — saatlik dağılım, son 30 günün trendi, GeoIP ile IP'lerin ülke bayrağı, şüpheli cihaz tespiti, isteğe bağlı Slack/Discord webhook alert.
- **İki dil + iki tema**: Hem mobil hem panelde TR/EN (396 string birebir parite, CI tarafından kontrol ediliyor), light/dark/system tema desteği.
- **CI/CD**: GitHub Actions ile her push'ta otomatik build/test, tag atınca otomatik release APK.
- **CrashHandler**: Yakalanmamış exception'lar diske yazılıyor, sonraki login'de DB'ye gönderiliyor — uygulama silindi gitti senaryosunda bile telemetri kaybolmuyor.
- **iCal/PDF/JSON çıkışları**: Hocanın haftalık programı `.ics` olarak telefon takvimine 1 tıkla aktarılabiliyor. Filtreli programlar A4 yatay PDF olarak indirilebiliyor. Tüm org verisi tek JSON dosyada yedeklenip geri yüklenebiliyor.

## Sonuç

Faz 2'nin ana hedefi olan "çoklu cihazdan erişilebilir, rol bazlı, çakışma korumalı bir program sistemi" tamam. Bunun ötesinde ekstra özellikler de eklendi çünkü her yeni özellik öğrenme fırsatıydı — özellikle RLS, Postgres trigger'ları, kotlinx.serialization ve Material 3'ün ExposedDropdownMenu pattern'i öğrendiğim önemli konular oldu.

İlerleyen aşamalarda öncelik vermek istediğim üç şey: Realtime entegrasyonunu mobile'a dahil etmek, KVKK uyumluluk belgelerini hazırlamak ve tablet için tüm ekranların 2-sütun adaptasyonunu tamamlamak.

---

*Kaynak kod:* https://github.com/mehmetyasinuzun/UniScheduler

*APK:* `app/build/outputs/apk/release/app-release.apk` (4.4 MB, release imzalı)

*Demo video:* `docs/demo/phase2-demo.mp4` (ayrı teslim)
