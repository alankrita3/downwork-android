package com.raviga.downwork.data

import com.raviga.downwork.data.api.Document
import com.raviga.downwork.data.api.SectionIds
import com.raviga.downwork.data.demo.BriefWriter
import com.raviga.downwork.data.demo.DemoApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefWriterTest {

    private val salon = "I want an app for my salon in Delhi called GlowBook. Customers should be able to book appointments, pay online with UPI, and get reminders. There should be an admin dashboard for the staff."

    @Test
    fun `writes all twelve sections in order`() {
        val draft = BriefWriter.write(listOf(salon))
        assertEquals(SectionIds.ordered, draft.sections.map { it.id })
        assertTrue(draft.sections.all { it.body.isNotBlank() })
        assertEquals("GlowBook", draft.title)
    }

    @Test
    fun `detects features and platforms`() {
        val draft = BriefWriter.write(listOf(salon))
        val must = draft.sections.first { it.id == SectionIds.FEATURES_MUST }.body
        assertTrue(must.contains("Booking", ignoreCase = true))
        assertTrue(must.contains("Payments", ignoreCase = true))
        val platforms = draft.sections.first { it.id == SectionIds.PLATFORMS }.body
        assertTrue(platforms.contains("Admin panel"))
    }

    @Test
    fun `quote lands in a bracket and respects the timeline rule`() {
        val draft = BriefWriter.write(listOf(salon))
        val doc = Document(version = 1, title = draft.title, sections = draft.sections)
        val q = BriefWriter.quote(doc, DemoApi.demoConfig())
        // Contract v0.7 pricing (x0.38, at least 5) and the founder's pace: days = ceil(credits / 3).
        assertTrue(q.credits >= 5)
        assertEquals((q.credits + 2) / 3, q.workingDays)
        assertTrue(q.bracketId != null)
        assertEquals(q.credits, q.breakdown.sumOf { it.second })
    }

    @Test
    fun `regenerate shorter halves the bullets`() {
        val draft = BriefWriter.write(listOf(salon))
        val section = draft.sections.first { it.id == SectionIds.NON_FUNCTIONAL }
        val shorter = BriefWriter.regenerate(section, listOf(salon), "make it shorter")
        assertTrue(shorter.body.lines().size < section.body.lines().size)
    }

    @Test
    fun `append only adds new bullets`() {
        val first = BriefWriter.write(listOf(salon))
        val doc = Document(version = 1, title = first.title, sections = first.sections)
        val merged = BriefWriter.append(doc, listOf(salon, "Also add a loyalty points and rewards feature."))
        val nice = merged.first { it.id == SectionIds.FEATURES_NICE }.body
        assertTrue(nice.contains("rewards", ignoreCase = true))
        val screens = merged.first { it.id == SectionIds.SCREENS }.body
        assertTrue(screens.contains("Welcome"))
    }
}
