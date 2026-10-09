package com.raviga.downwork.ui

import com.raviga.downwork.data.api.ProjectStatus
import com.raviga.downwork.data.api.ProjectSummary
import com.raviga.downwork.ui.status.StatusCopy
import com.raviga.downwork.ui.theme.Ink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusCopyTest {

    private fun policy(message: String) = com.raviga.downwork.data.api.ApiException(
        "content_rejected", 422, message,
        kotlinx.serialization.json.buildJsonObject { put("kind", kotlinx.serialization.json.JsonPrimitive("policy")) },
    )

    @Test
    fun `policy refusals get the appeal line exactly once`() {
        StatusCopy.supportEmail = "support@example.com"
        assertEquals(
            "Outside our policy. If you think this is a mistake, write to support@example.com.",
            policy("Outside our policy.").userLine(),
        )
        val already = "Outside our policy. If you think this is a mistake, write to support@example.com."
        assertEquals(already, policy(already).userLine())
        val phrased = "Outside our policy. If you think this is a mistake, email us."
        assertEquals(phrased, policy(phrased).userLine())
        StatusCopy.supportEmail = ""
    }

    @Test
    fun `detail titles match the shared vocabulary`() {
        assertEquals("Awaiting review", StatusCopy.detailTitle(ProjectStatus.SUBMITTED))
        assertEquals("Comments from the team", StatusCopy.detailTitle(ProjectStatus.CHANGES_REQUESTED))
        assertEquals("In build", StatusCopy.detailTitle(ProjectStatus.APPROVED))
        assertEquals("Not accepted", StatusCopy.detailTitle(ProjectStatus.REJECTED))
    }

    @Test
    fun `mark colours follow the design table`() {
        assertEquals(Ink.ash, StatusCopy.markColor(ProjectStatus.DRAFT))
        assertEquals(Ink.amber, StatusCopy.markColor(ProjectStatus.SUBMITTED))
        assertEquals(Ink.cobalt, StatusCopy.markColor(ProjectStatus.APPROVED))
        assertEquals(Ink.moss, StatusCopy.markColor(ProjectStatus.DELIVERED))
        assertEquals(Ink.brick, StatusCopy.markColor(ProjectStatus.REJECTED))
    }

    @Test
    fun `row lines use the shared wording`() {
        val approved = ProjectSummary(id = "p", status = ProjectStatus.APPROVED, estimatedDeliveryDate = "2026-11-21")
        assertTrue(StatusCopy.rowLine(approved).startsWith("Approved, in build. Ready by "))
        assertEquals("Submitted, awaiting review", StatusCopy.rowLine(ProjectSummary(id = "p", status = ProjectStatus.SUBMITTED)))
        assertEquals("Cancelled, credits returned", StatusCopy.rowLine(ProjectSummary(id = "p", status = ProjectStatus.CANCELLED)))
    }

    @Test
    fun `unknown error codes fall back to the server message`() {
        assertEquals("GitHub is unavailable", StatusCopy.errorLine("github_unavailable", "GitHub is unavailable"))
        assertEquals("Something went wrong. Try again.", StatusCopy.errorLine("github_unavailable", null))
        assertTrue(StatusCopy.errorLine("network", "ignored").startsWith("Couldn't reach DownWork"))
    }
}
