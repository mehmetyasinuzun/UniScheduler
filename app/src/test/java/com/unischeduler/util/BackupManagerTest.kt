package com.unischeduler.util

import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Department
import com.unischeduler.data.model.OrgSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun `Backup round-trips through JSON without losing fields`() {
        val original = BackupManager.Backup(
            schemaVersion = BackupManager.SCHEMA_VERSION,
            exportedAt = "2026-05-09T15:00:00+03:00",
            appVersion = "1.0.1",
            orgId = 7,
            data = BackupManager.BackupData(
                settings = OrgSettings(orgId = 7, dayStart = "08:00", dayEnd = "18:00"),
                departments = listOf(
                    Department(id = 1, orgId = 7, name = "Bilgisayar"),
                    Department(id = 2, orgId = 7, name = "Matematik")
                ),
                courses = listOf(
                    Course(id = 10, orgId = 7, code = "CS101", name = "Algo", departmentId = 1)
                )
            )
        )
        val text = json.encodeToString(BackupManager.Backup.serializer(), original)
        val parsed = json.decodeFromString(BackupManager.Backup.serializer(), text)
        assertEquals(original, parsed)
    }

    @Test
    fun `JSON contains expected top-level keys`() {
        val backup = BackupManager.Backup(
            schemaVersion = 1,
            exportedAt = "2026-01-01T00:00:00+03:00",
            appVersion = "1.0",
            orgId = 1,
            data = BackupManager.BackupData()
        )
        val text = json.encodeToString(BackupManager.Backup.serializer(), backup)
        assertTrue(text.contains("\"schema_version\""))
        assertTrue(text.contains("\"exported_at\""))
        assertTrue(text.contains("\"app_version\""))
        assertTrue(text.contains("\"org_id\""))
        assertTrue(text.contains("\"data\""))
    }

    @Test
    fun `summarise produces multi-line counts`() {
        val backup = BackupManager.Backup(
            schemaVersion = 1, exportedAt = "x", appVersion = "1.0", orgId = 1,
            data = BackupManager.BackupData(
                departments = List(3) { Department(id = it) },
                courses = List(5) { Course(id = it) }
            )
        )
        val summary = BackupManager.summarise(backup)
        assertTrue(summary.contains("Bölüm: 3"))
        assertTrue(summary.contains("Ders: 5"))
        assertTrue(summary.contains("Hoca: 0"))
    }

    @Test
    fun `schema version constant is bumped via single source`() {
        // Sanity check that callers can rely on a stable, non-zero schema version
        assertTrue(BackupManager.SCHEMA_VERSION >= 1)
    }
}
