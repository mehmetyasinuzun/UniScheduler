const XLSX = require('xlsx');
const fs = require('fs');

if (!fs.existsSync('Ornek_Exceller')) {
    fs.mkdirSync('Ornek_Exceller');
}

// 1. Akademisyen Örneği — Sütun isimleri backend ile birebir uyumlu
const lecData = [
    { unvan: 'Prof. Dr.', ad: 'Ahmet', soyad: 'Yılmaz', eposta: 'ahmet.yilmaz@uni.edu.tr' },
    { unvan: 'Doç. Dr.', ad: 'Ayşe', soyad: 'Kaya', eposta: 'ayse.kaya@uni.edu.tr' },
    { unvan: 'Dr. Öğr. Üyesi', ad: 'Mehmet', soyad: 'Demir', eposta: 'mehmet.demir@uni.edu.tr' },
    { unvan: 'Arş. Gör.', ad: 'Fatma', soyad: 'Çelik', eposta: 'fatma.celik@uni.edu.tr' },
    { unvan: 'Öğr. Gör.', ad: 'Ali', soyad: 'Öztürk', eposta: 'ali.ozturk@uni.edu.tr' }
];
// NOT: kullanici_adi ve sifre sütunları opsiyonel. Boş bırakılırsa sistem otomatik üretir.
const lecWs = XLSX.utils.json_to_sheet(lecData);
const lecWb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(lecWb, lecWs, 'Akademisyenler');
XLSX.writeFile(lecWb, 'Ornek_Exceller/Akademisyenler_Ornek.xlsx');

// 2. Ders Örneği
const courseData = [
    { kod: 'BLM101', ad: 'Bilgisayar Bilimlerine Giriş', teori: 3, lab: 0, kredi: 3 },
    { kod: 'BLM102', ad: 'Programlama I', teori: 2, lab: 2, kredi: 3 },
    { kod: 'MAT101', ad: 'Kalkülüs I', teori: 4, lab: 0, kredi: 4 },
    { kod: 'FIZ101', ad: 'Fizik I', teori: 3, lab: 2, kredi: 4 },
    { kod: 'BLM201', ad: 'Veri Yapıları', teori: 3, lab: 2, kredi: 4 },
    { kod: 'BLM301', ad: 'Veritabanı Yönetim Sistemleri', teori: 3, lab: 2, kredi: 4 },
    { kod: 'BLM302', ad: 'İşletim Sistemleri', teori: 3, lab: 0, kredi: 3 },
    { kod: 'ENG101', ad: 'İngilizce I', teori: 3, lab: 0, kredi: 3 }
];
const courseWs = XLSX.utils.json_to_sheet(courseData);
const courseWb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(courseWb, courseWs, 'Dersler');
XLSX.writeFile(courseWb, 'Ornek_Exceller/Dersler_Ornek.xlsx');

// 3. Sınıf Örneği
const classData = [
    { oda_kodu: 'A-101', kapasite: 40, tur: 'theory' },
    { oda_kodu: 'A-102', kapasite: 60, tur: 'theory' },
    { oda_kodu: 'A-201', kapasite: 80, tur: 'theory' },
    { oda_kodu: 'B-101', kapasite: 35, tur: 'theory' },
    { oda_kodu: 'LAB-1', kapasite: 25, tur: 'lab' },
    { oda_kodu: 'LAB-2', kapasite: 20, tur: 'lab' },
    { oda_kodu: 'LAB-3', kapasite: 30, tur: 'lab' }
];
const classWs = XLSX.utils.json_to_sheet(classData);
const classWb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(classWb, classWs, 'Siniflar');
XLSX.writeFile(classWb, 'Ornek_Exceller/Siniflar_Ornek.xlsx');

console.log('✅ Örnek Excel dosyaları Ornek_Exceller/ klasörüne oluşturuldu.');
console.log('   - Akademisyenler_Ornek.xlsx (5 akademisyen)');
console.log('   - Dersler_Ornek.xlsx (8 ders)');
console.log('   - Siniflar_Ornek.xlsx (7 sınıf)');
