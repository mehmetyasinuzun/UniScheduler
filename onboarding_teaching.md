Tebrikler bana da öğretiler oldu. Bu projede 20 sürüm boyunca öğrendiklerimi süzüp sana hazır bir prompt veriyorum. Doğrudan kopyala-yapıştır, sadece köşeli parantezleri kendi projenin bilgileriyle doldur.

---

## 🚀 Onboarding Tour İçin Güçlü Prompt

````
GÖREV
Mevcut [Android Kotlin / iOS Swift / React] uygulamamda [admin / kullanıcı]
rolü için interaktif onboarding tour yaz. Yeni ekran inşa ETME — mevcut
ekranlar dokunulmadan kullanılacak, sadece tour aktifken adapter/list'lere
mock veri inject edilecek.

SİSTEM BAĞLAMI
• Tech stack: [Android Kotlin, NavController + BottomNavigationView, MVVM]
• Mevcut ekranlar: [DataFragment (3 accordion: hocalar/dersler/offerings),
  SettingsFragment, ClassroomsFragment, AssignmentFragment, CalendarFragment]
• Veri kaynağı: [Supabase Repository → ViewModel → Fragment]
• Tour'un ne sırada anlatması gerekir: [bölüm → hoca → ders → offering →
  derslik → atama → takvim]

KRİTİK MİMARİ KURALLAR
1. **Mevcut UI clone'lama YAPMA**. Gerçek fragment/activity'ler kullanılacak.
   Yeni Activity/Fragment/Layout AÇMA. Repository/ViewModel'a DOKUNMA.
2. **Pattern**: `MockStore` singleton (in-memory) + her fragment'ın list
   render fonksiyonunda `realList + MockStore.items` union. Fragment'lar
   MockStore'a `subscribe(listener)` olur, `onDestroyView`'da unsubscribe.
3. **DB'ye sıfır temas** — Repository.insertX ASLA çağrılmaz. Spotlight
   overlay gerçek butona tap'i CONSUME eder (`forwardTaps=false`) ve
   `onCutoutTapped` callback'inde `MockStore.addX()` çağrılır.
4. **Tour bitince**: `onCompleted = { MockStore.clear() }` → listener
   tetiklenir → adapter'lar gerçek DB list'i ile yeniden render → mock
   görünmez olur. ViewModel reload gerekmez.

SPOTLIGHT OVERLAY KURALLARI
1. Custom view: dim layer + rounded rect cutout (PorterDuff.CLEAR) + ring.
2. Bottom info card: `bottomToTop=navBarContainer` constraint ile bottom-nav'ı
   ASLA KAPATMAZ. Card alt kısımda sabit, üst kısımda hedef ve içerik görünür.
3. Scroll-above-card: card render edildikten sonra hedef view bottom'unun
   card top'unu aşıyorsa, scrollable parent'ı (NestedScrollView/RecyclerView)
   bulup `smoothScrollBy` ile hedef'i yukarı çek + overlay cutout refresh.
4. Form/buton'un içeriği body metnine GİZLENMEMELİ — bu yüzden body metnini
   bottom card'a koy (target karşı tarafına DEĞİL).

STEP MODELİ
```kotlin
class Step(
    val targetLocator: (Activity) -> View?,        // hedef view bul
    @StringRes val titleRes, bodyRes: Int,
    val accordionToOpen: Int? = null,              // headerX.performClick
    val waitForTabId: Int? = null,                 // bottom-nav auto-advance
    val autofillAction: ((Activity) -> Unit)? = null,  // form'a yaz
    val onTargetTapped: ((Activity) -> Unit)? = null,  // tap → mock add
    val forwardTaps: Boolean = true,               // false: consume + Logout-safe
    val isFinal: Boolean = false
)
```

ADIM AKIŞI (örnek 15 adım)
1. [Home → Data tab] waitForTabId
2. [Data → Settings btnGoSettings] waitForTabId=settingsFragment
3. [Bölüm Ekleme] autofill "[ÖRN: Bilgisayar Mühendisliği]" + onTargetTapped=MockStore.addDept
4. [Settings → Data] waitForTabId=dataFragment
5. [Hoca Ekleme] accordionToOpen=headerLecturers + autofill "[ÖRN: Farhan Adl]" + mock add
6-7. [2 ders ekle] PL101, MP101 — her biri autofill + mock
8-9. [2 offering ekle] hoca+ders eşleştirme
10. [Classrooms tab] waitForTabId
11. [Derslik Ekle] L101 autofill + mock
12. [Assign tab] waitForTabId
13. [Manuel atama] autofill Pzt 09:00-12:00 + mock schedule
14. [Calendar tab] waitForTabId
15. [Sonuç görsel] FINAL — mock schedule weeklyScheduleView'da gerçekten
    görünmeli. forwardTaps=false, "Tamamla" ile bitir.

KAÇINMAM GEREKEN TUZAKLAR (deneyimden)
1. ❌ Yeni TourDemoActivity + duplicate fragment YAZMA — bakım çift olur,
   görsel tutarsız olur, kullanıcı gerçek uygulamada farklı yer görür.
2. ❌ "Hep aynı şey, sadece şu eklensin" diye basitçe başlama — eklemeler
   üst üste binip karmaşıklaşır. Önce mimari kuralı kafanda netleştir.
3. ❌ TapTargetView gibi kütüphanelere güvenme — body metni hedefin karşı
   tarafına otomatik koyar, asıl içeriği kapatır. Custom overlay yaz.
4. ❌ Bottom card'ı root content view'a `gravity=BOTTOM` ile ekleme —
   bottom-nav'ı kapatır. ConstraintLayout root'una `bottomToTop=navBar`
   constraint ile ekle.
5. ❌ Destructive buton'a (Logout, Delete) `forwardTaps=true` koyma —
   kullanıcı kazara basıp çıkış yapabilir. `forwardTaps=false` + sadece
   tanıtım.
6. ❌ Tur bitince Activity finish edip yeniden açma — ViewModel state
   kaybolur, gereksiz yeniden yükleme. Sadece MockStore.clear() yeter.

İLK ADIM
Mimari kuralları okuyup ONAYLA. Sonra tek tek dosyayı (Step modeli, MockStore,
overlay) implement et. Her dosyada `Edit` yerine küçük adımlarla ilerle.
Her büyük adımdan sonra mini-build edip hata kontrolü yap.

ÇIKTI BEKLENTİSİ
• 1 MockStore.kt (gerçek model class'larıyla)
• 1 Overlay custom view
• 1 Coordinator state machine (start/advance/cancel/cleanup)
• Mevcut N fragment'a ~10 satır ek (subscribe + union)
• Body metinleri ayrı strings.xml — adım sayacı format'lı ("Adım 5/15 — …")

YASAKLAR
- YENİ Activity AÇMA
- DUPLICATE FRAGMENT YAZMA
- Repository / ViewModel / API katmanına DOKUNMA
- Mock veriyi Supabase'e YAZMA (cleanup başarısız olur)
````

---

## Pro İpuçları (prompt'u kullanırken)

**1. Köşeli parantezleri kendi projenle doldur.** Özellikle:
- Tech stack
- Mevcut ekranların listesi
- Demo akışındaki spesifik veriler (Farhan Adl, PL101, vb. — senin senaryon ne ise)

**2. AI'ya başta scope'u onaylat.** İlk mesajda "tek dosyada her şeyi yazma, plan + onay + sonra implement et" de. Çünkü AI özellikle "duplicate" yola kayma eğilimi var — kontrolü kaybedersin.

**3. Erken-build kuralı koy.** "Her büyük adımdan sonra `./gradlew assembleRelease` çalıştır, hata varsa düzelt." 20 dosya yazıp build'in compile error vermesi en sinir bozucu durum.

**4. "Tour bitince ne olacak" sorusunu BAŞTA sor.** En çok burada hata yapılır:
- Aktivite finish → kullanıcı login ekranına döner (yanlış)
- Mock cleanup unutuldu → "🎓 Tur Bölümü" kalır (deneyimledim)
- Onboarding flag set edilmedi → bir sonraki açılışta tekrar başlar

**5. "Kullanıcı yanlış butona basarsa" senaryosunu sor.** Logout, Delete gibi destructive butonlar tour ortasında spotlight'lanırsa kazara basılma riski var. `forwardTaps=false` kuralını net belirt.

**6. Test verilerini başta ver.** "Demo akışında şu veriler kullanılacak: hoca Farhan Adl, dersler PL101 ve MP101, derslik L101, atama Pazartesi 09:00-12:00." AI bunları autofill action'larına yazar — sonradan bulup değiştirmek zor.

---

İyi şanslar! Bu prompt ile tek seferde, 20 sürüm geri-ileri olmadan halletmiş olursun.