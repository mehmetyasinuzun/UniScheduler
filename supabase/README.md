# UniScheduler — Supabase Kurulum Kılavuzu

Bu klasördeki SQL dosyalarını Supabase SQL Editor'de **sırasıyla** çalıştırarak veritabanını hazırlayın.

---

## Ön Gereksinimler

- [x] Supabase hesabı oluşturulmuş
- [x] Yeni proje oluşturulmuş (Frankfurt bölgesi önerilir)
- [x] `SUPABASE_URL` ve `SUPABASE_ANON_KEY` değerleri `local.properties`'e eklenmiş

---

## ⚠️ ÖNEMLİ: Auth Ayarı

Uygulama, hoca hesaplarını programatik olarak oluşturuyor (signUp). Supabase varsayılan olarak email onayı ister — bu, auto-generated hesapların çalışmasını engeller.

### Email Onayını Kapatın:

1. **Supabase Dashboard** → **Authentication** → **Providers**
2. **Email** provider'a tıklayın
3. **"Confirm email"** → **KAPATIN** (toggle OFF)
4. **Save** butonuna tıklayın

> Bu ayar kapalı olmazsa, uygulama içinden oluşturulan lecturer hesapları giriş yapamaz!

---

## SQL Dosyalarını Çalıştırma Sırası

Supabase Dashboard → **SQL Editor** → **New Query** ile her dosyayı ayrı ayrı yapıştırıp çalıştırın.

| Sıra | Dosya | Açıklama |
|------|-------|----------|
| 1️⃣ | `01_tables.sql` | Tüm tabloları oluşturur (profiles, departments, courses, lecturers, vb.) |
| 2️⃣ | `02_rls_policies.sql` | Tüm tablolarda RLS'yi etkinleştirir + rol bazlı erişim politikaları |
| 3️⃣ | `03_functions_triggers.sql` | Trigger'lar (auto profil, dual-routing) + yardımcı fonksiyonlar |
| 4️⃣ | `04_storage.sql` | Storage bucket'ları (excel-uploads, exports) + erişim politikaları |
| 5️⃣ | `05_realtime.sql` | Realtime publication konfigürasyonu (anlık bildirimler) |
| 6️⃣ | `06_seed.sql` | İlk admin kullanıcı oluşturma (talimatları dosya içinde) |

> **Her dosyayı tek seferde yapıştırıp çalıştırın.** Hata alırsanız bir önceki adımı kontrol edin.

---

## İlk Admin Kullanıcı Oluşturma

SQL dosyaları çalıştırıldıktan sonra:

### Adım 1: Auth'ta Kullanıcı Oluşturma
1. **Authentication** → **Users** → **Add User** → **Create New User**
2. Email: `admin@unischeduler.local` (veya istediğiniz bir email)
3. Password: Güçlü bir şifre belirleyin
4. ✅ **Auto Confirm User** kutusunu işaretleyin
5. **Create User** butonuna tıklayın

### Adım 2: Admin Rolü Atama
1. Oluşturulan kullanıcının satırındaki **UUID**'yi kopyalayın
2. **SQL Editor** → Yeni sorgu:

```sql
INSERT INTO public.profiles (id, name, surname, role)
VALUES (
    'BURAYA-UUID-YAPISTIRINIZ',
    'Admin',
    'User',
    'ADMIN'
)
ON CONFLICT (id) DO UPDATE SET role = 'ADMIN';
```

3. UUID'yi yapıştırıp çalıştırın

### Adım 3: Uygulamada Giriş
- Email: `admin@unischeduler.local`
- Password: Belirlediğiniz şifre
- Login sonrası 4 tab görünecek (Home, Calendar, Data, Settings)

---

## Doğrulama Kontrol Listesi

SQL dosyaları başarıyla çalıştıktan sonra kontrol edin:

### Tablolar (Table Editor'den)
- [ ] `profiles` — auth.users ile ilişkili
- [ ] `departments`
- [ ] `courses`
- [ ] `lecturers`
- [ ] `course_lecturers`
- [ ] `schedule_configs`
- [ ] `schedule_assignments`
- [ ] `availability_slots`
- [ ] `schedule_drafts`
- [ ] `change_requests`
- [ ] `import_logs`

### RLS (Authentication → Policies)
- [ ] Her tabloda RLS **etkin** (kırmızı "RLS disabled" yazmamalı)
- [ ] Her tabloda en az 1 policy tanımlı

### Storage (Storage sekmesi)
- [ ] `excel-uploads` bucket mevcut
- [ ] `exports` bucket mevcut

### Functions (Database → Functions)
- [ ] `handle_new_user` — Auth trigger
- [ ] `get_user_role` — RLS helper
- [ ] `get_user_department_id` — RLS helper
- [ ] `update_request_overall_status` — Dual-routing trigger
- [ ] `update_updated_at` — Timestamp trigger
- [ ] `get_department_stats` — İstatistik
- [ ] `upsert_availability` — Toplu müsaitlik
- [ ] `apply_draft_assignments` — Draft onay
- [ ] `make_admin` — Admin yetkilendirme
- [ ] `check_cross_department_conflict` — Çakışma kontrolü

### Realtime
- [ ] **Database** → **Replication** → `schedule_assignments`, `schedule_drafts`, `change_requests`, `availability_slots` tabloları listelenmiş

---

## Veritabanı Şeması Özeti

```
profiles ──FK──→ auth.users (1:1)
    └── department_id → departments

departments
    ├── courses (1:N)
    ├── lecturers (1:N)
    ├── schedule_configs (1:1)
    └── schedule_drafts (1:N)

lecturers
    ├── profile_id → profiles
    ├── availability_slots (1:N)
    ├── schedule_assignments (1:N)
    └── change_requests (1:N)

courses
    ├── course_lecturers (M:N → lecturers)
    └── schedule_assignments (1:N)
```

---

## Sorun Giderme

| Sorun | Çözüm |
|-------|--------|
| `relation "auth.users" does not exist` | SQL Editor'de değil, Table Editor'de oluşturmaya çalışıyorsunuz. SQL Editor kullanın. |
| `permission denied for table profiles` | RLS politikaları henüz oluşturulmamış. `02_rls_policies.sql`'i çalıştırın. |
| `duplicate key value violates unique constraint` | Dosya zaten çalıştırılmış. `IF NOT EXISTS` / `ON CONFLICT` ile güvenli. |
| Lecturer giriş yapamıyor | Authentication → Providers → Email → "Confirm email" **KAPAL** mı kontrol edin. |
| Realtime çalışmıyor | `05_realtime.sql` çalıştırıldı mı? Dashboard → Database → Replication'dan kontrol edin. |
| Storage'a erişilemiyor | `04_storage.sql` çalıştırıldı mı? Storage → Policies kontrol edin. |
