package com.unischeduler.util

import android.app.Application
import android.os.Build
import com.unischeduler.BuildConfig
import com.unischeduler.data.model.ClientErrorLogInsert
import com.unischeduler.data.repository.ErrorLogRepository

class ErrorReporter(app: Application) {

    private val repo = ErrorLogRepository()
    private val session = SessionManager(app)

    suspend fun reportException(
        screen: String,
        action: String,
        error: Throwable,
        usernameOverride: String? = null
    ) {
        val message = error.message ?: error::class.java.simpleName
        reportMessage(screen, action, message, error.stackTraceToString(), usernameOverride)
    }

    suspend fun reportMessage(
        screen: String,
        action: String,
        message: String,
        stackTrace: String? = null,
        usernameOverride: String? = null
    ) {
        val log = ClientErrorLogInsert(
            orgId = session.orgId.takeIf { it > 0 },
            userId = session.userId.takeIf { it.isNotBlank() },
            username = usernameOverride?.takeIf { it.isNotBlank() }
                ?: session.username.takeIf { it.isNotBlank() },
            role = session.role.takeIf { it.isNotBlank() },
            screen = screen,
            action = action,
            message = message,
            stackTrace = stackTrace,
            appVersion = BuildConfig.VERSION_NAME,
            deviceModel = Build.MODEL,
            osVersion = Build.VERSION.RELEASE
        )
        runCatching { repo.insert(log) }
    }
}
