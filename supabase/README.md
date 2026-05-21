# UniScheduler — Supabase Setup

> İki kurulum yolu var:
> 1. **Quick install** — sıfırdan yeni proje için, [`schema.sql`](schema.sql)'i tek seferde çalıştır
> 2. **Production deploy** — canlı veri varken sürüm güncellemek için [dbmate](https://github.com/amacneil/dbmate) ile versiyonlu migration'lar
>
> Eski parçalı dosyalar [`migrations/legacy/`](migrations/legacy/) altında arşivde. **Çalıştırmayın.**

---

## 1. Yeni Proje Kurulumu (Quick Install)

### 1.1 Supabase Projesi Oluştur
1. [supabase.com](https://supabase.com) → **New Project**
2. Bir proje adı belirleyin (örn. `unischeduler-prod`)
3. Güçlü bir DB parolası seçin, parola yöneticisine kaydedin
4. Kullanıcılarınıza en yakın region (`eu-central-1` Türkiye için iyi)
5. ~2 dakika provision bekleyin

### 1.2 Şemayı Yükle
1. Dashboard → **SQL Editor → New query**
2. [`schema.sql`](schema.sql) dosyasının tamamını yapıştır
3. **Run**

Bu komut şunları kurar:
- 11 tablo (organizations, org_settings, users, departments, lecturers, courses, classrooms, offerings, schedule_entries, lecturer_availability, client_error_logs, audit_log, login_attempts, super_admins)
- Org-scoped sorgular için tüm indeksler
- 5 SECURITY DEFINER helper fonksiyon (`current_org_id`, `current_user_role`, `is_admin`, `is_lecturer`, `current_lecturer_id`)
- Her tabloda Row-Level Security politikaları (multi-tenant izolasyonu)
- Realtime publication (schedule_entries, courses, lecturer_availability, offerings)
- `prevent_schedule_overlap` trigger (race-condition korumalı çakışma engeli)
- `admin_reset_lecturer_password` RPC (mobile admin'in service_role gerektirmeden hoca şifresi sıfırlaması)

> ⚠ Script `DROP TABLE IF EXISTS` ile başlar ve `auth.users` içindeki `*@unischeduler.app` satırları siler. **Mevcut tüm veri silinir.** Sadece yeni projede çalıştırın veya önce yedek alın.

### 1.3 İlk Organizasyonu Ekle
```sql
INSERT INTO organizations (name, code) VALUES ('Default University', 'default') RETURNING id;
```

### 1.4 İlk Admin'i Oluştur
**Dashboard → Authentication → Users → Add user**
- Email: `admin@unischeduler.app` (synthetic — gerçek e-posta gerekmez)
- Password: güçlü bir parola
- **Auto-confirm user**: ✅

UUID'yi kopyalayın, sonra:
```sql
INSERT INTO public.users (id, org_id, username, role, must_change_password)
VALUES ('<auth-user-uuid>', 1, 'admin', 'admin', true);
```

### 1.5 API Anahtarları
**Settings → API**:
- **Project URL** → `local.properties` içinde `SUPABASE_URL`
- **anon / public key** → mobile için `SUPABASE_ANON_KEY`
- **service_role key** → sadece [super-admin paneli](../super-admin-paneli/) için, **mobile APK'ya asla koyma**

---

## 2. Production Deploy — dbmate ile Versiyonlu Migration

> Quick install yeni projeye uygundur ama canlı veri varken `DROP TABLE` yapamayız. Şema değişiklikleri için **dbmate** kullanıyoruz — single Go binary, `DATABASE_URL` env'ine bağlanır, basit `up`/`down`/`new` komutları sunar.

### 2.1 dbmate Kurulumu

```bash
# macOS
brew install dbmate

# Linux
curl -fsSL -o /usr/local/bin/dbmate \
    https://github.com/amacneil/dbmate/releases/latest/download/dbmate-linux-amd64
chmod +x /usr/local/bin/dbmate

# Windows (PowerShell)
iwr https://github.com/amacneil/dbmate/releases/latest/download/dbmate-windows-amd64.exe `
    -OutFile $env:USERPROFILE\bin\dbmate.exe
```

Doğrulama:
```bash
dbmate --version
```

### 2.2 Bağlantı Konfigürasyonu

`supabase/migrations/.env` oluşturun (gitignored):
```bash
cp supabase/migrations/.dbmate.example.env supabase/migrations/.env
```

`DATABASE_URL`'i Supabase Settings → Database → **Connection string (URI)** kısmından kopyalayın. Production için **pooled connection** (port 6543) önerilir:

```
DATABASE_URL=postgresql://postgres.[REF]:[PASSWORD]@aws-0-eu-central-1.pooler.supabase.com:6543/postgres
```

### 2.3 Baseline'ı İşaretle

İlk dbmate kullanımında **schema.sql** ile zaten kurulmuş veritabanına dbmate'i tanıtmak gerekir. Bunu **bir defalık** yapın:

```bash
cd supabase
dbmate --migrations-dir migrations status   # bakım için: 1 pending görmeli (baseline)
dbmate --migrations-dir migrations up       # baseline migration uygulanır (no-op marker)
```

Veya manuel SQL ile (bağlantı yoksa):
```sql
CREATE TABLE IF NOT EXISTS schema_migrations (version VARCHAR(255) PRIMARY KEY);
INSERT INTO schema_migrations (version) VALUES ('20260521000000') ON CONFLICT DO NOTHING;
```

Bu, dbmate'in "baseline applied, devam et" demesini sağlar.

### 2.4 Yeni Migration Yazma

```bash
cd supabase
dbmate new add_audit_request_id
# → migrations/20260601120000_add_audit_request_id.sql oluşturuldu
```

Dosyayı açın, iki bölümü doldurun:
```sql
-- migrate:up
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS request_id TEXT;
CREATE INDEX IF NOT EXISTS idx_audit_request_id ON audit_log(request_id);

-- migrate:down
DROP INDEX IF EXISTS idx_audit_request_id;
ALTER TABLE audit_log DROP COLUMN IF EXISTS request_id;
```

### 2.5 Production'a Uygula

```bash
# Önce: backup al!  Dashboard → Database → Backups
# veya: pg_dump $DATABASE_URL > backup-$(date +%F).sql

# Dry-run — uygulanacak migration'ları listele
dbmate --migrations-dir migrations status

# Uygula
dbmate --migrations-dir migrations up

# Sorun varsa rollback
dbmate --migrations-dir migrations rollback
```

### 2.6 Best Practice'ler

**✅ Her migration'da:**
- `IF NOT EXISTS` / `IF EXISTS` kullanın (idempotent olsun, yarıda kalırsa devam edebilesiniz)
- `migrate:down` boş bırakmayın — geri alma yolu olmalı
- Büyük tablo değişikliklerinde `ALTER TABLE ... ADD COLUMN ... DEFAULT NULL` (DEFAULT değer atamak prod'da full table rewrite tetikler)
- INDEX'leri `CONCURRENTLY` ile ekleyin (production'da yazma kilitlemez):
  ```sql
  CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_xx ON foo(bar);
  ```

**❌ Asla:**
- Mevcut migration dosyasının içeriğini DEĞİŞTİRMEYİN. Yeni migration yazın.
  (dbmate hash'lere bakmaz ama takım üyelerinin DB'leri uyumsuz hale gelir.)
- Production'da `dbmate drop` (TÜM veriyi siler) çalıştırmayın.
- Migration'ı kontrol etmeden `dbmate up` yapmayın — `status` ile önce ne uygulanacağını görün.

### 2.7 CI/CD Entegrasyonu

`.github/workflows/deploy-migrations.yml` örneği (ileride):
```yaml
- name: Apply migrations
  env:
    DATABASE_URL: ${{ secrets.SUPABASE_DATABASE_URL }}
  run: dbmate --migrations-dir supabase/migrations up
```

GitHub Secrets'a `SUPABASE_DATABASE_URL` ekledikten sonra tag-based deploy mümkün.

---

## 3. Tablo Özeti

| Tablo | Amaç |
|---|---|
| `organizations` | Multi-tenant kök (her kurum bir satır) |
| `org_settings` | Org-bazlı yapılandırma (zaman dilimi, mesai saatleri, aktif günler) |
| `users` | Auth profili (`auth.users` ile 1:1) — rol + org bağı |
| `departments` | Bölüm listesi (org'a scoped) |
| `lecturers` | Hoca profilleri (`users` + `departments` FK) |
| `courses` | Ders kataloğu (org'a scoped) |
| `classrooms` | Fiziksel/lab sınıflar (org'a scoped) |
| `offerings` | Açılan dersler (akademik yıl + dönem) |
| `schedule_entries` | Nihai program: offering × hoca × derslik × gün × saat |
| `lecturer_availability` | Hocaların müsait/meşgul saatleri |
| `client_error_logs` | Mobile + panel hata raporları |
| `audit_log` | Trigger ile her INSERT/UPDATE/DELETE jsonb diff'i |
| `login_attempts` | CTI için tüm giriş denemeleri (başarılı + başarısız) |
| `super_admins` | Panel sahibi listesi (sadece audit kaydı için) |

## 4. RLS Hızlı Referans

- **Herkes** sadece kendi organizasyonunu görür (`current_org_id()`).
- **Admin'ler** kendi org'larında her tabloya yazabilir.
- **Hoca'lar** sadece kendi `lecturer_availability` satırlarını yazabilir.
- **service_role** anahtarı (sadece süper-admin paneli kullanır) her politikayı bypass eder — DB root parolası gibi koruyun.

---

## 5. Yedekleme Stratejisi

**Supabase otomatik backup:**
- Free tier: 7 günlük günlük backup
- Pro tier: 7 gün + Point-in-Time Recovery (PITR)

**Manuel yedek:**
```bash
pg_dump $DATABASE_URL > backup-$(date +%Y%m%d).sql
```

**Geri yükleme:**
```bash
# Önce mevcut DB'yi temizle (DİKKAT)
psql $DATABASE_URL -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
psql $DATABASE_URL < backup-2026-05-21.sql
```

**Migration sonrası verification:**
```bash
dbmate --migrations-dir migrations status  # tüm migration applied göstermeli
```
