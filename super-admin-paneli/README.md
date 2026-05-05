# UniScheduler Super Admin Paneli

Bu proje, UniScheduler sisteminin en üst düzey yönetim ve izleme arayüzünü oluşturur. Sistem sahibi veya baş yöneticiler (Super Admin) için tasarlanmış olan bu web tabanlı panel, sistemdeki tüm organizasyonları, yöneticileri, akademisyenleri ve ders programlarını tek bir noktadan yönetebilmenizi ve denetleyebilmenizi sağlar.

## Özellikler

- Organizasyon Yönetimi: Sisteme yeni üniversiteler veya kurumlar ekleme ve çıkarma.
- Admin Yönetimi: Organizasyonlara özel yönetici (admin) hesapları oluşturma ve şifre sıfırlama işlemleri.
- Veri Görüntüleme: Sisteme kayıtlı tüm akademisyenlerin, derslerin, sınıfların ve hazırlanmış olan ders programlarının organizasyon bazlı detaylı listelenmesi.
- Müsaitlik Yönetimi: Akademisyenlerin sisteme girdikleri müsaitlik durumlarının takibi ve gerektiğinde düzenlenmesi.
- Log İzleme: Mobil uygulama tarafında karşılaşılan hataların cihaz ve kullanıcı detaylarıyla birlikte anlık olarak izlenmesi.

## Kurulum ve Çalıştırma

Paneli kendi ortamınızda çalıştırmak için aşağıdaki adımları izleyebilirsiniz.

### 1. Gereksinimler
Bilgisayarınızda veya sunucunuzda Node.js (versiyon 14 ve üzeri tavsiye edilir) yüklü olmalıdır.

### 2. Bağımlılıkların Yüklenmesi
Terminal veya komut satırından projenin bulunduğu dizine (super-admin-paneli) gidin ve gerekli kütüphaneleri indirmek için şu komutu çalıştırın:

npm install

### 3. Çevre Değişkenlerinin (Environment Variables) Ayarlanması
Projeyi çalıştırmadan önce veritabanı bağlantılarını ayarlamanız gerekmektedir. 
Proje ana dizininde bulunan `.env.example` dosyasının adını `.env` olarak değiştirin veya kopyalayarak yeni bir `.env` dosyası oluşturun. 
Dosya içerisindeki gerekli alanları kendi Supabase proje bilgilerinizle doldurun:

- SUPABASE_URL: Supabase projenizin API URL adresi.
- SUPABASE_SERVICE_KEY: Veritabanına tam erişim sağlayan Service Role Key.
- ADMIN_USERNAME: Panele giriş yapmak için kullanacağınız kullanıcı adı.
- ADMIN_PASSWORD: Panele giriş yapmak için kullanacağınız şifre.

Not: Güvenliğiniz için bu bilgileri hiçbir zaman kaynak kod kontrol sistemlerine (örneğin GitHub veya GitLab) yüklemeyin.

### 4. Uygulamayı Başlatma
Yapılandırmaları tamamladıktan sonra paneli başlatmak için terminalden aşağıdaki komutu kullanabilirsiniz:

npm start

Bu komut sunucuyu başlatacak ve terminalde uygulamanın hangi port üzerinde (varsayılan olarak http://localhost:3000) çalıştığını belirten bir mesaj gösterecektir. İnternet tarayıcınızdan bu adrese giderek panele erişebilirsiniz.

## Mimari ve Teknolojiler

Bu panel, hızlı çalışması ve kolay bakım yapılabilmesi amacıyla sade bir mimari ile inşa edilmiştir:
- Arka Uç (Backend): Node.js ve Express.js kullanılarak geliştirilmiştir. Veritabanı işlemleri için Supabase SDK (supabase-js) kullanmaktadır.
- Ön Yüz (Frontend): Herhangi bir ağır framework kullanılmadan, saf HTML, CSS ve JavaScript (Vanilla JS) ile kodlanmıştır. İş mantığı, tasarım ve iskelet dosyaları (app.js, style.css ve index.html) birbirinden ayrılarak temiz bir kod yapısı oluşturulmuştur.
- Güvenlik: Uygulama Helmet ve CORS ara katmanları (middleware) ile HTTP başlıkları düzeyinde güvence altına alınmış, ön yüzde XSS ataklarını önleyecek filtreleme yöntemleri uygulanmıştır.

Daha fazla geliştirme veya entegrasyon ihtiyacınız olduğunda `server.js` dosyası üzerinden yeni API rotaları ekleyebilir veya `public/js/app.js` dosyası üzerinden ön yüz mantığını genişletebilirsiniz.
