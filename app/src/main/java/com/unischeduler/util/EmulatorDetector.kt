// EmulatorDetector — best-effort emulator/virtual device tespiti.
//
// Production app'i emülatörden açmak meşru bir senaryo değil; saldırgan
// otomasyonu (otomatik kullanıcı oluşturma, brute-force) genelde
// emülatörlerden geçer. Bu sınıf birden çok sinyali toplayıp tek bir
// boolean üretir; CTI tablosuna `is_emulator` kolonu olarak yazılır.
//
// AKTİF BLOK YOK — sadece veri toplama ve panelde rozet. Süper-admin
// gerekirse manuel olarak hesabı dondurur. False positive maliyetinden
// kaçınmak için kararlar her zaman insan tarafından verilir.
package com.unischeduler.util

import android.os.Build

object EmulatorDetector {

    /**
     * Build sınıfının cihaz parmak izinden tipik emülatör imzalarını
     * arar. Tek başına yanılabilir (örn. bazı OEM'ler "generic" string'i
     * kullanır), bu yüzden çoklu sinyal birleştirilir; iki veya daha
     * fazla eşleşme = emülatör kabul edilir.
     */
    fun isEmulator(): Boolean = signalCount() >= 2

    /** Eşleşen sinyal sayısı — testler ve debug için ayrı tutuldu. */
    fun signalCount(): Int {
        var hits = 0
        val fingerprint = (Build.FINGERPRINT ?: "").lowercase()
        val model       = (Build.MODEL ?: "").lowercase()
        val product     = (Build.PRODUCT ?: "").lowercase()
        val hardware    = (Build.HARDWARE ?: "").lowercase()
        val brand       = (Build.BRAND ?: "").lowercase()
        val device      = (Build.DEVICE ?: "").lowercase()
        val manufacturer= (Build.MANUFACTURER ?: "").lowercase()

        if (fingerprint.startsWith("generic") || fingerprint.contains("vbox") ||
            fingerprint.contains("test-keys") || fingerprint.contains("sdk_gphone")) hits++
        if (model.contains("google_sdk") || model.contains("emulator") ||
            model.contains("android sdk built for")) hits++
        if (product.contains("sdk") || product == "google_sdk" ||
            product.contains("vbox86p") || product.contains("emulator")) hits++
        if (hardware.contains("goldfish") || hardware.contains("ranchu") ||
            hardware == "vbox86" || hardware.contains("nox") ||
            hardware.contains("ttvm")) hits++
        if (brand.startsWith("generic") && device.startsWith("generic")) hits++
        if (manufacturer.contains("genymotion")) hits++
        // Bluestacks / NoxPlayer / LDPlayer vb. masaüstü emülatörler
        if (device.contains("bluestacks") || device.contains("noxplayer") ||
            device.contains("ldplayer")) hits++

        return hits
    }
}
