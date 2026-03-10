# UniScheduler — Supabase API Endpoints

> Tüm endpoint'ler Supabase PostgREST üzerinden otomatik REST olarak sunulur.
> Base URL: `https://<PROJECT_ID>.supabase.co/rest/v1/`
> Auth: `Authorization: Bearer <JWT>` + `apikey: <ANON_KEY>`

---

## Authentication (Supabase Auth)

| İşlem | Method | Endpoint |
|---|---|---|
| Giriş yap | `POST` | `/auth/v1/token?grant_type=password` |
| Kayıt ol | `POST` | `/auth/v1/signup` |
| Çıkış yap | `POST` | `/auth/v1/logout` |
| Session yenile | `POST` | `/auth/v1/token?grant_type=refresh_token` |
| Mevcut kullanıcı | `GET` | `/auth/v1/user` |

---

## profiles

| İşlem | Method | Endpoint | Kısıtlamalar |
|---|---|---|---|
| Kendi profilini getir | `GET` | `/profiles?id=eq.<uuid>` | Herkes kendi profilini görebilir |
| Tüm profilleri getir | `GET` | `/profiles` | Sadece ADMIN |
| Profil güncelle | `PATCH` | `/profiles?id=eq.<uuid>` | Kendi profili veya ADMIN |

---

## departments

| İşlem | Method | Endpoint | Kısıtlamalar |
|---|---|---|---|
| Tüm bölümleri listele | `GET` | `/departments` | Giriş yapmış herkes |
| Bölüm detayı | `GET` | `/departments?id=eq.<id>` | Giriş yapmış herkes |
| Bölüm oluştur | `POST` | `/departments` | Sadece ADMIN |
| Bölüm güncelle | `PATCH` | `/departments?id=eq.<id>` | Sadece ADMIN |
| Bölüm sil | `DELETE` | `/departments?id=eq.<id>` | Sadece ADMIN |

---

## lecturers

| İşlem | Method | Endpoint | Kısıtlamalar |
|---|---|---|---|
| Bölüm bazlı listele | `GET` | `/lecturers?department_id=eq.<id>` | ADMIN, DEPT_HEAD (kendi bölümü), STUDENT, LECTURER |
| Profil ID ile getir | `GET` | `/lecturers?profile_id=eq.<uuid>` | Kendi kaydı veya ADMIN |
| Hoca oluştur/güncelle | `POST` / `PATCH` | `/lecturers` | ADMIN, DEPT_HEAD |
| Hoca sil | `DELETE` | `/lecturers?id=eq.<id>` | Sadece ADMIN |

---

## courses

| İşlem | Method | Endpoint | Kısıtlamalar |
|---|---|---|---|
| Tüm dersleri listele | `GET` | `/courses` | Giriş yapmış herkes |
| Bölüm bazlı listele | `GET` | `/courses?department_id=eq.<id>` | Giriş yapmış herkes |
| Ders oluştur/güncelle | `POST` / `PATCH` | `/courses` | ADMIN, DEPT_HEAD (kendi bölümü) |
| Ders sil | `DELETE` | `/courses?id=eq.<id>` | ADMIN, DEPT_HEAD (kendi bölümü) |

---

## course_lecturers (M:N ilişki)

| İşlem | Method | Endpoint | Kısıtlamalar |
|---|---|---|---|
| Ders-Hoca eşleşmelerini getir | `GET` | `/course_lecturers?course_id=eq.<id>` | Giriş yapmış herkes |
| Hoca atama | `POST` | `/course_lecturers` | ADMIN, DEPT_HEAD |
| Hoca atamasını kaldır | `DELETE` | `/course_lecturers?id=eq.<id>` | ADMIN, DEPT_HEAD |

---

## schedule_configs

| İşlem | Method | Endpoint | Kısıtlamalar |
|---|---|---|---|
| Bölüm config'ini getir | `GET` | `/schedule_configs?department_id=eq.<id>` | Giriş yapmış herkes |
| Config oluştur/güncelle | `POST` / `PATCH` | `/schedule_configs` | ADMIN, DEPT_HEAD (kendi bölümü) |

**Body örneği:**
```json
{
  "department_id": 1,
  "slot_duration_minutes": 60,
  "day_start_time": "08:00",
  "day_end_time": "17:00",
  "active_days": [1, 2, 3, 4, 5]
}
```

---

## schedule_assignments (Canlı Program)

| İşlem | Method | Endpoint | Kısıtlamalar |
|---|---|---|---|
| Bölüm atamaları | `GET` | `/schedule_assignments?course_id=in.(<id1>,<id2>...)` | Giriş yapmış herkes |
| Hoca atamaları | `GET` | `/schedule_assignments?lecturer_id=eq.<id>` | Giriş yapmış herkes |
| Tüm atamaları getir | `GET` | `/schedule_assignments` | Giriş yapmış herkes |
| Atama oluştur | `POST` | `/schedule_assignments` | ADMIN, DEPT_HEAD (FULL_ACCESS) |
| Atama güncelle | `PATCH` | `/schedule_assignments?id=eq.<id>` | ADMIN, DEPT_HEAD (FULL_ACCESS) |
| Atama sil | `DELETE` | `/schedule_assignments?id=eq.<id>` | ADMIN, DEPT_HEAD (FULL_ACCESS) |

**Kilit durumu güncelle:**
```
PATCH /schedule_assignments?id=eq.<id>
Body: { "is_locked": true }
```

---

## availability_slots (Hoca Müsaitlik)

| İşlem | Method | Endpoint | Kısıtlamalar |
|---|---|---|---|
| Hoca müsaitliği getir | `GET` | `/availability_slots?lecturer_id=eq.<id>` | Giriş yapmış herkes |
| Müsaitlik güncelle (upsert) | `POST` | `/availability_slots` (upsert) | LECTURER (kendi), DEPT_HEAD, ADMIN |
| Slot sil | `DELETE` | `/availability_slots?id=eq.<id>` | LECTURER (kendi), ADMIN |

**Upsert Headers:**
```
Prefer: resolution=merge-duplicates
```

**Body örneği:**
```json
{
  "lecturer_id": 1,
  "day_of_week": 1,
  "slot_index": 0,
  "is_available": false
}
```

---

## schedule_drafts (Program Taslakları)

| İşlem | Method | Endpoint | Kısıtlamalar |
|---|---|---|---|
| Bölüm taslakları | `GET` | `/schedule_drafts?department_id=eq.<id>` | DEPT_HEAD (kendi bölümü), ADMIN |
| Bekleyen taslaklar | `GET` | `/schedule_drafts?status=eq.PENDING` | Sadece ADMIN |
| Taslak detayı | `GET` | `/schedule_drafts?id=eq.<id>` | DEPT_HEAD (sahibi), ADMIN |
| Taslak oluştur | `POST` | `/schedule_drafts` | Sadece DEPT_HEAD |
| Taslak güncelle | `PATCH` | `/schedule_drafts?id=eq.<id>` | DEPT_HEAD (DRAFT durumunda) |
| Taslak durumu güncelle | `PATCH` | `/schedule_drafts?id=eq.<id>` | Sadece ADMIN |
| Taslak sil | `DELETE` | `/schedule_drafts?id=eq.<id>` | DEPT_HEAD (kendi, DRAFT'ta) |

**Onay body örneği:**
```json
{
  "status": "APPROVED",
  "admin_note": "Onaylandı",
  "reviewed_by": "<admin-uuid>",
  "reviewed_at": "2025-01-01T10:00:00Z"
}
```

---

## change_requests (Değişiklik Talepleri)

| İşlem | Method | Endpoint | Kısıtlamalar |
|---|---|---|---|
| Kendi taleplerim | `GET` | `/change_requests?lecturer_id=eq.<id>` | LECTURER (kendi) |
| Bölüm talepleri | `GET` | `/change_requests?lecturer_id=in.(...)` | DEPT_HEAD (kendi bölümü) |
| Bekleyen tümü | `GET` | `/change_requests?status=eq.PENDING` | Sadece ADMIN |
| Talep oluştur | `POST` | `/change_requests` | Sadece LECTURER |
| Dept Head inceleme | `PATCH` | `/change_requests?id=eq.<id>` | Sadece DEPT_HEAD |
| Admin inceleme | `PATCH` | `/change_requests?id=eq.<id>` | Sadece ADMIN |

**Dept Head onay body:**
```json
{
  "dept_head_status": "APPROVED",
  "dept_head_note": "Uygun",
  "dept_head_reviewed_by": "<uuid>",
  "dept_head_reviewed_at": "2025-01-01T10:00:00Z"
}
```

**Admin onay body:**
```json
{
  "admin_status": "APPROVED",
  "status": "APPROVED",
  "admin_note": "Onaylandı",
  "admin_reviewed_by": "<uuid>",
  "admin_reviewed_at": "2025-01-01T10:00:00Z"
}
```

---

## Storage (Excel İçe/Dışa Aktarma)

| İşlem | Method | Endpoint |
|---|---|---|
| Excel yükle | `POST` | `/storage/v1/object/excel-imports/<filename>` |
| Dosya URL al | `GET` | `/storage/v1/object/public/excel-imports/<filename>` |

---

## Realtime Subscriptions (Kotlin SDK)

```kotlin
// Bekleyen talepleri dinle
supabase.realtime
    .channel("change_requests")
    .postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "change_requests"
        filter = PostgresChangeFilter(
            type = FilterType.EQ,
            column = "status",
            value = "PENDING"
        )
    }
    .collect { action -> /* güncelle */ }

// Taslak durumu değişince dinle
supabase.realtime
    .channel("schedule_drafts")
    .postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "schedule_drafts"
    }
    .collect { action -> /* güncelle */ }
```

---

## Supabase SQL Helper Functions

| Fonksiyon | Açıklama |
|---|---|
| `public.get_user_role()` | Mevcut kullanıcının rolünü döndürür |
| `public.get_user_department_id()` | Mevcut kullanıcının bölüm ID'sini döndürür |

---

## Query Parametreleri (PostgREST)

| Parametre | Kullanım |
|---|---|
| `select=*` | Tüm alanlar |
| `select=id,name,department_id` | Belirli alanlar |
| `?id=eq.5` | ID eşitlik filtresi |
| `?status=in.(PENDING,APPROVED)` | Çoklu değer filtresi |
| `?order=created_at.desc` | Sıralama |
| `?limit=20&offset=0` | Sayfalama |
| `Prefer: return=representation` | Yanıtta oluşturulan kaydı döndür |
| `Prefer: resolution=merge-duplicates` | Upsert işlemi için |
