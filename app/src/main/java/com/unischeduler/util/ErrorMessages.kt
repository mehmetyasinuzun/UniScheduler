// Centralized error message mapper — converts raw exceptions into user-friendly messages.
// Use ErrorMessages.map(e) in all ViewModel catch blocks instead of e.message.
//
// LOCALIZATION
// ────────────
// Repository / non-UI katmanları Context tutmadığı için throw mesajını
// hardcoded yapmak yerine UniSchedulerException(R.string.X) fırlatır.
// map() bunu yakalayıp App.instance.getString ile aktif locale'de çözer.
// Bu sayede aynı kod hem TR hem EN için doğru mesajı döner.
package com.unischeduler.util

import androidx.annotation.StringRes
import com.unischeduler.App
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Localized exception — Repository/util katmanlarından fırlatıldığında
 * ErrorMessages.map() çağrıldığında aktif locale'e göre strings.xml'den
 * çözülür. Format args opsiyonel.
 */
class UniSchedulerException(
    @StringRes val resId: Int,
    val args: Array<out Any> = emptyArray(),
    cause: Throwable? = null
) : Exception(cause)

object ErrorMessages {

    /**
     * Maps a raw exception to a user-friendly localized message.
     * Falls back to the exception message if no specific mapping is found.
     */
    fun map(e: Throwable): String {
        // i18n-aware exception — direkt strings.xml'den lookup. App.instance
        // henüz başlamadıysa (Robolectric/test) cause.message'a düş.
        if (e is UniSchedulerException) {
            return runCatching {
                if (e.args.isEmpty()) App.instance.getString(e.resId)
                else App.instance.getString(e.resId, *e.args)
            }.getOrElse { e.cause?.message ?: e.message ?: "Bir hata oluştu." }
        }

        if (e is IllegalArgumentException) return e.message ?: "Geçersiz giriş."
        if (e is IllegalStateException) return e.message ?: "Beklenmeyen bir hata oluştu."

        val msg = e.message.orEmpty().lowercase()

        return when {
            // Network errors
            e is UnknownHostException || e is ConnectException ->
                "İnternet bağlantısı yok. Lütfen ağınızı kontrol edin."
            e is SocketTimeoutException ->
                "Sunucuya ulaşılamadı. Lütfen tekrar deneyin."
            msg.contains("unable to resolve host") || msg.contains("no address associated") ->
                "İnternet bağlantısı yok. Lütfen ağınızı kontrol edin."
            msg.contains("timeout") || msg.contains("timed out") ->
                "Sunucuya ulaşılamadı. Lütfen tekrar deneyin."

            // Auth errors
            msg.contains("invalid login credentials") || msg.contains("invalid username or password") ->
                "Kullanıcı adı veya şifre hatalı."
            msg.contains("email not confirmed") ->
                "Hesap aktif değil. Yöneticinize başvurun."
            msg.contains("user already registered") || msg.contains("already been registered") ->
                "Bu kullanıcı adı zaten mevcut."
            msg.contains("email rate limit exceeded") || msg.contains("rate limit") || msg.contains("too many requests") ->
                "Çok fazla deneme yapıldı. Lütfen birkaç dakika bekleyip tekrar deneyin."
            msg.contains("email address") && msg.contains("invalid") ->
                "E-posta formatı geçersiz. Yöneticinize başvurun."
            msg.contains("signup is disabled") ->
                "Kayıt kapalı. Yöneticinize başvurun."
            msg.contains("password should be") || msg.contains("password must") ->
                "Şifre yeterince güçlü değil. En az 6 karakter olmalı."

            // Database constraint errors
            msg.contains("duplicate key") || msg.contains("unique constraint") || msg.contains("already exists") ->
                "Bu kayıt zaten mevcut. Lütfen farklı bir değer kullanın."
            msg.contains("violates foreign key") || msg.contains("foreign key constraint") ->
                "İşlem tamamlanamadı — bu kayda bağlı başka kayıtlar var."
            msg.contains("violates check constraint") ->
                "Geçersiz veri. Lütfen girdiğiniz değerleri kontrol edin."
            msg.contains("schedule conflict") ->
                "Çakışma tespit edildi. Hoca veya derslik bu saatte zaten dolu."
            msg.contains("violates not-null constraint") ->
                "Zorunlu bir alan boş bırakıldı. Lütfen tüm alanları doldurun."

            // RLS / permission errors
            msg.contains("new row violates row-level security") || msg.contains("rls") ->
                "Bu işlem için yetkiniz yok."
            msg.contains("jwt expired") || msg.contains("token is expired") ->
                "Oturumunuz sona erdi. Lütfen tekrar giriş yapın."
            msg.contains("not authenticated") || msg.contains("no api key") ->
                "Kimlik doğrulama gerekli. Lütfen tekrar giriş yapın."

            // Server-side issues
            msg.contains("stack depth") || msg.contains("max_stack_depth") ->
                "Sunucu yapılandırma hatası. Lütfen yöneticinize bildirin."
            msg.contains("statement timeout") ->
                "Sunucu meşgul. Lütfen biraz sonra tekrar deneyin."
            msg.contains("connection refused") || msg.contains("could not connect") ->
                "Sunucuya bağlanılamadı. Lütfen daha sonra tekrar deneyin."
            msg.contains("503") || msg.contains("service unavailable") ->
                "Sunucu geçici olarak kullanılamıyor. Lütfen biraz sonra tekrar deneyin."
            msg.contains("500") || msg.contains("internal server error") ->
                "Sunucu hatası. Lütfen tekrar deneyin."

            // Catch-all
            else -> e.message ?: "Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin."
        }
    }

    /**
     * Returns true if the exception likely indicates a transient failure that
     * makes sense to retry (network glitch, timeout, 5xx). Use to decide whether
     * to surface a "Retry" button.
     */
    fun isTransient(e: Throwable): Boolean {
        if (e is UnknownHostException || e is ConnectException || e is SocketTimeoutException) return true
        val msg = e.message.orEmpty().lowercase()
        return msg.contains("timeout") || msg.contains("timed out") ||
               msg.contains("503") || msg.contains("500") ||
               msg.contains("statement timeout") || msg.contains("connection refused")
    }
}
