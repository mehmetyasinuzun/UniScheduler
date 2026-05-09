# UniScheduler — Güvenlik Modeli & Erişim Matrisi

> Senior dev review. Hangi rol hangi tabloya/endpoint'e ne kadar erişebiliyor,
> hangi güvenlik katmanları var, hangi senaryoda ne olur.
>
> Bu doküman `schema.sql` v2 (production) ile uyumludur.

---

## 1. Aktörler

| Aktör | Nasıl tanınıyor | Kullandığı key |
|-------|-----------------|----------------|
| **Anonymous** | Henüz login olmamış | `SUPABASE_ANON_KEY` |
| **Authenticated User** | Supabase Auth JWT | `SUPABASE_ANON_KEY` + JWT |
| **Admin** | `users.role = 'admin'` (org-scoped) | aynı |
| **Lecturer** | `users.role = 'lecturer'` (org-scoped) | aynı |
| **Super-admin** | Web panel `ADMIN_PASSWORD` ile login | `SUPABASE_SERVICE_KEY` (RLS bypass) |

**Önemli:** Super-admin kimliği şu anda Supabase Auth'a bağlı **DEĞİL** — paneldeki `ADMIN_PASSWORD` (env var) ile token tabanlı session oluşturuluyor. `super_admins` tablosu yalnızca metadata + audit için. Bu, panel kompromise olduğunda RLS bypass etme riskini panel sunucusuna sınırlandırır.

---

## 2. Erişim Matrisi (Tablo × Rol × İşlem)

| Tablo | Anon | Lecturer | Admin (kendi org'u) | Admin (başka org) | Super-admin |
|-------|------|----------|---------------------|-------------------|-------------|
| `organizations`        | ❌ | 👁 (kendi org'u) | 👁 (kendi org'u) | ❌ | ✏️ tam |
| `org_settings`         | ❌ | 👁 | ✏️ tam | ❌ | ✏️ tam |
| `users`                | ❌ | 👁 (kendi + org) ✏️ (sadece kendi) | ✏️ tam (kendi org) | ❌ | ✏️ tam |
| `departments`          | ❌ | 👁 | ✏️ tam | ❌ | ✏️ tam |
| `lecturers`            | ❌ | 👁 (org içi) | ✏️ tam | ❌ | ✏️ tam |
| `courses`              | ❌ | 👁 | ✏️ tam | ❌ | ✏️ tam |
| `classrooms`           | ❌ | 👁 | ✏️ tam | ❌ | ✏️ tam |
| `offerings`            | ❌ | 👁 | ✏️ tam | ❌ | ✏️ tam |
| `schedule_entries`     | ❌ | 👁 (sadece okur — kendi schedule'ını değiştiremez) | ✏️ tam | ❌ | ✏️ tam |
| `lecturer_availability`| ❌ | ✏️ (sadece kendi) | ✏️ tam (org içi) | ❌ | ✏️ tam |
| `client_error_logs`    | ❌ | ➕ (kendi org adına insert) | 👁 (org + mobile only) ➕ | ❌ | ✏️ tam |
| `audit_log`            | ❌ | ❌ | 👁 (kendi org) | ❌ | ✏️ tam |
| `login_attempts`       | ❌ | 👁 (sadece kendi username) | 👁 (kendi username) | ❌ | ✏️ tam |
| `super_admins`         | ❌ | ❌ | ❌ | ❌ | ✏️ tam (panel UI üzerinden) |

**Lejant:** ❌ erişim yok • 👁 read-only • ➕ INSERT-only • ✏️ tam CRUD

### Kritik Senaryolar

**Senaryo: Bir admin başka org'un verisini görmeye çalışırsa?**
- RLS policy `is_admin() AND org_id = current_org_id()` → kendi org_id'si ≠ hedef org_id → boş set döner.
- API hata atmaz, sadece "kayıt yok" davranır → information leak yok.

**Senaryo: Bir lecturer SQL injection ile başka lecturer'ın schedule'ını silmeye çalışırsa?**
- Supabase REST API parametrize sorgu kullanır → SQL injection katmanı yok.
- DELETE `schedule_entries` policy'si `is_admin()` gerektiriyor → lecturer için her zaman false → reddedilir.
- Lecturer'ın JWT'sinde `role: lecturer` claim var ama biz tablo seviyesinde kontrol ediyoruz, JWT claim'a güvenmiyoruz.

**Senaryo: APK decompile edilip ANON_KEY çıkarılırsa?**
- ANON_KEY public-by-design — RLS olmadan tehlikeli, RLS açıkken zararsız.
- Saldırgan en fazla **anonymous** olabilir → tabloda hiçbir SELECT geçemez.
- Saldırgan login olmuş bir gerçek kullanıcı ise → o kullanıcının zaten yapabildiklerini yapar, fazlası değil.

**Senaryo: Mobil cihaz kaybolursa?**
- `EncryptedSharedPreferences` (AES-256-GCM, AES-256-SIV) ile session şifreli.
- Cihaz unlock edilebilirse session okunabilir → cihaz parolası UniScheduler güvenliğinin bir parçasıdır.
- Çözüm: kullanıcı yeni cihazdan login → eski JWT iptal değil ama yeni şifre değişimi tüm session'ları geçersiz kılar (Supabase Auth davranışı).

**Senaryo: Admin hesabı ele geçirilirse?**
- Saldırgan o org'un tüm verisini silebilir/okuyabilir.
- Ama: `audit_log` her DELETE'i kayıt altına alır + `actor_id` doldurulur → forensic mümkün.
- Ek katman: panel'den admin şifresi sıfırlanabilir → token revoke (Supabase Auth davranışı).

**Senaryo: Service role key sızarsa?**
- 🔴 **KRİTİK** — RLS bypass eder, her tabloyu okuyabilir/yazabilir, auth.users'ı bile yönetebilir.
- Mitigation:
  1. Service key sadece web panel sunucusunda — `local.properties`'te DEĞİL, APK'da DEĞİL.
  2. `.env` git'e girmez (.gitignore).
  3. `NODE_ENV=production` ile zayıf parolalar reddedilir.
  4. `ALLOWED_IPS` set edilmeli — service key kullanan endpoint'lere yalnızca güvenilir IP'ler erişsin.
  5. Supabase Dashboard'dan service key her zaman rotate edilebilir (anlık invalidate).

---

## 3. Güvenlik Katmanları (Defense-in-Depth)

```
                        ┌─────────────────┐
                        │  Saldırgan       │
                        └────────┬─────────┘
                                 │
              ┌──────────────────▼──────────────────┐
       1.     │  TLS (HTTPS, Supabase + panel)      │  ← Cleartext kapalı
              └──────────────────┬──────────────────┘
                                 │
              ┌──────────────────▼──────────────────┐
       2.     │  Mobile: certificate validation,    │  ← network_security_config.xml
              │  no cleartext, no debuggable        │
              └──────────────────┬──────────────────┘
                                 │
              ┌──────────────────▼──────────────────┐
       3.     │  Supabase Auth (JWT, rate limit)    │  ← Email confirm off, but
              │                                       │     login_attempts tablo
              └──────────────────┬──────────────────┘
                                 │
              ┌──────────────────▼──────────────────┐
       4.     │  RLS (table-level org isolation)    │  ← Schema.sql §17–20
              └──────────────────┬──────────────────┘
                                 │
              ┌──────────────────▼──────────────────┐
       5.     │  CHECK constraints (data validity)  │  ← Username format,
              │                                       │     time format, etc.
              └──────────────────┬──────────────────┘
                                 │
              ┌──────────────────▼──────────────────┐
       6.     │  Trigger-level invariants           │  ← prevent_schedule_overlap
              └──────────────────┬──────────────────┘
                                 │
                         ┌───────▼────────┐
                         │   Postgres DB   │
                         └─────────────────┘
```

### Yan Katmanlar (panel & sürdürülebilirlik)

- **Audit log trigger** her INSERT/UPDATE/DELETE'i `audit_log`'a yazar → "kim sildi?" sorusu cevaplanır.
- **Login attempts** brute-force tespiti için username bazında.
- **Soft delete** (`deleted_at`) — silinen kayıtların metadata'sı saklanır.
- **Updated_at trigger** — ne zaman değişti audit edilebilir.
- **Citext email** — email karşılaştırması case-insensitive, "ali@... vs Ali@..." aynı kabul edilir.

---

## 4. Mobil App Güvenlik Sertleştirmeleri (zaten kuruldu)

| Önlem | Konum |
|-------|-------|
| `allowBackup="false"` | AndroidManifest.xml |
| `dataExtractionRules` (Android 12+) | xml/data_extraction_rules.xml |
| `usesCleartextTraffic="false"` | AndroidManifest.xml |
| `network_security_config.xml` (HTTPS only) | xml/network_security_config.xml |
| `EncryptedSharedPreferences` (AES-256-GCM session) | SessionManager.kt |
| ProGuard/R8 minify + obfuscate | build.gradle.kts release |
| `Log.d/v/i` strip in release | proguard-rules.pro |
| 12-char SecureRandom passwords | CredentialGenerator.kt |
| versionCode-based prefs migration | App.kt |
| `META-INF/services` merge (POI providers) | build.gradle.kts packaging |

---

## 5. Web Panel Güvenlik Sertleştirmeleri

| Önlem | Konum |
|-------|-------|
| Session cookie 8 saat TTL | server.js |
| Rate limit (5 login/15min, 100 API/min) | server.js |
| `helmet` HTTP headers | server.js |
| CORS: production'da strict origin | server.js (`isProduction` block) |
| `IP allowlist` (opsiyonel ama önerilen) | server.js (`ALLOWED_IPS` env) |
| Default password rejection (NODE_ENV=production) | server.js |
| Multer file size cap (5 MB) | server.js |
| `:orgId` paramı NaN reddi | server.js (`requireValidOrgId`) |
| Centralised error middleware (no SQL leak) | server.js |
| Graceful shutdown (SIGTERM) | server.js |
| Healthcheck endpoint `/healthz` | server.js |

---

## 6. Üretime Çıkış Öncesi Son Kontrol

Aşağıdakilerin **TÜM**'ü ✅ olmadan production'a gitme.

### Database
- [ ] `schema.sql` çalıştırıldı
- [ ] Auth → Confirm email **kapalı**
- [ ] En az bir organization oluşturuldu
- [ ] En az bir admin (`auth.users` + `public.users` row eşleşmesi) oluşturuldu
- [ ] Test: Admin ile login ol → kendi org verisini gör; logout → başka org admini ile login → diğer org verisi GÖZÜKMÜYOR

### Mobile App
- [ ] `local.properties` production URL + ANON_KEY (NOT service_key!)
- [ ] Production keystore oluşturuldu, ŞİFRESİ + DOSYA yedeklendi (2+ lokasyon)
- [ ] `versionCode` ve `versionName` doğru bumplandı
- [ ] `./gradlew assembleRelease` temiz (ProGuard warning'ları sadece bilinen `java.awt.geom`)
- [ ] AAPT2 dump kontrolü: `allowBackup=0`, `usesCleartextTraffic=0`, security config ref edili
- [ ] Telefon testi: Excel import + PDF export + bildirim hatırlatma çalışıyor

### Web Panel
- [ ] Ayrı sunucuda (VPS / Docker / Heroku) — APK ile aynı paketten gitmiyor
- [ ] HTTPS arkasında (sertifika geçerli, HSTS önerilir)
- [ ] `NODE_ENV=production`
- [ ] `ADMIN_PASSWORD` ≥ 16 karakter random (default değil)
- [ ] `ALLOWED_IPS` set (firewall layer + bu uygulama layer = iki katman)
- [ ] `ALLOWED_ORIGINS` panel'in kendi URL'i (CORS strict)
- [ ] Service role key sadece bu sunucuda, başka hiçbir yerde
- [ ] `.env` git'te değil (gitignore kontrol)
- [ ] `/healthz` test → `{ ok: true }` dönüyor
- [ ] Logout testi: super-admin logout sonrası eski token çalışmıyor

### İzleme
- [ ] Supabase Logs panelinden 24h sonrası incelendi → suspicious activity yok
- [ ] `audit_log` tablosu doluyor (admin bir test deletion yapsın → audit'e düşsün)
- [ ] `client_error_logs`'a panel + mobil hatalar düşüyor
