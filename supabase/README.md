# UniScheduler — Supabase Setup

**TEK DOSYA, TEK RUN.** [`schema.sql`](schema.sql)'i Supabase SQL Editor'a
yapıştır, **Run** butonuna bas — proje çalışmaya hazır. Yama / migration
zinciri yok; her şey tek dosyada idempotent.

Opsiyonel: [`functions/bulk-create-lecturers`](functions/bulk-create-lecturers/)
Edge Function'ını da deploy et → 350 hocalık bulk import 9 dakika yerine
30 saniye sürer.

---

## Hızlı Kurulum (5 dakika)

### 1. Supabase Projesi Aç
1. [supabase.com](https://supabase.com) → **New Project**
2. Proje adı + güçlü DB parolası + en yakın region
3. ~2 dakika provision bekle

### 2. Şemayı Yükle
1. Dashboard → **SQL Editor** → **New query**
2. [`schema.sql`](schema.sql) dosyasının **tamamını** yapıştır
3. **Run**

Beklenen sonuç (alt panelde):
```
status                          tables  rls_policies  triggers  functions
UniScheduler schema installed   14      32+           30+       10+
```

Bu tek script şunları kurar:
- **14 tablo** — organizations, org_settings, users, departments, lecturers, courses, classrooms, offerings, schedule_entries, lecturer_availability, client_error_logs, audit_log, login_attempts, super_admins
- **32+ RLS policy** — multi-tenant izolasyon (her admin sadece kendi org'unu görür)
- **5 SECURITY DEFINER helper** — `current_org_id`, `current_user_role`, `is_admin`, `is_lecturer`, `current_lecturer_id`
- **3 SECURITY DEFINER RPC** — admin işlemleri için:
  - `admin_reset_lecturer_password(lecturer_id, new_password)` — mobile admin'in service_role gerektirmeden hoca şifresi sıfırlaması
  - `generate_unique_lecturer_username(first, last)` — global-unique username üretimi (cross-org collision + Auth orphan koruması)
- **`prevent_schedule_overlap` trigger** — TOCTOU race koruması
- **Audit trigger'ları** — users / lecturers / courses / classrooms / departments / offerings / schedule_entries değişiklikleri otomatik kayıt
- **Realtime publication** — schedule_entries, courses, offerings, lecturer_availability (mobile uygulamada canlı güncelleme)

> ⚠ Script **DROP TABLE IF EXISTS** ile başlar ve `auth.users` içindeki
> `*@unischeduler.app` synthetic kullanıcılarını siler. Canlı veri varsa
> ÖNCE yedek al.

### 3. İlk Admin'i Oluştur

Dashboard → **Authentication** → **Users** → **Add user**:
- Email: `superadmin@unischeduler.app`
- Password: güçlü bir parola
- ✅ Auto-confirm user

Kaydet → açılan satırdan **UUID**'yi kopyala.

SQL Editor'da:
```sql
-- Organizasyon yarat
INSERT INTO organizations (name, code) VALUES ('Sivas BTÜ', 'SBTU') RETURNING id;
-- → dönen id (örn. 1) aşağıdaki insert'in org_id'sine yazılır

-- Admin profilini bağla
INSERT INTO public.users (id, org_id, username, role, must_change_password)
VALUES ('<UUID-buraya>', 1, 'superadmin', 'admin', false);

-- Opsiyonel: panel super_admins kaydı
INSERT INTO super_admins (username) VALUES ('superadmin');
```

### 4. Auth Confirm Email kapat
Dashboard → **Authentication** → **Providers** → **Email**
→ "Confirm email" → **OFF** + Save.

(Mobile uygulama email doğrulama beklemediği için açık kalırsa lecturer
girişleri patlatır.)

### 5. Mobile + Panel
- **Mobile:** APK'yı telefona yükle, `superadmin` + verdiğin parola ile giriş yap.
- **Panel:** `cd super-admin-paneli && npm install && npm start` → http://localhost:3000

---

## Edge Function (opsiyonel ama önerilen)

Mobile admin'in 350+ hocayı Excel ile toplu eklemesi durumunda Supabase
Auth'un 30 createUser/dk rate-limit'i devreye girer ve import 9 dakika sürer.
Edge Function bunu **30 saniyeye** indirir.

### Deploy (Dashboard üzerinden, CLI gerekmez)

1. Dashboard → **Edge Functions** → **Create a new function**
2. Function adı: **`bulk-create-lecturers`** (bu isim sabit, mobile kodu bekliyor)
3. Editor'a [`functions/bulk-create-lecturers/index.ts`](functions/bulk-create-lecturers/index.ts) içeriğini yapıştır
4. **Deploy function** → "Status: Active" yeşil

Detaylı talimat: [`functions/README.md`](functions/README.md)

### Doğrulama
```bash
# 401 dönmeli (Authorization eksik) — doğru davranış
curl -X POST https://<project-ref>.supabase.co/functions/v1/bulk-create-lecturers
# → {"error":"Authorization header eksik."}
```

> Edge Function deploy edilmemişse mobile app yine çalışır — sadece eski
> yavaş yola düşer (~9 dakika). Yani önce APK yüklemen sorun yok, Edge'i
> sonra deploy edebilirsin.

---

## Bağlantı Anahtarları

Dashboard → **Project Settings** → **API**

| Anahtar | Nerede kullanılır | Gizli mi? |
|---|---|---|
| `Project URL` | local.properties `SUPABASE_URL` + panel `.env` | Hayır |
| `anon (public)` key | local.properties `SUPABASE_ANON_KEY` (APK'da gömülü) | Hayır (RLS koruyor) |
| `service_role` key | panel `.env` `SUPABASE_SERVICE_KEY` + Edge Function ortamı | **EVET — asla repo'ya koyma** |

`local.properties` ve `.env` zaten `.gitignore`'da; commit etmiyoruz.

---

## Eski Migration'lar (Tarihsel)

[`migrations/legacy/`](migrations/legacy/) klasöründe önceki kurulum
versiyonları arşivlenmiş. **Çalıştırmayın** — `schema.sql` zaten her
şeyi içeriyor.

| Dosya | İçerik (artık schema.sql'de) |
|---|---|
| `001_schema.sql` → `005_error_log_source.sql` | İlk RLS denemeleri (artık 17.–20. bölümler) |
| `20260521000000_baseline.sql` | dbmate baseline marker |
| `20260522000000_fix_login_attempts_rls.sql` | login_attempts RLS (schema.sql bölüm 20) |
| `20260523000000_unique_username_rpc.sql` | RPC ilk sürümü |
| `20260523100000_secure_username_rpc.sql` | RPC güvenlik sıkılaştırma (schema.sql bölüm 22c) |

---

## Mevcut Veri Üzerine Re-Deploy

Aynı `schema.sql`'i mevcut bir proje üzerinde tekrar çalıştırırsan:

1. `DROP TABLE IF EXISTS ... CASCADE` ile tüm tablolar silinir
2. `DELETE FROM auth.users WHERE email LIKE '%@unischeduler.app'` ile
   sentetik kullanıcılar silinir
3. Yeniden temiz şema kurulur

**Yani veri kaybı kesin.** Sadece geliştirme/test ortamında yap. Production
re-deploy için (canlı veri korumayla) ayrı bir migration yazılması gerekir.

---

## Sorun Giderme

| Belirti | Sebep | Çözüm |
|---|---|---|
| Mobile giriş "Profile not found" | `public.users` insert eksik | Adım 3'ü tamamla |
| Mobile giriş "Email not confirmed" | Auth confirm aktif | Adım 4'ü uygula |
| Bulk import 9 dakika sürüyor | Edge Function deploy edilmemiş | Edge Function bölümünü uygula |
| RPC çağrısı "Yetkisiz" | Caller admin değil | `public.users.role='admin'` ata |
| CTI sayfası boş | `login_attempts` policy yok | `schema.sql`'i yeniden çalıştır |
