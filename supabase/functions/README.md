# Supabase Edge Functions — UniScheduler

Bu klasör mobile uygulamanın "ağır" işlemlerini servis-tarafında yapan
Deno/TypeScript fonksiyonlarını içerir. Edge Function'lar Supabase Cloud
içinde 7/24 ayakta durur — kendi sunucu açmana gerek yok.

## Mevcut Fonksiyonlar

### `bulk-create-lecturers`

Mobile uygulamanın **350+ hocayı Excel'den toplu içeri aktarmasını**
30 saniye içinde tamamlar. Eski mobile-direkt yaklaşımı Supabase Auth'un
30 req/dk rate-limit'i yüzünden 9+ dakika sürerdi.

**Çağıran:** Sadece giriş yapmış admin'ler (içeride JWT + role kontrolü)
**Yan etkisi:** Org'a hoca ekler, public.users + lecturers + auth.users
yazar, response'ta `credentials` (username + temp password) listesi döner.

---

## Deploy Etme

### Yöntem 1 — Supabase Dashboard (önerilen, CLI gerekmez)

1. **Supabase Dashboard** → Projenize gidin
2. Sol menüden **Edge Functions** → **Create a new function**
3. Function adı: `bulk-create-lecturers` (bu isim mobile kodunda sabit, değiştirme)
4. **Code editor**'a açılan yere `supabase/functions/bulk-create-lecturers/index.ts`
   dosyasının **tüm içeriğini** yapıştır
5. Sağ üstte **Deploy function** butonuna bas
6. Deploy bittiğinde "Status: Active" yeşil işareti görmelisin

### Yöntem 2 — Supabase CLI (geliştirici tercihi)

```bash
# Bir kere kurulum
npm install -g supabase

# Login
supabase login

# Bu projeye link
supabase link --project-ref lcnganxesvgbfiorifig

# Deploy
supabase functions deploy bulk-create-lecturers
```

---

## Doğrulama

Deploy ettikten sonra fonksiyonun çalıştığını şu URL'le test edebilirsin:

```bash
# Bu HTTP 401 dönmeli ("Authorization eksik") — doğru davranış.
# 404 dönerse henüz deploy edilmemiş demektir.
curl -X POST https://lcnganxesvgbfiorifig.supabase.co/functions/v1/bulk-create-lecturers
```

Beklenen yanıt:
```json
{"error":"Authorization header eksik."}
```

---

## Ortam Değişkenleri

Supabase Edge Functions otomatik olarak şu env değişkenlerini sağlar:
- `SUPABASE_URL` — proje URL'i
- `SUPABASE_SERVICE_ROLE_KEY` — RLS bypass yetkili anahtar
- `SUPABASE_ANON_KEY` — public anahtar

Manuel ayar gerekmez. Fonksiyon bu üçünü `Deno.env.get(...)` ile okur.

---

## Güvenlik Notu

- `SERVICE_ROLE_KEY` sadece Edge Function ortamında. Mobile uygulamaya
  hiç verilmez.
- Mobile uygulama kendi admin JWT'sini gönderir; fonksiyon içeride
  `auth.getUser(jwt)` ile doğrular + `role='admin'` kontrolü yapar.
- Anonim kullanıcı çağıramaz; lecturer rolündeki kullanıcı çağıramaz.
- Admin sadece **kendi org'una** insert yapabilir (cross-org saldırı engelli).

---

## Fallback Davranışı

Edge Function deploy edilmemiş bir projede:
- Mobile import yine **çalışır**, sadece eski (yavaş, ~9 dk) yola düşer.
- Logcat'ta uyarı görürsün: `"Edge Function unavailable — falling back to client-side import"`

Yani deploy etmek **opsiyonel optimizasyon** — kritik değil ama bulk
import'lar için hayat kurtarıcı.
