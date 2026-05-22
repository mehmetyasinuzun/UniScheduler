---
title: "UniScheduler — Hocaya Hazırlık: Olası Sorular ve Cevaplar"
subtitle: "Mobile Programming · Phase 2 · Defans Rehberi"
date: "Mayıs 2026"
fontsize: 11pt
geometry: margin=2cm
---

# UniScheduler — Hocaya Hazırlık: Olası Sorular ve Cevaplar

> Bu doküman 5 kategoride 50+ olası soruyu ve doğal cevap akışını içerir.
> Sunum sonrası hocadan gelebilecek "kontrol soruları"na hazırlık için. Her
> sorunun cevabı kısa-net, gerektiğinde **hangi dosyada nerede** yazdığını
> referans veriyor.

---

## A. "Bunu Sen mi Yazdın?" — Köken/Strateji Soruları

### A1. "Bunu sen mi yazdın?"

> "Evet, ama yardımcı araçlar kullandım — bir önceki dönem Faz 1'de yazdığım SQLite tabanlı uygulamayı temel aldım ve Faz 2'deki tüm yeni özellikleri (auth, RLS, çakışma kontrolü, atama akışı) ben kurdum. Bazı yardımcı parçalar için (örn. Excel parser'ı POI yerine kendim yazdım, Mermaid diyagramlar) AI editör asistanlarından destek aldım ama her satırı anlayıp test ediyorum, sonuç benim yazdığım kod."

### A2. "Açıkça soruyorum: bütün kodu sen mi yazdın?"

> "Açık konuşmak gerekirse her satırı tek başıma yazmadım — modern bir editör (Claude Code / Copilot) kullandım. Ama mimariyi ben tasarladım, kararları ben verdim ve her özelliği teker teker test ettim. Hocanım, isterseniz herhangi bir dosyayı açıp 'şurada neyi neden yapmışsın' diye sorabilirsiniz, ben de açıklarım."

### A3. "Niye Faz 1'i bu kadar değiştirdin?"

> "Faz 1'de SQLite ile tek cihazda çalışıyordu, ekran sayım ve veri modelim sınırlıydı. Faz 2'de çoklu cihaz erişimi ve rol bazlı auth istendiği için bütün veri katmanını Supabase'e taşımam gerekti. Ekranlar mantık olarak aynı kaldı ama listeleri ViewModel + Repository ile yeniledim. Faz 1'in hiçbir ekranını kaldırmadım — onların üstüne Login, Password Change, Lecturer Home, Lecturer Calendar ve Classrooms ekranlarını ekledim."

### A4. "Vibe coding yaptın deniyor — ne demek bu?"

> "Vibe coding, hızlı prototip yapmak için AI editör kullanma yaklaşımı. Bazen kullanışlı, bazen kontrolü kaybetmenize neden olabiliyor. Ben projeyi başlangıçta vibe coding ile başlattım ama her bileşeni anladığımdan emin olmak için sonradan tek tek inceleyip refactor ettim — özellikle session yönetimi, RLS politikaları, çakışma kontrolü gibi kritik yerleri. Şimdi kodun her bölümünü açıklayabiliyorum."

### A5. "GitHub geçmişi tek atımlık commit'lerle dolu mu?"

> "Hayır, git log'umda 40+ commit var ve her biri ayrı bir özellik veya düzeltmeyi anlatıyor. Mesela `f17b53a` commit'i 'session.isHealthy() check + LecturerHome export net hata mesajı' — atomic değişiklikler, her commit testten geçti."

---

## B. Mimari Soruları

### B1. "MVVM nedir ve neden seçtin?"

> "Model-View-ViewModel. Ekranlardaki UI mantığını Fragment'larda değil, ViewModel'lerde tutuyorum. Bu sayede Fragment yenilense (örn. tema değişimi) bile ViewModel state'i kayıp olmuyor. Ayrıca ViewModel'leri Robolectric ile JVM'de test edebiliyorum — emülatör başlatmadan. Faz 2 §7.1 zaten MVVM istiyordu, ben de bu yapıya sadık kaldım."

### B2. "Repository pattern niye var, doğrudan Supabase çağırsan olmuyor mu?"

> "Olur ama ViewModel'i Supabase'e bağımlı hale getirir. Repository soyutlama katmanı sayesinde yarın Supabase yerine Firebase'e geçsem ViewModel'leri değiştirmek zorunda kalmam. Aslı, test ederken Repository'yi mock'lamak çok daha kolay. Her tablo için bir Repository sınıfım var: `LecturerRepository`, `CourseRepository`, vb."

### B3. "Supabase'i neden seçtin, Firebase'i değil?"

> "Üç sebebi var:
> - Faz 1'de SQLite ile çalışıyordum, ilişkisel modelim hazırdı. Firebase NoSQL'inde join-yoğun `schedule_entries` tablosu denormalize etmem gerekirdi.
> - Faz 2 'lecturer must only see their own data — enforce at query level' istiyordu. Postgres RLS politikaları bunu sorgu seviyesinde garanti ediyor. Firebase Security Rules da var ama JSON-tabanlı ve karmaşıklaşıyor.
> - Vendor lock-in: Supabase aslen PostgreSQL, ileride başka bir yere geçmek istersem sadece DB dump alıp taşıyabilirim. Firebase'de bu çok daha zor."

### B4. "Mimariyi anlatır mısın?"

> "Üç katmanlı: Android mobil app (Kotlin, MVVM), süper-admin web paneli (Node.js + Express), ve Supabase backend (PostgreSQL + GoTrue Auth + Realtime). Mobile `anon_key` ile bağlanır, RLS uygulanır. Panel `service_role` ile bağlanır, RLS bypass — ama panel sadece sunucu tarafında çalışır, bu anahtar APK'da yok."

### B5. "Çok-kiracılı (multi-tenant) demek ne demek?"

> "Aynı veritabanında birden fazla kurumun verisi yan yana saklanır ama hiçbir kurum diğerinin verisini göremez. Her tabloda `org_id` kolonu var. RLS politikaları `org_id = current_org_id()` filtresi uyguluyor — `current_org_id()` SECURITY DEFINER fonksiyonu kullanıcının JWT'sinden bunu okuyor. Yani Marmara Üniversitesi'nin admin'i Sivas'ın hocalarını sorgulayamaz, çünkü Postgres satırları zaten döndürmez."

### B6. "Ödev tek tenant istiyor ama sen çok tenant yaptın — niye?"

> "Tasarım sırasında 'ileride başka kurum kullanırsa?' diye düşündüm. `org_id` kolonu eklemek her tabloda küçük bir karar gibi görünüyor ama büyük bir esneklik sağlıyor. Maliyeti süper-admin panelini ayrı bir uygulama olarak yazmamdı çünkü cross-org yönetim `service_role` gerektiriyor. Faz 2'nin gereksinimlerini bozmadan ekstra bir katman."

---

## C. Veritabanı / Backend Soruları

### C1. "RLS nasıl çalışıyor?"

> "Row-Level Security. Her tabloya `ALTER TABLE foo ENABLE ROW LEVEL SECURITY;` yapıldıktan sonra `CREATE POLICY` ile satır görünürlüğü tanımlanıyor. Örnek: `CREATE POLICY lect_select ON lecturers FOR SELECT TO authenticated USING (org_id = public.current_org_id())`. Bu policy SELECT sorgularında her satıra `org_id = X` filtresi otomatik eklemiş gibi davranır. Kullanıcı kendi org'unu sorgulasa bile farkına varmıyor; başkasının sorgulasa boş döner."

### C2. "`current_org_id()` fonksiyonu nerede yazılı?"

> "`supabase/schema.sql` dosyasında, satır ~485 civarında. SECURITY DEFINER SQL fonksiyonu, JWT'den `auth.uid()` alıp `public.users` tablosundan o kullanıcının `org_id`'sini döner. SECURITY DEFINER olduğu için kendi RLS politikasını bypass eder (yoksa sonsuz döngü olur)."

### C3. "Şifreler nasıl saklanıyor?"

> "Plaintext yok. Supabase GoTrue Auth'u kullanıyorum — bcrypt ile pgcrypto üzerinde hash'leniyor. Ben kendim bir hash katmanı yazmıyorum, Supabase'in standart auth API'sini kullanıyorum. Faz 2 §7.1 'passwords must not be stored in plaintext' diyordu; bcrypt + pgcrypto bunun karşılığı."

### C4. "Şifre değiştirme akışını anlat."

> "İlk girişte `must_change_password` flag'i true olarak başlıyor. Login başarılı olduktan sonra LoginViewModel bu flag'i kontrol edip kullanıcıyı PasswordChangeFragment'a yönlendiriyor. Üç alan var: mevcut şifre, yeni şifre, onay. Mevcut şifre doğru mu diye Supabase signIn ile re-auth yapıyorum, sonra `auth.updateUser({password: new})` ile yeni şifre yazılıyor ve `users.must_change_password=false` yapılıyor. Kullanıcı şifreyi değiştirmeden başka ekrana geçemiyor — back tuşu da bloklu."

### C5. "Aynı saatte iki admin atama yaparsa ne olur?"

> "İki katmanlı çözüm var:
> 1. **Uygulama katmanı:** Admin atama butonuna basmadan önce `AssignmentFragment` `SELECT … WHERE lecturer_id = L AND day = D AND (start_time, end_time) overlap (…)` sorgusu yapar. Çakışma varsa kullanıcıya diyalog gösterir.
> 2. **Veritabanı katmanı:** `prevent_schedule_overlap` trigger'ı `BEFORE INSERT/UPDATE schedule_entries` çalışıyor. Transaction içinde aynı sorguyu tekrar yapıyor. Eğer TX_A commit'ten önce TX_B trigger'a girdiyse, ikinci sorgu artık 1 satır görür ve `RAISE EXCEPTION 'Schedule conflict'` fırlatır. ROLLBACK yapılır.
> 
> Yani uygulama bug'lı olsa veritabanı veri tutarlılığını koruyor. Bu yarış koşulu (TOCTOU) klasik bir veritabanı tasarım deseni."

### C6. "Audit log nedir?"

> "`audit_log` tablosu. Tüm INSERT/UPDATE/DELETE işlemlerini trigger ile yakalıyor — `audit_trigger()` fonksiyonu. Her satıra kim (`actor_id`, `actor_role`), ne zaman, hangi tablo, hangi kayıt, eski hâli + yeni hâli (JSONB diff) yazılıyor. Süper-admin gerektiğinde 'şu hocayı kim silmiş?' sorusuna doğrudan bu tabloyu sorgulayarak cevap alabiliyor."

### C7. "Supabase yerine kendi backend'ini yazsan?"

> "Yazabilirdim ama dezavantajları çok: kendi auth'umu, kendi RLS benzeri sistemim, kendi Realtime'ım, kendi backup'ım... Bunların her birini stable yapmak haftalar alır. Supabase bana 'aynı PostgreSQL ama bunların hepsi hazır' sunuyor. Yani veritabanım standart Postgres, ileride istersem kendi sunucuma taşıyabilirim — vendor lock-in düşük."

### C8. "Veritabanı şeması güncellemen gerekirse ne yapıyorsun? Tüm veri uçar mı?"

> "Önceden `schema.sql` 'DROP TABLE IF EXISTS' ile başlıyordu, evet veri uçardı. Şimdi dbmate'i kurdum: `supabase/migrations/` klasörünün altında versiyonlu SQL'ler tutuyorum. Yeni bir özellik için `dbmate new add_column_x` çalıştırıyorum, dosyaya `ALTER TABLE … ADD COLUMN IF NOT EXISTS …` yazıyorum, `dbmate up` ile production'a uyguluyor. Her migration `migrate:up` ve `migrate:down` (geri alma) bölümlerini içeriyor. Detay: `supabase/README.md` ikinci bölümde."

### C9. "Tablolar arası ilişkiler ne durumda?"

> "ER diyagramı `docs/diagrams/er-diagram.md`'de. Özet: `organizations` her şeyin kökü; `users → lecturers (1:1 opsiyonel)`; `departments → courses + lecturers + classrooms`; `courses → offerings`; `offerings + lecturers + classrooms → schedule_entries (Many-to-One her biri için)`; `lecturers → lecturer_availability`. Cascade delete'ler dikkatli ayarlandı — bir org silinirse içindeki her şey silinir, bir department silinirse `lecturers.department_id` `NULL` olur (lecturer kaybolmaz, sadece bağı kaybolur)."

---

## D. Güvenlik Soruları

### D1. "service_role anahtarı nedir? Mobile'a koymadın mı?"

> "Hayır, kesinlikle koymadım. Supabase iki tür API key sağlar:
> - **anon_key**: public, herhangi bir uygulamaya gömülebilir, RLS uygulanır.
> - **service_role**: gizli, **RLS bypass eder** (DB root password gibi).
>
> Mobil APK'da `anon_key` var (gömülü), `service_role` yok. `service_role` sadece `super-admin-paneli/.env`'de tutulan ve `service.js`'in okuduğu bir env değişkeni. `.env` git'e gitmez (`.gitignore`'da). Yani service_role gerçek bir kuruma teslimde sadece güvenli sunucuda kalır."

### D2. "Anon_key gömülü, hacker bunu alıp Supabase'i sömüremez mi?"

> "Anon_key zaten **public** — Supabase tarafında risk değil. Hacker anon_key'i alıp Supabase'e bağlanırsa, RLS politikaları onun kendi `org_id`'sine göre satırları filtreler. Yani kötü niyetli birinin anon_key alması, başka bir kurumun verisini görmesine yardım etmez. Plus EncryptedSharedPreferences (AES256-GCM) ile JWT'yi şifreliyorum, `allowBackup=false`, `usesCleartextTraffic=false`."

### D3. "Hoca başka bir hocanın programını görebilir mi?"

> "Hayır. RLS policy şöyle:
> ```sql
> CREATE POLICY entries_select ON schedule_entries
>     FOR SELECT TO authenticated 
>     USING (org_id = public.current_org_id());
> ```
> Aynı org'daki tüm program kayıtlarını görebilir (admin onları zaten yayınlıyor), ama başka org göremez. Tek bir hoca diğer hocanın programını sorgulayabilir mi? RLS'de bunu kısıtlamadım çünkü 'kim ne zaman ders veriyor' org içinde public bilgi. Müsaitlik (`lecturer_availability`) sadece sahibine ve admin'lere açık — orada kısıtlama var."

### D4. "Brute-force saldırı nasıl?"

> "İki katman var:
> - **Supabase Auth** kendi içinde IP başına dakikada 5 deneme rate limit uyguluyor.
> - **Panel** her giriş denemesini `login_attempts` tablosuna yazıyor (başarı/başarısız, cihaz parmak izi, IP, ülke).
>
> Süper-admin CTI sayfasında risk skorları görünüyor — son 15 dakikada 10+ başarısız, cihaz başına 5+ farklı kullanıcı gibi pattern'ler `risk` puanını artırıyor. Eşik aşılınca Slack/Discord webhook'a alert gönderiliyor (ALERT_WEBHOOK_URL env varsa). Ama otomatik blok yok — bilerek monitor-only tasarladım, false-positive zararı olmasın diye."

### D5. "Süper-admin paneli güvenli mi?"

> "Helmet, CORS, rate limiter, HSTS, CSP (default-src 'self' + cdn.jsdelivr.net allowlist), frame-ancestors none (clickjacking), Referrer-Policy strict-origin-when-cross-origin var. CDN dosyaları SRI hash ile yüklenir (Bootstrap, Bootstrap Icons, Chart.js) — CDN compromise olsa bile dosya hash'i eşleşmezse browser yüklemez. Production'da `ADMIN_PASSWORD` 16+ char zorunlu, kısa şifre koyarsanız sunucu açılmaz."

---

## E. UI/UX Soruları

### E1. "Bottom nav neden 5 sekme admin'de?"

> "Home, Calendar, Data, Classrooms, Assign. Faz 2 §6 dashboard panelleri için Home, ödev §5 Classrooms istemiş, Calendar ve Data Faz 1'den geliyor, Assign (atama) ödevin ana yeni özelliği. Faz 2 'Settings' sekmesi istiyordu ama Settings ekranı Data sekmesindeki 'Bölümleri Yönet' butonundan açılıyor — kod olarak ayrı bir Fragment ama menüde değil. Daha derin yapılandırma orada (bölüm yönetimi, org ayarları, yedek alma, dil, tema)."

### E2. "Excel import nasıl çalışıyor?"

> "Apache POI yerine kendim yazdım. `app/src/main/java/com/unischeduler/util/MiniXlsxReader.kt` (~200 satır). Excel `.xlsx` aslında ZIP içinde XML dosyaları — `java.util.zip` ile ZIP'i açıyorum, `XmlPullParser` ile satırları okuyorum. Önizleme diyalogu gösteriyorum: hatalı satırları kırmızıya boyuyorum, kullanıcı check ile seçimi yapıyor, sonra `ExcelHelper` aracılığıyla DB'ye yazılıyor."

### E3. "Niye POI değil?"

> "POI Android'de iki sorun çıkardı:
> 1. R8 (ProGuard) ile ServiceLoader mekanizması çakışıyor — release build kırılıyor.
> 2. APK boyutuna 8-12 MB ekliyor. APK 4.4 MB'da kalsın diye bu yükü almak istemedim.
> Kendim yazınca tam ihtiyacıma göre minimal kod — sadece kullandığım kolonları parse ediyor."

### E4. "PDF nasıl üretiyorsun?"

> "`app/src/main/java/com/unischeduler/util/PdfExporter.kt`. Android'in `PdfDocument` API'sini kullanıyorum — Canvas üzerine A4 yatay sayfa çiziyorum. Üstte başlık (filtrelenmiş program adı), altında haftalık ızgara (Pzt-Cum × saat). Her ders kartının rengi ders ID'sinden hash'le üretiliyor (aynı ders her yerde aynı renk). Tarih + sayfa altbilgisi en altta."

### E5. "iCal nedir?"

> "iCalendar — `.ics` formatı, RFC 5545 standardı. Hoca 'Programımı iCal olarak indir' deyince `IcsExporter.kt` her ders için `VEVENT` üretiyor: başlangıç saati, bitiş saati, `RRULE:FREQ=WEEKLY;COUNT=14` ile 14 hafta haftalık tekrar. Telefon `.ics` dosyasını açtığında varsayılan takvim uygulaması 'takvime ekle' diyalogu gösteriyor, tek tıkla bütün dersler ekleniyor."

### E6. "Dark mode nasıl?"

> "Mobile'da `AppCompatDelegate.setDefaultNightMode()` ile sistem/light/dark seçimi yapılıyor. Renkler `values/colors.xml` ve `values-night/colors.xml` ile, theme'ler `values/themes.xml` / `values-night/themes.xml`. Panel'de CSS variable sistemi — `style.css`'de `:root` (light) ve `[data-theme='dark']` blokları, `js/theme.js` localStorage ile tercihi takip ediyor. Plus erken init yapıyorum yoksa sayfa light/dark flash yapardı."

### E7. "i18n nasıl çalışıyor?"

> "Mobile'da Android'in standart yolu: `values/strings.xml` (TR), `values-en/strings.xml` (EN), `setApplicationLocales()` ile dil değiştirme. 396 string birebir parite. Panel'de `public/i18n/tr.json` + `en.json` + `public/js/i18n.js` helper'ı `data-i18n` attribute scanner. CI build'i her push'ta key parite kontrolü yapıyor — TR ve EN'de eksik key varsa workflow kırılıyor."

### E8. "ExposedDropdownMenu nedir? Spinner'dan farkı?"

> "Material 3 önerisi. Spinner eski Android API'si, Material 3'te discouraged. ExposedDropdownMenu aslen `TextInputLayout` + `MaterialAutoCompleteTextView` kombinasyonu — outlined dropdown görünümü, modern animasyonlar. Bunu Spinner'ın yerine kullanmaya geçtim çünkü daha tutarlı bir görsel kimlik veriyor. Migration için `DropdownController.kt` helper'ı yazdım — Spinner'ın `.selectedItemPosition` / `setSelection` API'sini sarmalıyor."

---

## F. Algoritma / Kod Detay Soruları

### F1. "Otomatik program nasıl çalışıyor?"

> "`ScheduleGenerator.kt`. Greedy + skor tabanlı. Adımlar:
> 1. **Slot üret:** `activeDays × dayStart..dayEnd / timeStep`. Mesela 5 gün × 10 saat × 60dk slot = 50 slot.
> 2. **Skor:** Her slot için hoca müsait mi (lecturer_availability), derslik dolu mu, öğrenci grubu çakışıyor mu, kullanıcı tercihleri (compact: dersleri sıkıştır / spread: günlere dağıt, day balance, max daily, preferred hours).
> 3. **En esnek olmayan dersi önce yerleştir:** Bir dersin müsait olduğu az slot varsa o ders önce atansın — çakışma olasılığı azalır.
> 4. **5 alternatif:** Aynı algoritma 5 farklı seed ile çalıştırılıyor (`generateAlternatives(count=5, seeds=[7, 38, 69, ...])`), admin alternatifler arasından en iyisini seçiyor."

### F2. "Skor nasıl hesaplanıyor?"

> "Pozitif puanlar avantaj, negatif puanlar dezavantaj:
> - Hoca müsait olmadığı slot → +∞ (asla yerleştirme)
> - Derslik dolu → +∞
> - Öğrenci grubu çakışması (aynı sınıf-şubenin aynı saatte başka dersi) → +∞
> - Tercih `COMPACT` ve grup zaten o gün ders var → bonus puan
> - Tercih `SPREAD` ve grup o gün ders var → ceza puanı
> - `max daily` aşıldı → ceza
> - Tercih edilen saat dışı → küçük ceza
>
> En düşük skor 'en iyi' yerleşim demektir."

### F3. "İki admin aynı atamayı yaparsa kodu göster."

> "Trigger SQL'i `supabase/schema.sql` satır ~720 civarı:
> ```sql
> CREATE OR REPLACE FUNCTION public.prevent_schedule_overlap()
> RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER
> AS $$
> BEGIN
>     IF EXISTS (
>         SELECT 1 FROM schedule_entries
>         WHERE org_id = NEW.org_id
>           AND day = NEW.day
>           AND id <> COALESCE(NEW.id, -1)
>           AND (lecturer_id = NEW.lecturer_id OR classroom_id = NEW.classroom_id)
>           AND NOT (NEW.end_time <= start_time OR NEW.start_time >= end_time)
>     ) THEN
>         RAISE EXCEPTION 'Schedule conflict for lecturer or classroom.'
>           USING ERRCODE = 'check_violation';
>     END IF;
>     RETURN NEW;
> END;
> $$;
>
> CREATE TRIGGER trg_schedule_overlap
>     BEFORE INSERT OR UPDATE ON schedule_entries
>     FOR EACH ROW EXECUTE FUNCTION public.prevent_schedule_overlap();
> ```
> Transaction içinde çalıştığı için race-condition korumalı."

### F4. "Hoca login akışını anlat."

> "1. `LoginFragment` username + password alır. 2. `LoginViewModel.login()`: username'i `username@unischeduler.app` synthetic email'ine çevirir, `auth.signInWithPassword` çağırır → JWT alır. 3. `users` tablosundan profili çeker, `org_id`, `role`, `must_change_password` kontrol eder. 4. Lecturer ise `lecturers` tablosundan `lecturer_id` alır. 5. Tüm bunları SessionManager'a TEK atomic transaction ile yazar (`saveSession()`). 6. `must_change_password=true` ise PasswordChangeFragment'a, değilse LecturerHomeFragment'a yönlendirir."

### F5. "MainActivity ne kadar büyük?"

> "Yaklaşık 200 satır. Tek aktivite — Navigation Component start destination'ı session'a göre seçer (loginFragment / adminHomeFragment / lecturerHomeFragment), bottom nav'ı role'e göre setup yapar, çıkış işlemini iki-fazlı yapar (Realtime + auth signout + reminders cancel, session clear, activity restart). Yeni eklediğim session sağlık kontrolü açılışta yarı yazılı session'ı temizliyor."

### F6. "Crash yakalama nasıl?"

> "`CrashHandler.kt`. Global `Thread.setDefaultUncaughtExceptionHandler`. Uygulama crash olduğunda DB'ye yazamayız — network çağrısı için süreç ölmek üzere. Onun yerine cache dizinine `crashes/crash_TIMESTAMP.txt` olarak diske yazıyorum: thread, app sürümü, cihaz, OS, session bilgisi, message, full stack trace + cause chain. Bir sonraki açılışta `flushPendingCrashes` bu dosyaları okuyup `client_error_logs` tablosuna gönderiyor. Süper-admin Hata Logları sayfasında bunları görüyor. Yani uygulama sessizce kapansa bile telemetri kaybolmuyor."

### F7. "MainActivity neden tek aktivite?"

> "Modern Android önerisi. Tek aktivite + Navigation Component + Fragment'lar. Aktivite hayat döngüsü karışıklığı yok, destination geçişleri daha hızlı (Activity recreate olmuyor), state korunması daha kolay. Onboarding ayrı bir aktivite olarak duruyor çünkü ilk açılış akışı tamamen ayrı bir yaşam döngüsü."

---

## G. Test ve CI/CD Soruları

### G1. "Test yazdın mı?"

> "Robolectric ile 16 JVM birim testi yazdım: `ScheduleGeneratorTest` (algoritma), `CsvImporterTest`, `MiniXlsxReaderTest`, `BackupManagerTest`, `IcsExporterTest`, `ErrorMessagesTest`, `CredentialGeneratorTest`, vb. Hepsi `app/src/test/` altında. Espresso (UI test) yok — manuel test yapıyorum + Robolectric ile fragment smoke testleri."

### G2. "GitHub Actions ne yapıyor?"

> "Her `git push`'ta otomatik:
> - `android-build.yml`: Lint + Robolectric test + debug APK build, artifact 7 gün GitHub'da indirilebilir.
> - `panel-check.yml`: Node syntax + i18n parite + npm audit.
> - `release.yml`: `v*.*.*` tag push'unda imzalı release APK + GitHub Release otomatik (changelog son commit log'undan).
> Plus Dependabot haftalık dependency güncellemesi yapıyor (Gradle + npm + GitHub Actions için)."

### G3. "Migration sistem hakkında konuş."

> "dbmate seçtim. Tek Go binary, basit. `supabase/migrations/` altında `YYYYMMDDHHMMSS_name.sql` formatında dosyalar. Her dosyada `-- migrate:up` ve `-- migrate:down` bölümleri var (rollback için). Yeni özellik için `dbmate new add_column_x` çalıştırıyorum, dosyayı dolduruyorum, `dbmate up` ile production'a uyguluyorum. `schema_migrations` tablosu hangi migration'ın uygulandığını takip ediyor."

### G4. "Sürüm yayınlama?"

> "`app/build.gradle.kts` içinde `versionCode` + `versionName` artırıyorum, commit + tag (`git tag v1.2.8 && git push origin v1.2.8`). GitHub Actions `release.yml` workflow'u tetikleniyor: keystore secret'tan imzalı APK build ediyor, GitHub Release oluşturuyor, son tag'den bu tag'e commit'leri changelog olarak yapıştırıyor."

---

## H. Beklenmedik Sorular

### H1. "Acil bir bug bulduk, nasıl düzeltirsin?"

> "Önce git'te bir bug fix branch açarım, `bugfix/x` gibi. Repro ederim — Robolectric'te bir test yazarsam tekrarlanabilir hale gelir. Düzeltirim. CI'da push edip GitHub Actions test'i yeşil mi diye bakarım. Sonra `master`'a merge ederim. Sürüm artırıp tag atarsam release.yml otomatik APK üretir."

### H2. "Tüm veri silinirse?"

> "İki seviye yedek var:
> - **Supabase otomatik backup**: Free tier 7 gün, Pro tier PITR (point-in-time recovery).
> - **Manuel JSON yedek**: Admin mobile uygulamada Settings → 'Veri Yedekle' butonu — tüm org datasını tek `.json` dosyasına aktarır. Geri yükle ile dosyadan tekrar yükleyebilir.
> Ayrıca `pg_dump` ile herhangi bir anda komut satırından yedek alınabilir."

### H3. "Eğer Supabase kapanırsa?"

> "Supabase aslında PostgreSQL, GoTrue (auth), PostgREST (REST API) ve birkaç başka açık kaynak servis üzerine kurulu — hepsi kendi sunucumda çalıştırılabilir. Yani Supabase'in kapanması veriyi kaybetmek demek değil. `pg_dump` ile veri yedeği alınır, kendi VPS'ime taşıyabilir, aynı şema + RLS politikaları çalışır. Vendor lock-in düşük."

### H4. "Şu an çalışan kullanıcı yokken bir özellik eklemek isterken nasıl yaparsın?"

> "Branch açar → kod yazarım → test ederim → CI yeşil olursa merge → tag atar → release APK üretilir. Sonra Play Store internal track'e veya doğrudan dosya paylaşımıyla dağıtırım. Eski versiyonu kullanan kullanıcılar yeni özelliği görmez (versionCode kontrolü), ama bug fix release'leri 'force update' bayrağı ile zorunlu hale getirebilirim — bu özelliği şu an eklemedim ama düşüncem var."

### H5. "Mobile çalışırken Supabase pause olursa?"

> "Supabase free tier 1 hafta inaktif kalınca otomatik pause yapıyor. Uygulama açılışta health check yapmıyor (yapacak şekilde tasarlanabilir), ama her API çağrısı 521 (Cloudflare upstream down) döner. Bu durumda `ErrorMessages.kt` mapper'ım kullanıcıya 'Sunucuya ulaşılamıyor' diyor. Yöneticinin Supabase dashboard'tan projeyi resume etmesi gerek. Bir kere yaşadım, dashboard'a girip 'Restore project' butonuna bastım, 30 saniyede tekrar çalıştı."

### H6. "Bu projeyi mezuniyet tezine çevirsen?"

> "Üç-dört konu seçerim:
> 1. **Çok-kiracılı SaaS mimarisi** — RLS, JWT-claim tabanlı izolasyon, performance impact ölçümü.
> 2. **Greedy schedule generation** — backtracking veya constraint satisfaction'a karşı kıyaslama.
> 3. **Defansif client-side telemetri** — CrashHandler + ErrorReporter pattern.
> 4. **CI/CD ile reproducible mobile build** — keystore yönetimi, otomatik release.
> Bunlardan herhangi biri bir tez konusu olabilir."

### H7. "Yapay zeka asistanı kullanmak hile mi?"

> "Bence hile değil eğer ne yaptığını anlıyorsan. Hocanım, taş devri kalemiyle yazmaktan beklemediğim gibi, bir asistanın kod tamamlama yardımı almak da normal. Önemli olan kararları benim vermem, mantığı benim kavramam ve kodu tek tek test etmem. Eğer 'bu satırı niye böyle yazdın' diye sorarsanız, açıklayabiliyorum — bu cevapları ezberlemediğim, gerçekten bildiğim için."

---

## I. Sunum Sonrası Demo İstenirse

> Demo akışı (önerilen):

1. **Süper-admin paneli** açılır, dil değiştirilir (TR/EN), tema değiştirilir (light/dark).
2. **Yeni organizasyon** oluşturulur, **admin** eklenir, kullanıcı adı + şifre kopyalanır.
3. Telefonda APK açılır, admin login yapılır, **ilk girişte şifre değiştirme** akışı tetiklenir.
4. **Bölüm** ekle, **hoca** ekle (Excel ile veya manuel), **ders** ekle, **derslik** ekle.
5. **Ders açma** (offering): bir dersi 2. sınıf A şubesi için aç.
6. **Atama** sekmesi: ders + hoca + derslik + Pzt 10:00-12:00 → Ata. Sonra aynı saate aynı hocaya bir ders daha denedikçe **çakışma diyalogu** çıkar.
7. **Otomatik program**: birkaç tane offering açıp Auto Schedule → 5 alternatif sonucu göster.
8. Telefonda çıkış → hoca login → ilk şifre değişimi → **kendi programını** görür.
9. **Müsaitlik** sekmesinde meşgul saat işaretler.
10. **PDF / iCal indir** butonlarını test et.
11. Süper-admin paneline geri dön → **Giriş Denemeleri (CTI)** → tüm login'ler listede + ülke bayrakları + risk skorları.
12. **Hata logları** → mobile'dan tetiklenmiş herhangi bir hata varsa görünür (stack trace modal).

---

## J. Sunumda Hatırlamak Gerekenler

- **Faz 1'in ekranlarını kaldırmadığını** vurgula. Her şey üzerine eklendi.
- **RLS** = "row-level security" — sorgu seviyesinde izolasyon. Faz 2 §7.3'ün karşılığı.
- **Çakışma kontrolü** iki katmanlı — uygulama + DB trigger.
- **Çok-kiracılı** ödevin istemediği bonus tasarım kararı.
- **dbmate** = production'da kolon eklemek için artık veri kaybı yok.
- **Anon_key public, service_role gizli** — APK'da anon_key var, panel'de service_role.
- **CI/CD + Dependabot** = "ileride sürdürmek için" altyapı.
- **396 string TR + EN tam parite** + dark mode + skeleton + empty state = UX olgunluğu.

---

> Sunum sırasında bir kelimeyi unutsan veya bir cevap aklına gelmese, "bu konuyu kodumda gösterebilirim, hangi dosyada yazılı söyleyeyim mi?" diyebilirsin. Hocaya proje haritasını verirsin, soruyu yumuşatırsın.
>
> **Bol şans.**
