package com.unischeduler.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.domain.repository.ScheduleRepository
import com.unischeduler.domain.repository.SettingsRepository
import com.unischeduler.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class ScheduleReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val settings = settingsRepository.settings.first()
            if (!settings.notificationsEnabled) return Result.success()

            val user = authRepository.getCurrentUser() ?: return Result.success()
            val advanceMinutes = settings.notificationAdvanceMinutes

            // Get current day and time to find upcoming classes
            val calendar = java.util.Calendar.getInstance()
            val currentDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 1=Mon
            val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(java.util.Calendar.MINUTE)
            val currentTotalMinutes = currentHour * 60 + currentMinute

            val assignments = when {
                user.departmentId != null -> scheduleRepository.getAssignmentsByDepartment(user.departmentId)
                else -> emptyList()
            }

            assignments
                .filter { it.dayOfWeek == currentDayOfWeek }
                .forEach { assignment ->
                    // slotIndex → approximate time: slot 0 = 08:00, slot 1 = 09:00, etc.
                    val classStartMinutes = 480 + (assignment.slotIndex * 60)
                    val diff = classStartMinutes - currentTotalMinutes

                    if (diff in (advanceMinutes - 1)..(advanceMinutes + 1)) {
                        NotificationHelper.showClassNotification(
                            context = context,
                            notificationId = assignment.id,
                            courseName = assignment.courseName,
                            lecturerName = assignment.lecturerName,
                            minutesBefore = advanceMinutes
                        )
                    }
                }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
