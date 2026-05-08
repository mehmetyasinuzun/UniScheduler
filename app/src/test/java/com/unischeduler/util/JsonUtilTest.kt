package com.unischeduler.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonUtilTest {

    @Test
    fun `extractIntsFromColumn returns ids when column has values`() {
        val raw = """[{"course_id":1},{"course_id":2},{"course_id":3}]"""
        val ids = JsonUtil.extractIntsFromColumn(raw, "course_id")
        assertEquals(setOf(1, 2, 3), ids)
    }

    @Test
    fun `extractIntsFromColumn skips null entries`() {
        val raw = """[{"lecturer_id":1},{"lecturer_id":null},{"lecturer_id":4}]"""
        val ids = JsonUtil.extractIntsFromColumn(raw, "lecturer_id")
        assertEquals(setOf(1, 4), ids)
    }

    @Test
    fun `extractIntsFromColumn handles empty list`() {
        val ids = JsonUtil.extractIntsFromColumn("[]", "course_id")
        assertTrue(ids.isEmpty())
    }

    @Test
    fun `extractIntsFromColumn handles blank input`() {
        val ids = JsonUtil.extractIntsFromColumn("", "course_id")
        assertTrue(ids.isEmpty())
    }

    @Test
    fun `extractIntsFromColumn ignores missing column`() {
        val raw = """[{"other":1},{"another":2}]"""
        val ids = JsonUtil.extractIntsFromColumn(raw, "course_id")
        assertTrue(ids.isEmpty())
    }

    @Test
    fun `extractIntsFromColumn deduplicates`() {
        val raw = """[{"id":1},{"id":1},{"id":2}]"""
        val ids = JsonUtil.extractIntsFromColumn(raw, "id")
        assertEquals(setOf(1, 2), ids)
    }

    @Test
    fun `rowCount counts top-level objects`() {
        assertEquals(3, JsonUtil.rowCount("""[{"a":1},{"a":2},{"a":3}]"""))
        assertEquals(0, JsonUtil.rowCount("[]"))
        assertEquals(0, JsonUtil.rowCount(""))
    }
}
