package com.raviga.app

import com.raviga.app.util.Time
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TimeTest {

    @Test
    fun `parses instants and dates`() {
        assertNotNull(Time.parse("2026-10-09T04:30:00Z"))
        assertNotNull(Time.parse("2026-10-09"))
        assertNull(Time.parse(""))
        assertNull(Time.parse("not a date"))
    }

    @Test
    fun `working days skip weekends`() {
        val friday = LocalDate.of(2026, 10, 9)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        val plusOne = Time.addWorkingDays(friday, 1)
        assertEquals(DayOfWeek.MONDAY, plusOne.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 23), Time.addWorkingDays(friday, 10))
    }
}
