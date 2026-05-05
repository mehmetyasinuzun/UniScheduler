# UniScheduler — Eksikler & Best Practice İhlalleri
> Senior Developer Gözünden Tam Denetim Raporu

---

## 1. KRİTİK İŞLEVSEL EKSİKLER

### 1.1 Logout Yok
- `SessionManager.clear()` metodu var ama **hiçbir yerde çağrılmıyor**.
- Admin veya Lecturer ekranında logout butonu/menüsü yok.
- Kullanıcı uygulamayı kapatıp açtığında session devam ediyor (EncryptedSharedPreferences'dan okunuyor) — bu beklenen davranış. Ama oturumu sonlandırmanın hiç yolu yok.
- **Fix:** `MainActivity`'de `Options Menu` veya her role'e özel bir "Çıkış Yap" butonu ekle. Tıklanınca `session.clear()` çağır, ardından `navController.navigate(R.id.loginFragment)` ile yönlendir ve back stack'i temizle.

### 1.2 DataFragment Stub
- `DataFragment.kt` içinde sadece `TextView("Data import screen — coming soon.")` var.
- Bottom navigation'da "Data" sekmesi görünüyor ama içi boş.
- `LecturerRepository.insertLecturerWithUser()` hazır, `CredentialGenerator` ve `PasswordHasher` hazır — ama bu fragment'a bağlı değil.
- **Fix:** En azından manuel lecturer ekleme formu (isim, unvan, departman spinner) bağlanmalı. Dosyadan import ikinci aşama olarak bırakılabilir ama form olmadan MVP eksik.

### 1.3 SettingsFragment Stub
- `SettingsFragment.kt` de yalnızca `TextView("Settings screen — coming soon.")` döndürüyor.
- Admin bottom nav'ında görünüyor, içi tamamen boş.
- Proje gereksinimlerine göre burada departman yönetimi (insert/list) olması gerekiyor.

### 1.4 AdminCalendarFragment'te Retry Butonu Bağlanmamış
- `AdminCalendarFragment.onViewCreated()` içinde `binding.btnRetry.setOnClickListener { ... }` çağrısı **yok**.
- Hata durumunda "Retry" butonu görünse bile hiçbir şey yapmıyor.
- Karşılaştırma: `AdminHomeFragment`, `LecturerHomeFragment`, `ClassroomsFragment` hepsinde retry listener var. Sadece `AdminCalendarFragment`'ta unutulmuş.

### 1.5 Assignment Silinemiyor
- `ScheduleRepository.deleteEntry(entryId: Int)` metodu yazılmış.
- `ScheduleEntryAdapter` içinde delete butonu veya swipe-to-delete yok.
- `item_schedule_entry.xml`'de silme UI'ı tanımlanmamış.
- Kullanıcı bir atamayı listeleyebiliyor ama silemez. Feature yarım kalmış.

### 1.6 Classroom'a Departman Atanamıyor
- `ClassroomsFragment.btnAddClassroom` click handler'ında `departmentId = null` hardcoded:
  ```kotlin
  viewModel.addClassroom(roomCode = ..., capacity = ..., departmentId = null)
  ```
- Kullanıcı arayüzünden bölüm seçilemiyor. Tüm derslikler departmansız ekleniyor.

---

## 2. LAYOUT / UI HATALARI

### 2.1 activity_main.xml — NavHostFragment Constraint Hatası
- `navHostFragment` şu anda sadece `bottomNavAdmin`'e constrain edilmiş:
  ```xml
  app:layout_constraintBottom_toTopOf="@id/bottomNavAdmin"
  ```
- Lecturer girişinde `bottomNavAdmin` GONE yapılıyor → ConstraintLayout GONE view'a karşı constraint'i ignore eder ve `navHostFragment` ekranın altına kadar uzar → `bottomNavLecturer`'ın üzerine çakışır → İçerik bottom nav arkasında kalır.
- **Fix:** Her iki bottom nav'ı tek bir `FrameLayout` wrapper'ına al ve `navHostFragment`'ı ona constrain et. Ya da `MainActivity.onCreate()`'de aktif nav'a göre `navHostFragment` bottom padding'i dinamik set et.

### 2.2 SimpleTextAdapter — "None" Mesajı Asla Görünmüyor
`AdminHomeFragment` içindeki `SimpleTextAdapter`:
```kotlin
override fun getItemCount() = items.size  // boş liste → 0
override fun onBindViewHolder(holder: VH, position: Int) {
    holder.tv.text = if (items.isEmpty()) "None" else items[position]  // DEAD CODE
}
```
- `items` boşsa `getItemCount()` 0 döner → `onBindViewHolder` hiç çağrılmaz → "None" **asla ekranda görünmez**.
- RecyclerView'lar tamamen boş görünür; kullanıcı veri yüklenip yüklenmediğini anlayamaz.
- **Fix:** Boş liste durumu için ayrı bir `emptyView` (TextView) kullan veya `getItemCount()` > 0 kontrolüne göre adapter-level empty state yönet.

### 2.3 Input Validation Yok — ClassroomsFragment
- `etCapacity` boş bırakılabilir veya negatif/sıfır girilebilir.
- `etRoomCode` boş gönderilebilir.
- ViewModel'de de validasyon yok; bu doğrudan DB insert'e gidiyor ve Supabase NOT NULL constraint hatası dönebilir — raw hata mesajı kullanıcıya gösteriliyor.

---

## 3. NAVİGASYON EKSİKLERİ

### 3.1 Assignment Ekranına Direkt Erişim Yok
- `assignmentFragment` nav_graph'ta sadece `classroomsFragment`'tan gelen bir action'la ulaşılabiliyor.
- Admin bottom nav'ında "Assignment" tabı yok.
- Admin'in atama yapmak için önce "Classrooms" tab'ına gidip oradan butona tıklaması gerekiyor. Bu sezgisel değil.
- **Fix:** Assignment'ı admin bottom nav'ına direkt tab olarak ekle VEYA classrooms tab'ının adını/ikonunu "Assign" olarak düzenle ve niyeti netleştir.

### 3.2 Lecturer PasswordChange Sonrası Back Stack
- `action_login_to_passwordChange` loginFragment'i pop ediyor (`popUpToInclusive="true"`).
- `passwordChangeFragment` tamamlandığında `action_passwordChange_to_lecturerHome` çalışıyor.
- Bu zincir doğru. Ama eğer ağ hatası nedeniyle password change başarısız olursa ve kullanıcı geri butonuna basarsa → uygulama kapanır (login stack'te yok). Hata senaryosu iyi ele alınmamış.

### 3.3 Logout Sonrası Nav Stack Temizlenmeli
- Logout eklediğinde dikkat: `session.clear()` + navigate to login yeterli değil. Back stack tamamen temizlenmeli:
  ```kotlin
  navController.navigate(R.id.loginFragment) {
      popUpTo(navController.graph.startDestinationId) { inclusive = true }
  }
  ```
  Aksi hâlde kullanıcı logout sonrası back tuşuyla önceki fragment'a dönebilir.

---

## 4. GÜVENLİK SORUNLARI

### 4.1 RLS (Row Level Security) Kapalı
- Supabase'de tüm tablolar için RLS devre dışı.
- Anon key'e sahip herkes `schedule_entries`, `users`, `lecturers` tablolarını okuyabilir/yazabilir.
- Özellikle `users` tablosunda `password_hash` kolonu herkese açık.
- **Fix (production):** Her tablo için RLS politikaları tanımla: Lecturer kendi satırlarını okuyabilir, admin her şeyi yapabilir.

### 4.2 Anon Key APK'ya Gömülüyor
- `local.properties` üzerinden `BuildConfig.SUPABASE_ANON_KEY` olarak geliyor. Bu doğru yaklaşım — `local.properties` git'e girmemeli.
- Ama Supabase anon key'i halihazırda public tasarım gereği (RLS ile korunması bekleniyor). Gerçek sorun 4.1'deki RLS eksikliği.

### 4.3 SHA-256 Şifre Hash — Güvensiz
- `PasswordHasher.sha256()` kullanılıyor.
- SHA-256 şifre saklamak için uygun değil: rainbow table saldırılarına açık, salt yok.
- **Fix (production):** bcrypt veya Argon2 kullan. Android'de `BCrypt` için `mindrot:jbcrypt` kütüphanesi.

### 4.4 lecturerId -1 Default Değeri
- Login'de `repo.getLecturerByUserId()` null dönerse:
  ```kotlin
  session.lecturerId = lecturer?.id ?: -1
  ```
- Sonrasında `LecturerHomeViewModel.load()` → `scheduleRepo.getEntriesForLecturer(-1)` çağrılıyor.
- Bu Supabase'de geçersiz sorgu yapıyor; hata mesajı "Lecturer profile not found." — ama bu hata hiç loglanmıyor.
- **Fix:** `lecturerId == -1` ise SessionManager bunu "geçersiz" olarak işaretlemeli ve `LecturerHomeViewModel`'de erken catch yapılmalı.

---

## 5. KOD KALİTESİ SORUNLARI

### 5.1 `runBlocking` in `awaitClose` — Thread Bloklama
`CourseRepository` ve `ScheduleRepository`'de:
```kotlin
awaitClose { kotlinx.coroutines.runBlocking { client.realtime.removeChannel(channel) } }
```
- `awaitClose` lambda'sı main thread'de çalışır. `runBlocking` burada main thread'i bloklar.
- Potansiyel ANR (Application Not Responding) riski.
- **Fix:**
  ```kotlin
  awaitClose {
      val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
      scope.launch { client.realtime.removeChannel(channel) }
  }
  ```

### 5.2 Repository'ler ViewModel'de Inline Instantiate Ediliyor
Her ViewModel'de:
```kotlin
private val courseRepo    = CourseRepository()
private val lecturerRepo  = LecturerRepository()
```
- DI (Dependency Injection) yok. Test edilemez. Mock inject edilemiyor.
- Her ViewModel kendi bağımlılıklarını yaratıyor.
- **Fix (ideal):** Hilt veya Koin. **Fix (minimal):** `ViewModelFactory` + constructor injection.

### 5.3 Realtime Flow'lar Kullanılmıyor
- `CourseRepository.observeCourses()` — Flow yazılmış ama hiçbir Fragment collect etmiyor.
- `ScheduleRepository.observeSchedule()` — aynı durum.
- Realtime kanal açılıp kapatılmadığı için gereksiz WebSocket bağlantısı tüketilebilir.
- **Fix:** Ya bu flow'ları Assignment/AdminCalendar fragment'larında kullan, ya da kullanmıyorsan kodu sil (dead code).

### 5.4 `SupabaseClient` Lifecycle Yönetimi Yok
- `SupabaseClient` singleton `object`. Realtime bağlantısı uygulama boyunca açık kalıyor.
- `onDestroy()` veya `Application.onTerminate()` içinde bağlantı kapatılmıyor.
- **Fix:** `Application` subclass'ında `SupabaseClient.client.close()` çağır.

### 5.5 AndroidViewModel vs ViewModel Tutarsızlığı
- `LoginViewModel(app: Application) : AndroidViewModel(app)` ✓ (Application context gerekiyor — SessionManager için)
- `LecturerHomeViewModel(app: Application) : AndroidViewModel(app)` ✓
- `AdminHomeViewModel : ViewModel()` — Repository'ler context almıyor ✓
- `AssignmentViewModel : ViewModel()` — Tutarlı ✓
- `CalendarViewModel : ?` — Ne kullandığını kontrol et. SessionManager kullanıyorsa AndroidViewModel olmalı.

### 5.6 `CancellationException` Rethrow Tutarsız
Bazı ViewModel'lerde:
```kotlin
if (e is kotlinx.coroutines.CancellationException) throw e
```
Bu doğru. Ama fully qualified name kullanmak yerine import edilmeli. Minör.

### 5.7 Adapter'lar Fragment İçinde Tanımlanmış
- `SimpleTextAdapter`, `ClassroomAdapter`, `ScheduleEntryAdapter` — hepsi kendi Fragment dosyasının altında tanımlanmış.
- Küçük projeler için tolere edilebilir ama ayrı dosyalara taşımak daha temiz.

---

## 6. MİMARİ SORUNLAR

### 6.1 DI (Dependency Injection) Yok
- Her ViewModel kendi repository instance'larını oluşturuyor.
- Repository'ler interface değil concrete class — swap edilemiyor, mock edilemiyor.
- **Minmal fix:** Repository'leri singleton companion object veya Application-level holder'da tut. **Doğru fix:** Hilt.

### 6.2 Error Handling Granülaritesi Düşük
- Tüm hatalar `e.message ?: "Fallback message"` ile gösteriliyor.
- Network hatası ile double-booking hatası aynı şekilde işleniyor (sadece mesaj değişiyor).
- HTTP status code'larına göre özel handling yok (401 Unauthorized, 409 Conflict vb.).

### 6.3 No Repository Interface / Abstraction
- `CourseRepository`, `LecturerRepository` vs. concrete class'lar. Interface yok.
- Test edilebilirlik sıfır — Unit test yazmak için Supabase gerçekten çalışmak zorunda.

---

## 7. TEST EKSİKLİĞİ

- Unit test yok (`test/` dizini boş veya yok).
- `PasswordHasher.sha256()` test edilmemiş.
- `CredentialGenerator.generateUsername()` / `generatePassword()` test edilmemiş.
- `ScheduleRepository.assignEntry()` double-booking logic test edilmemiş.
- UI/Instrumentation test yok.

---

## 8. TESLİM EDİLECEKLER EKSİK

- Demo video yok.
- Supabase kurulum adımları (RLS disable SQL, schema SQL) tek bir yerde dokümante edilmemiş.
- Test kullanıcı bilgileri (admin username/password) proje içinde yazılı değil.
- `supabase/README.md` var ama schema creation SQL'i eksik (sadece RLS disable var).

---

## ÖNCELİKLİ FİX SIRASI

| Öncelik | Sorun | Etki |
|---------|-------|------|
| 🔴 P0 | Logout yok | Kullanıcı oturumu kapatamıyor |
| 🔴 P0 | SimpleTextAdapter "None" bug | Boş listeler hiç görünmüyor |
| 🔴 P0 | activity_main.xml constraint bug | Lecturer'da içerik bottom nav'a giriyor |
| 🔴 P0 | AdminCalendarFragment retry listener yok | Hata durumunda recovery imkansız |
| 🟠 P1 | DataFragment stub | Admin veri giremez |
| 🟠 P1 | Assignment silinemez | Yanlış atama düzeltilemiyor |
| 🟠 P1 | Classroom departmanı atanamaz | Tüm derslikler departmansız |
| 🟡 P2 | `runBlocking` in `awaitClose` | ANR potansiyeli |
| 🟡 P2 | RLS kapalı | Güvenlik açığı |
| 🟡 P2 | Realtime flow'lar kullanılmıyor | Dead code / gereksiz kaynak |
| 🟢 P3 | DI / test infrastructure | Teknik borç |
| 🟢 P3 | SHA-256 → bcrypt | Güvenlik iyileştirme |
