package com.unischeduler.util

object ExcelTemplates {
    // Import template columns
    val IMPORT_HEADERS = listOf("Ders Kodu", "Ders Adı", "Öğretim Üyesi")

    // Schedule export columns
    val SCHEDULE_HEADERS = listOf(
        "Ders Kodu", "Ders Adı", "Öğretim Üyesi", "Gün", "Saat", "Derslik", "Dönem"
    )

    // Availability export columns
    val AVAILABILITY_HEADERS = listOf(
        "Öğretim Üyesi", "Gün", "Slot", "Müsait"
    )

    // Credentials export columns
    val CREDENTIALS_HEADERS = listOf(
        "Ad Soyad", "Ünvan", "Kullanıcı Adı", "E-posta", "Şifre"
    )

    // Draft export columns
    val DRAFT_HEADERS = listOf(
        "Ders Kodu", "Ders Adı", "Öğretim Üyesi", "Gün", "Slot", "Derslik"
    )

    val DAY_NAMES = mapOf(
        1 to "Pazartesi",
        2 to "Salı",
        3 to "Çarşamba",
        4 to "Perşembe",
        5 to "Cuma",
        6 to "Cumartesi",
        7 to "Pazar"
    )
}
