// LoginViewModel — authenticates user via Supabase GoTrue, updates SessionManager, emits UiState.
// Flow: signIn → fetch profile → save session → emit role.
package com.unischeduler.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unischeduler.data.repository.AuthRepository
import com.unischeduler.util.ErrorMessages
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.SessionManager
import com.unischeduler.util.UiState
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = AuthRepository()
    private val session = SessionManager(app)
    private val errorReporter = ErrorReporter(app)

    private val _state = MutableStateFlow<UiState<LoginResult>>(UiState.Idle)
    val state: StateFlow<UiState<LoginResult>> = _state

    fun login(username: String, password: String) {
        val attemptedUsername = username.trim()
        if (username.isBlank() || password.isBlank()) {
            _state.value = UiState.Error("Please fill in all fields.", retryable = false)
            reportValidationError("login", "Please fill in all fields.", attemptedUsername)
            return
        }

        // Wipe any persisted session before attempting a new login. Without this,
        // a failed login (or one that throws after JWT issue) could leave the old
        // session key/orgId in place and subsequent screens would query the wrong
        // org.
        session.clear()

        viewModelScope.launch {
            _state.value = UiState.Loading
            // attemptStep tracks which phase we were in when something
            // failed — used by the audit logger so an admin can see
            // "auth failed" vs. "profile fetch failed" without parsing
            // the message text.
            var attemptStep = "signIn"
            runCatching {
                withContext(Dispatchers.IO) {
                    // 1. Sign in via Supabase GoTrue — obtains JWT.
                    //    Critically, we DO NOT swallow the Supabase
                    //    exception here. The original message ("Invalid
                    //    login credentials" / "Email not confirmed" /
                    //    "Network is unreachable" / etc.) is what the
                    //    user needs to see; ErrorMessages.map will
                    //    translate it to Turkish. Wrapping it as a
                    //    generic "Invalid username or password" was
                    //    masking real problems (e.g. mis-confirmed
                    //    auth users, expired projects).
                    repo.signIn(attemptedUsername, password)

                    attemptStep = "fetchProfile"
                    // 2. Fetch user profile from public.users
                    val user = repo.getCurrentUserProfile()
                        ?: throw IllegalStateException(
                            "Kimlik doğrulandı ama public.users içinde profil yok. " +
                            "Süper admin panelinden bu kullanıcıyı yeniden ekleyin."
                        )

                    val orgId = user.orgId
                    if (orgId <= 0) {
                        throw IllegalStateException(
                            "Bu hesap bir kuruma bağlı değil (org_id=$orgId). Yöneticiyle iletişime geçin."
                        )
                    }

                    attemptStep = "fetchLecturer"
                    // 3. If lecturer, fetch lecturer profile
                    val lecturer = if (user.role == "lecturer") {
                        repo.getLecturerByUserId(user.id, orgId)
                            ?: throw IllegalStateException(
                                "Hocaya ait bir profil bulunamadı. Yöneticiyle iletişime geçin."
                            )
                    } else null

                    attemptStep = "persistSession"
                    // 4. Save session — TEK atomic commit içinde.
                    //    Önceki implementasyon her alan için ayrı apply()
                    //    çağırıyordu; biri fail olursa session yarı yazılı
                    //    kalıyordu (orgId yazılmış, role boş gibi → bottom
                    //    nav görünmez + sonraki load'da "Bu hesap bir
                    //    kuruma bağlı değil" hatası). saveSession() atomic.
                    val saved = session.saveSession(
                        userId = user.id,
                        orgId = orgId,
                        username = user.username,
                        role = user.role,
                        lecturerId = lecturer?.id ?: -1
                    )
                    if (!saved) {
                        throw IllegalStateException(
                            "Oturum bilgileri cihaza yazılamadı. " +
                            "Cihaz depolaması dolu olabilir veya güvenli " +
                            "anahtar deposu erişilemez durumda — uygulamayı " +
                            "yeniden başlatın ya da cache temizleyin."
                        )
                    }

                    // Diagnostic — Logcat: adb logcat | grep "LoginVM"
                    android.util.Log.d(
                        "LoginVM",
                        "Session persisted — orgId=$orgId, role='${user.role}', " +
                        "lecturerId=${lecturer?.id ?: -1}, userId=${user.id.take(8)}…"
                    )

                    LoginResult(role = user.role, mustChangePassword = user.mustChangePassword)
                }
            }.onSuccess { result ->
                _state.value = UiState.Success(result)
                // Successful login — record the attempt for audit/CTI.
                recordLoginAttempt(attemptedUsername, succeeded = true)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = UiState.Error(ErrorMessages.map(e), retryable = false)
                reportError("login.$attemptStep", e, attemptedUsername)
                recordLoginAttempt(attemptedUsername, succeeded = false, failureStep = attemptStep)
            }
        }
    }

    private fun reportError(action: String, e: Throwable, username: String) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportException("LoginViewModel", action, e, usernameOverride = username)
        }
    }

    /**
     * Records every login attempt (success or failure) into the
     * login_attempts table for audit / threat-intelligence.
     *
     * Best-effort — if the write fails (offline, RLS denies, table
     * missing on legacy schema) we silently drop. The login UX is
     * the priority; we never want a logging failure to mask the
     * authentication outcome.
     *
     * Privacy note: device_id is a HASH of (manufacturer + model +
     * Settings.Secure.ANDROID_ID), NOT the raw values. ANDROID_ID
     * resets on factory reset, which is the granularity we want for
     * CTI: catches the same attacker hopping accounts on one device,
     * doesn't track an end-user across two phones. The hash means
     * even if the database leaks, no raw identifier escapes.
     *
     * IP address is filled in server-side by the panel — clients
     * can't see their own egress IP reliably.
     */
    private fun recordLoginAttempt(
        username: String,
        succeeded: Boolean,
        failureStep: String? = null
    ) {
        val app = getApplication<Application>()
        val deviceId = deviceFingerprint(app)
        val model    = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val osVer    = "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
        val appVer   = com.unischeduler.BuildConfig.VERSION_NAME
        val ua       = "UniScheduler/$appVer (Android ${android.os.Build.VERSION.RELEASE})"
        val isEmu    = com.unischeduler.util.EmulatorDetector.isEmulator()

        // CRITICAL: use applicationScope, NOT viewModelScope.
        // LoginFragment is fragment-scoped and navigates away the instant
        // UiState.Success is emitted; viewModelScope gets cancelled and the
        // insert dies before reaching the network. applicationScope survives
        // navigation and process-binds the work to the Application lifecycle.
        com.unischeduler.App.applicationScope.launch {
            // Public IP'yi best-effort topla; başarısızsa null gönder.
            // Login akışı zaten tamamlandığı için bu insert'in geç gelmesi
            // kullanıcıyı bekletmiyor — fire-and-forget pattern.
            val publicIp = fetchPublicIp()
            try {
                com.unischeduler.data.remote.SupabaseClient.client.postgrest["login_attempts"]
                    .insert(LoginAttemptRow(
                        username     = username,
                        succeeded    = succeeded,
                        userAgent    = ua,
                        deviceId     = deviceId,
                        deviceModel  = model.take(120),
                        osVersion    = osVer,
                        appVersion   = appVer,
                        source       = "mobile",
                        failureStep  = failureStep,
                        isEmulator   = isEmu,
                        ipAddress    = publicIp
                    ))
                android.util.Log.d(
                    "LoginVM",
                    "login_attempt logged — username=$username, succeeded=$succeeded, " +
                    "ip=${publicIp ?: "(null)"}"
                )
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Throwable) {
                // We used to silently swallow this with runCatching {} which
                // is exactly what kept the CTI dashboard empty for weeks:
                // any RLS denial / network blip / serialization mismatch was
                // invisible. Now Logcat sees it AND ErrorReporter persists it
                // so the admin can see auth-audit failures alongside other
                // client errors.
                android.util.Log.e(
                    "LoginVM",
                    "login_attempt insert failed (succeeded=$succeeded): ${e.javaClass.simpleName}: ${e.message}",
                    e
                )
                runCatching {
                    errorReporter.reportException(
                        "LoginViewModel",
                        "recordLoginAttempt.insert(succeeded=$succeeded)",
                        e,
                        usernameOverride = username
                    )
                }
            }
        }
    }

    /** Stable, salted hash of (manufacturer + model + ANDROID_ID).
     *  Returns the same value on the same device across launches and
     *  app reinstalls; resets on factory reset. */
    private fun deviceFingerprint(ctx: android.content.Context): String {
        return runCatching {
            val androidId = android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            val raw = "${android.os.Build.MANUFACTURER}:${android.os.Build.MODEL}:$androidId:UniScheduler"
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .substring(0, 16)  // first 64 bits — collision-safe at our scale
        }.getOrDefault("unknown")
    }

    @kotlinx.serialization.Serializable
    private data class LoginAttemptRow(
        val username: String,
        val succeeded: Boolean,
        @kotlinx.serialization.SerialName("user_agent")    val userAgent: String,
        @kotlinx.serialization.SerialName("device_id")     val deviceId: String,
        @kotlinx.serialization.SerialName("device_model")  val deviceModel: String,
        @kotlinx.serialization.SerialName("os_version")    val osVersion: String,
        @kotlinx.serialization.SerialName("app_version")   val appVersion: String,
        val source: String,
        @kotlinx.serialization.SerialName("failure_step")  val failureStep: String? = null,
        @kotlinx.serialization.SerialName("is_emulator")   val isEmulator: Boolean? = null,
        // Public egress IP — client tarafında bilinemeyeceği için login öncesi
        // hafif bir IP-API çağrısıyla doldurulur. Başarısızlık durumunda null,
        // CTI panelinde "Bilinmiyor" olarak gösterilir.
        @kotlinx.serialization.SerialName("ip_address")    val ipAddress: String? = null
    )

    /**
     * 1.5 sn timeout ile public egress IP'sini çeker. Başarısızlık olursa null
     * döndürür — login akışını asla bloklamayız. api.ipify.org küçük (60 byte
     * civarı düz metin), CORS-free, ücretsiz, IPv4 ve IPv6 destekli.
     *
     * Privacy: bu çağrı sadece IPv4/IPv6 adresini geri verir; başka veri
     * göndermez. Yine de kurumsal ortamda firewall kapatabileceği için tüm
     * yol best-effort.
     */
    private suspend fun fetchPublicIp(): String? {
        return runCatching {
            withContext(Dispatchers.IO) {
                val url = java.net.URL("https://api.ipify.org?format=text")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent",
                    "UniScheduler/${com.unischeduler.BuildConfig.VERSION_NAME}")
                conn.inputStream.use { it.bufferedReader().readText().trim() }
                    .takeIf { it.isNotEmpty() && it.length <= 45 }  // IPv6 max 39, ipv4-mapped 45
            }
        }.getOrNull()
    }

    private fun reportValidationError(action: String, message: String, username: String) {
        viewModelScope.launch(Dispatchers.IO) {
            errorReporter.reportMessage("LoginViewModel", action, message, usernameOverride = username)
        }
    }
}

data class LoginResult(val role: String, val mustChangePassword: Boolean)
