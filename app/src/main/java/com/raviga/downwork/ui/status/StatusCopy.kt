package com.raviga.downwork.ui.status

import androidx.compose.ui.graphics.Color
import com.raviga.downwork.data.api.Project
import com.raviga.downwork.data.api.ProjectStatus
import com.raviga.downwork.data.api.ProjectSummary
import com.raviga.downwork.ui.theme.Ink
import com.raviga.downwork.util.Time

/** The shared status vocabulary from DESIGN.md: same words on iOS, Android and in push copy. */
object StatusCopy {

    fun markColor(status: String): Color = when (status) {
        ProjectStatus.DRAFT, ProjectStatus.CANCELLED -> Ink.ash
        ProjectStatus.SUBMITTED, ProjectStatus.CHANGES_REQUESTED, ProjectStatus.REVISION_REQUESTED -> Ink.amber
        ProjectStatus.APPROVED -> Ink.cobalt
        ProjectStatus.DELIVERED, ProjectStatus.ACCEPTED -> Ink.moss
        ProjectStatus.REJECTED -> Ink.brick
        else -> Ink.ash
    }

    fun detailTitle(status: String): String = when (status) {
        ProjectStatus.DRAFT -> "Draft"
        ProjectStatus.SUBMITTED -> "Awaiting review"
        ProjectStatus.CHANGES_REQUESTED -> "Comments from the team"
        ProjectStatus.APPROVED -> "In build"
        ProjectStatus.DELIVERED -> "Delivered"
        ProjectStatus.REVISION_REQUESTED -> "Revision in progress"
        ProjectStatus.ACCEPTED -> "Accepted"
        ProjectStatus.REJECTED -> "Not accepted"
        ProjectStatus.CANCELLED -> "Cancelled"
        else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun rowLine(summary: ProjectSummary): String = rowLine(summary.status, summary.updatedAt, summary.estimatedDeliveryDate)

    fun rowLine(project: Project): String = rowLine(project.status, project.updatedAt, project.timeline?.estimatedDeliveryDate)

    fun rowLine(status: String, updatedAt: String?, estimatedDeliveryDate: String?): String = when (status) {
        ProjectStatus.DRAFT -> "Draft, edited ${Time.relative(updatedAt)}"
        ProjectStatus.SUBMITTED -> "Submitted, awaiting review"
        ProjectStatus.CHANGES_REQUESTED -> "The team left comments"
        ProjectStatus.APPROVED -> if (estimatedDeliveryDate != null) "Approved, in build. Ready by ${Time.shortDate(estimatedDeliveryDate)}" else "Approved, in build"
        ProjectStatus.DELIVERED -> "Delivered, review the repo"
        ProjectStatus.REVISION_REQUESTED -> "Revision requested"
        ProjectStatus.ACCEPTED -> "Accepted on ${Time.shortDate(updatedAt)}"
        ProjectStatus.REJECTED -> "Not accepted, credits returned"
        ProjectStatus.CANCELLED -> "Cancelled, credits returned"
        else -> detailTitle(status)
    }

    fun pushLine(status: String): String = when (status) {
        ProjectStatus.CHANGES_REQUESTED -> "The team left comments on your brief."
        ProjectStatus.APPROVED -> "Approved. We have started building."
        ProjectStatus.DELIVERED -> "Your code is ready. Review the repo."
        ProjectStatus.REJECTED -> "Not accepted. Your credits have been returned."
        ProjectStatus.ACCEPTED -> "Accepted. Thank you."
        else -> "Your project was updated."
    }

    /** Maps an API error code to the plain copy the design asks for. */
    fun errorLine(code: String, fallback: String?): String = when (code) {
        "network" -> "Couldn't reach DownWork. Check your connection and try again."
        "decode" -> "DownWork sent something this version can't read. Try again later."
        "consent_required" -> "Allow AI processing before continuing."
        "insufficient_credits" -> fallback ?: "You need more credits to submit."
        "version_conflict" -> "The brief changed elsewhere. Reload and try again."
        "quote_stale" -> "The brief changed since this quote. Get a new quote."
        "document_locked" -> "This brief is locked because it has been submitted."
        "github_user_not_found" -> "No GitHub user with that name. Check the spelling."
        "invalid_argument" -> fallback ?: "Something in the form isn't right."
        "invalid_state" -> fallback ?: "That isn't possible at this stage."
        "revision_limit_reached" -> "Your included revision rounds are used up. Email us and we can add more."
        "active_projects" -> "Finish or cancel your active projects before deleting your data."
        "aws_role_not_ready" -> "AWS hasn't finished creating the role yet. Give it a minute and check again."
        "otp_invalid" -> "That recovery key doesn't match. Check it and try again."
        "upgrade_required" -> "Update DownWork from the Play Store to continue."
        "rate_limited" -> "Too many requests. Wait a moment and try again."
        "ai_unavailable" -> "The AI is busy right now. Try again in a moment."
        "not_found" -> "That no longer exists."
        "job_timeout" -> "This is taking longer than usual. Check back in a few minutes."
        "unauthenticated" -> "This phone is no longer linked. Move your projects here with a recovery key."
        else -> fallback?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
    }
}
