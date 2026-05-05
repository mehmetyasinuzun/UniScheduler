const XLSX = require('xlsx');
const fs = require('fs');

if (!fs.existsSync('Ornek_Exceller')) {
    fs.mkdirSync('Ornek_Exceller');
}

// 1. Akademisyen Örneği
const lecData = [
    { Unvan: 'Prof. Dr.', Ad: 'Ahmet', Soyad: 'Yılmaz', Eposta: 'ahmet.yilmaz@uni.edu.tr', Kullanici_Adi: 'ahmet.yilmaz', Sifre: 'Sifre123!' },
    { Unvan: 'Doç. Dr.', Ad: 'Ayşe', Soyad: 'Kaya', Eposta: 'ayse.kaya@uni.edu.tr', Kullanici_Adi: 'ayse.kaya', Sifre: 'Sifre123!' }
];
const lecWs = XLSX.utils.json_to_sheet(lecData);
const lecWb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(lecWb, lecWs, 'Akademisyenler');
XLSX.writeFile(lecWb, 'Ornek_Exceller/Akademisyenler_Ornek.xlsx');

// 2. Ders Örneği
const courseData = [
    { Kod: 'CS101', Ad: 'Bilgisayar Bilimlerine Giriş', Teori: 3, Lab: 0, Kredi: 3 },
    { Kod: 'MATH101', Ad: 'Kalkülüs I', Teori: 4, Lab: 0, Kredi: 4 },
    { Kod: 'PHY101', Ad: 'Fizik I', Teori: 3, Lab: 2, Kredi: 4 }
];
const courseWs = XLSX.utils.json_to_sheet(courseData);
const courseWb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(courseWb, courseWs, 'Dersler');
XLSX.writeFile(courseWb, 'Ornek_Exceller/Dersler_Ornek.xlsx');

// 3. Sınıf Örneği
const classData = [
    { Oda_Kodu: 'A-101', Kapasite: 40, Tur: 'theory' },
    { Oda_Kodu: 'LAB-1', Kapasite: 20, Tur: 'lab' },
    { Oda_Kodu: 'B-202', Kapasite: 60, Tur: 'theory' }
];
const classWs = XLSX.utils.json_to_sheet(classData);
const classWb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(classWb, classWs, 'Siniflar');
XLSX.writeFile(classWb, 'Ornek_Exceller/Siniflar_Ornek.xlsx');

console.log('Örnek Excel dosyaları Ornek_Exceller klasörüne oluşturuldu.');
