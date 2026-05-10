// CrashHandler — global UncaughtExceptionHandler.
//
// Crash anında network çağrısı yapamayız çünkü proses ölmek üzere.
// Bunun yerine stack trace'i diske (cache/crashes/) atomik olarak
// yazıyoruz. Bir sonraki App.onCreate'te flushPendingCrashes ()
// dosyaları okuyup ErrorReporter ile DB'ye gönderir, sonra siler.
// Böylece "log'a düşmüyor, app sessizce kapanıyor" durumu çözülür.
package com.unischeduler.util

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.unischeduler.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val DIR_NAME = "crashes"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writePendingCrash(app, thread, throwable) }
                .onFailure { Log.e(TAG, "writePendingCrash failed", it) }
            // Sistemin kendi handler'ına devret — ANR/diyalog vs. normal akmasın engellenmesin
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writePendingCrash(ctx: Context, thread: Thread, throwable: Throwable) {
        val dir = File(ctx.cacheDir, DIR_NAME).apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(dir, "crash_$ts.txt")

        val sb = StringBuilder()
        sb.appendLine("THREAD=${thread.name}")
        sb.appendLine("APP_VERSION=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("DEVICE=${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("OS=Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        // Session bilgisi best-effort — yoksa boş kalır
        runCatching {
            val s = SessionManager(ctx)
            sb.appendLine("USERNAME=${s.username}")
            sb.appendLine("ROLE=${s.role}")
            sb.appendLine("ORG_ID=${s.orgId}")
            sb.appendLine("LECTURER_ID=${s.lecturerId}")
        }
        sb.appendLine("MESSAGE=${throwable.message ?: throwable::class.java.simpleName}")
        sb.appendLine("---")
        sb.appendLine(throwable.stackTraceToString())
        var cause = throwable.cause
        while (cause != null) {
            sb.appendLine("Caused by:")
            sb.appendLine(cause.stackTraceToString())
            cause = cause.cause
        }
        file.writeText(sb.toString())
    }

    /**
     * Diskte birikmiş crash dosyalarını oku, DB'ye gönder, sil.
     *
     * KRİTİK: client_error_logs insert policy'si SADECE authenticated.
     * Login'den önce flush yaparsak Postgrest SDK auth-less request
     * gönderirken null pointer atıyor (CharSequence.length crash) ve
     * gerçek crash kayboluyor. Bu yüzden session aktifken çağrılır.
     *
     * Insert başarısız olursa dosya SİLİNMEZ — sonraki turda yine
     * denenir; "yutulmuş" crash kalmaz.
     */
    fun flushPendingCrashes(app: Application) {
        val session = SessionManager(app)
        if (!session.isLoggedIn) {
            Log.d(TAG, "flush ertelendi — henüz login değil")
            return
        }

        val dir = File(app.cacheDir, DIR_NAME)
        if (!dir.exists()) return
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("crash_") } ?: return
        if (files.isEmpty()) return

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val repo = com.unischeduler.data.repository.ErrorLogRepository()
            for (f in files) {
                val body = runCatching { f.readText() }.getOrNull() ?: continue
                val msg = body.lineSequence()
                    .firstOrNull { it.startsWith("MESSAGE=") }
                    ?.removePrefix("MESSAGE=")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Uncaught exception"

                // ErrorReporter'ın retry-fallback'ini bypass ediyoruz —
                // o catch bloğu gerçek crash'i FALLBACK ile maskeleiyordu.
                // Burada ya tam başarı ya da dosya korunuyor.
                val log = com.unischeduler.data.model.ClientErrorLogInsert(
                    orgId = session.orgId.takeIf { it > 0 },
                    userId = session.userId.takeIf { it.isNotBlank() },
                    username = session.username.takeIf { it.isNotBlank() },
                    role = session.role.takeIf { it.isNotBlank() },
                    screen = "UncaughtException",
                    action = "crash",
                    message = msg.take(500),
                    stackTrace = body.take(8000),
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceModel = Build.MODEL,
                    osVersion = Build.VERSION.RELEASE
                )

                runCatching { repo.insert(log) }
                    .onSuccess { f.delete() }
                    .onFailure { Log.w(TAG, "flush insert failed for ${f.name}", it) }
            }
        }
    }
}
