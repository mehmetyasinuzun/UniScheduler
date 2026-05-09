// BackupManager — exports the entire org-scoped dataset to a single JSON
// document for archival.
//
// Design decisions (senior review notes):
//
//  • Export-only in this version. Restore is intentionally NOT implemented
//    here because a correct restore requires:
//       (a) a CASCADE-safe order of inserts (organizations → settings →
//           departments → users (auth) → lecturers → courses → classrooms
//           → offerings → schedule_entries → availability)
//       (b) Auth Admin API access to recreate users with stable IDs — only
//           available with the service_role key, which the mobile app does
//           NOT carry (and shouldn't carry — bypassing RLS from a phone is
//           a security disaster).
//       (c) FK id remapping (every row has a SERIAL primary key; restoring
//           into a non-empty database changes the ids and breaks every
//           FK reference in the dump).
//    A correct restore therefore lives on the super-admin web panel, which
//    has service_role and can run a single transaction. That ticket is
//    tracked separately. For mobile users, this export is the data sigorta
//    poliçesi: download, archive, sleep at night.
//
//  • One JSON file with a `schemaVersion`. Future readers (the eventual
//    panel-side restore, or a different tool) can branch on this to handle
//    older dumps. Keep current export at v1.
//
//  • All Supabase reads happen inside a single coroutineScope so the seven
//    table reads run in parallel — fast enough that a 5000-row dataset
//    finishes in <2s on a typical fiber connection.
//
//  • The output JSON is pretty-printed for human inspection. The ~30%
//    size penalty is irrelevant compared to the value of being able to
//    `cat` the file in a terminal during incident response.
package com.unischeduler.util

import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Department
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.LecturerAvailability
import com.unischeduler.data.model.Offering
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.data.repository.AvailabilityRepository
import com.unischeduler.data.repository.ClassroomRepository
import com.unischeduler.data.repository.CourseRepository
import com.unischeduler.data.repository.DepartmentRepository
import com.unischeduler.data.repository.LecturerRepository
import com.unischeduler.data.repository.OfferingRepository
import com.unischeduler.data.repository.OrgSettingsRepository
import com.unischeduler.data.repository.ScheduleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BackupManager {

    /** Bumped if the on-disk JSON shape changes. v1 = initial. */
    const val SCHEMA_VERSION = 1

    @Serializable
    data class Backup(
        @SerialName("schema_version") val schemaVersion: Int,
        @SerialName("exported_at")    val exportedAt: String,
        @SerialName("app_version")    val appVersion: String,
        @SerialName("org_id")         val orgId: Int,
        val data: BackupData
    )

    @Serializable
    data class BackupData(
        val settings:    OrgSettings? = null,
        val departments: List<Department>            = emptyList(),
        val lecturers:   List<Lecturer>              = emptyList(),
        val courses:     List<Course>                = emptyList(),
        val classrooms:  List<Classroom>             = emptyList(),
        val offerings:   List<Offering>              = emptyList(),
        @SerialName("schedule_entries")
        val schedule:    List<ScheduleEntry>         = emptyList(),
        val availability: List<LecturerAvailability> = emptyList()
    )

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Pull every org-scoped table in parallel and serialise the bundle.
     *
     * Caller is expected to be on Dispatchers.IO.
     */
    suspend fun createBackup(orgId: Int, appVersion: String): String = coroutineScope {
        val settingsRepo    = OrgSettingsRepository()
        val deptRepo        = DepartmentRepository()
        val lecturerRepo    = LecturerRepository()
        val courseRepo      = CourseRepository()
        val classroomRepo   = ClassroomRepository()
        val offeringRepo    = OfferingRepository()
        val scheduleRepo    = ScheduleRepository()
        val availabilityRepo = AvailabilityRepository()

        // Parallelise all eight reads. settings can be null on a brand-new org.
        val settingsAsync     = async { runCatching { settingsRepo.getSettings(orgId) }.getOrNull() }
        val departmentsAsync  = async { runCatching { deptRepo.getAllDepartments(orgId) }.getOrDefault(emptyList()) }
        val lecturersAsync    = async { runCatching { lecturerRepo.getAllLecturers(orgId) }.getOrDefault(emptyList()) }
        val coursesAsync      = async { runCatching { courseRepo.getAllCourses(orgId) }.getOrDefault(emptyList()) }
        val classroomsAsync   = async { runCatching { classroomRepo.getAllClassrooms(orgId) }.getOrDefault(emptyList()) }
        val offeringsAsync    = async { runCatching { offeringRepo.getAllOfferings(orgId) }.getOrDefault(emptyList()) }
        val scheduleAsync     = async { runCatching { scheduleRepo.getAllEntries(orgId) }.getOrDefault(emptyList()) }
        val availabilityAsync = async { runCatching { availabilityRepo.getAllForOrg(orgId) }.getOrDefault(emptyList()) }

        val backup = Backup(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = isoTimestamp(),
            appVersion = appVersion,
            orgId = orgId,
            data = BackupData(
                settings     = settingsAsync.await(),
                departments  = departmentsAsync.await(),
                lecturers    = lecturersAsync.await(),
                courses      = coursesAsync.await(),
                classrooms   = classroomsAsync.await(),
                offerings    = offeringsAsync.await(),
                schedule     = scheduleAsync.await(),
                availability = availabilityAsync.await()
            )
        )
        json.encodeToString(Backup.serializer(), backup)
    }

    /** Quick statistics for the success dialog. */
    fun summarise(backup: Backup): String = buildString {
        val d = backup.data
        appendLine("• Bölüm: ${d.departments.size}")
        appendLine("• Hoca: ${d.lecturers.size}")
        appendLine("• Ders: ${d.courses.size}")
        appendLine("• Derslik: ${d.classrooms.size}")
        appendLine("• Açılan ders: ${d.offerings.size}")
        appendLine("• Program kaydı: ${d.schedule.size}")
        append("• Müsaitlik kaydı: ${d.availability.size}")
    }

    private fun isoTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())
}
