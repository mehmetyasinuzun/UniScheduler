package com.unischeduler.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])

/**
 * Round-trips data through MiniXlsxWriter → MiniXlsxReader. If both
 * sides produce/consume Office Open XML correctly, the data we feed in
 * must come back out identical (modulo number-format normalisation).
 */
class MiniXlsxRoundTripTest {

    @Test
    fun `cellRef builder matches reader`() {
        // Sanity: the writer's column-to-letter generator must produce
        // refs the reader can parse back.
        for (col in 0..100) {
            val ref = MiniXlsxWriter.cellRef(col, 1)
            assertEquals("col $col round-trip", col, MiniXlsxReader.colIndexFromRef(ref))
        }
    }

    @Test
    fun `string columns survive round trip`() {
        val headers = listOf("Code", "Name", "Department")
        val rows = listOf(
            listOf(
                MiniXlsxWriter.Cell.Str("BIL101"),
                MiniXlsxWriter.Cell.Str("Algoritmalar"),
                MiniXlsxWriter.Cell.Str("Bilgisayar")
            ),
            listOf(
                MiniXlsxWriter.Cell.Str("MAT202"),
                MiniXlsxWriter.Cell.Str("Olasılık ve İstatistik"),
                MiniXlsxWriter.Cell.Str("Matematik")
            )
        )

        val bytes = ByteArrayOutputStream().also {
            MiniXlsxWriter.write(it, "Test", headers, rows)
        }.toByteArray()

        val parsed = ByteArrayInputStream(bytes).use { MiniXlsxReader.read(it) }
        assertEquals(3, parsed.size)  // header + 2 data
        assertEquals(headers, parsed[0])
        assertEquals(listOf("BIL101", "Algoritmalar", "Bilgisayar"), parsed[1])
        assertEquals(listOf("MAT202", "Olasılık ve İstatistik", "Matematik"), parsed[2])
    }

    @Test
    fun `numbers come back as strings the parser can re-parse`() {
        val headers = listOf("Code", "Capacity")
        val rows = listOf(
            listOf(MiniXlsxWriter.Cell.Str("D101"), MiniXlsxWriter.Cell.Num(40.0)),
            listOf(MiniXlsxWriter.Cell.Str("L201"), MiniXlsxWriter.Cell.Num(28.0))
        )

        val bytes = ByteArrayOutputStream().also {
            MiniXlsxWriter.write(it, "Classrooms", headers, rows)
        }.toByteArray()

        val parsed = ByteArrayInputStream(bytes).use { MiniXlsxReader.read(it) }
        assertEquals("40", parsed[1][1])  // integer, no .0
        assertEquals("28", parsed[2][1])
    }

    @Test
    fun `xml special chars in strings are escaped + decoded correctly`() {
        val tricky = "A & B <test> \"quoted\" 'apos'"
        val headers = listOf("text")
        val rows = listOf(listOf<MiniXlsxWriter.Cell>(MiniXlsxWriter.Cell.Str(tricky)))

        val bytes = ByteArrayOutputStream().also {
            MiniXlsxWriter.write(it, "S", headers, rows)
        }.toByteArray()

        val parsed = ByteArrayInputStream(bytes).use { MiniXlsxReader.read(it) }
        assertEquals(tricky, parsed[1][0])
    }

    @Test
    fun `empty cells round-trip as empty strings`() {
        val headers = listOf("a", "b", "c")
        val rows = listOf(
            listOf<MiniXlsxWriter.Cell>(
                MiniXlsxWriter.Cell.Str("X"),
                MiniXlsxWriter.Cell.Str(""),
                MiniXlsxWriter.Cell.Str("Y")
            )
        )

        val bytes = ByteArrayOutputStream().also {
            MiniXlsxWriter.write(it, "E", headers, rows)
        }.toByteArray()

        val parsed = ByteArrayInputStream(bytes).use { MiniXlsxReader.read(it) }
        // Reader pads missing columns with empty strings to keep
        // indices aligned with the header row width.
        assertTrue("Row width >= 3 expected, got ${parsed[1]}", parsed[1].size >= 3)
        assertEquals("X", parsed[1][0])
        assertEquals("",  parsed[1][1])
        assertEquals("Y", parsed[1][2])
    }
}
