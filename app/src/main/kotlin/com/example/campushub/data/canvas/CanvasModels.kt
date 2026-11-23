package com.example.campushub.data.canvas

import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CanvasUser(
  val id: Long,
  val name: String = "",
  @SerialName("short_name") val shortName: String? = null,
  @SerialName("primary_email") val primaryEmail: String? = null,
)

@Serializable
data class CanvasCourse(
  val id: Long,
  val name: String = "",
  @SerialName("course_code") val courseCode: String? = null,
  @SerialName("workflow_state") val workflowState: String? = null,
  @SerialName("access_restricted_by_date") val accessRestrictedByDate: Boolean = false,
)

@Serializable
data class CanvasPlannerRawItem(
  @SerialName("plannable_type") val plannableType: String = "",
  @SerialName("plannable_id") val plannableId: Long = 0L,
  @SerialName("plannable_date") val plannableDate: String? = null,
  @SerialName("course_id") val courseId: Long? = null,
  @SerialName("html_url") val htmlUrl: String? = null,
  @SerialName("context_name") val contextName: String? = null,
  val plannable: CanvasPlannableDetail? = null,
  val submissions: CanvasPlannerSubmission? = null,
  @SerialName("planner_override") val plannerOverride: CanvasPlannerOverride? = null,
)

@Serializable
data class CanvasPlannableDetail(
  val id: Long? = null,
  val title: String? = null,
  @SerialName("due_at") val dueAt: String? = null,
  @SerialName("points_possible") val pointsPossible: Double? = null,
)

@Serializable
data class CanvasPlannerSubmission(
  val submitted: Boolean = false,
  val graded: Boolean = false,
  val missing: Boolean = false,
)

@Serializable
data class CanvasPlannerOverride(
  @SerialName("marked_complete") val markedComplete: Boolean = false,
)

@Serializable
data class CanvasTodoRawItem(
  val type: String = "",
  @SerialName("course_id") val courseId: Long? = null,
  @SerialName("context_name") val contextName: String? = null,
  @SerialName("html_url") val htmlUrl: String? = null,
  val assignment: CanvasAssignmentRaw? = null,
)

@Serializable
data class CanvasAssignmentRaw(
  val id: Long = 0L,
  @SerialName("course_id") val courseId: Long? = null,
  val name: String = "",
  val description: String? = null,
  @SerialName("due_at") val dueAt: String? = null,
  @SerialName("points_possible") val pointsPossible: Double? = null,
  @SerialName("submission_types") val submissionTypes: List<String> = emptyList(),
  @SerialName("html_url") val htmlUrl: String? = null,
  val submission: CanvasSubmissionRaw? = null,
)

@Serializable
data class CanvasSubmissionRaw(
  @SerialName("workflow_state") val workflowState: String? = null,
  @SerialName("submitted_at") val submittedAt: String? = null,
  val score: Double? = null,
  val grade: String? = null,
  val late: Boolean = false,
  val missing: Boolean = false,
  @SerialName("submission_comments") val submissionComments: List<CanvasSubmissionCommentRaw> = emptyList(),
)

@Serializable
data class CanvasSubmissionCommentRaw(
  @SerialName("author_name") val authorName: String? = null,
  val comment: String? = null,
)

@Serializable
data class CanvasAnnouncementRaw(
  val id: Long = 0L,
  val title: String = "",
  val message: String? = null,
  @SerialName("posted_at") val postedAt: String? = null,
  @SerialName("html_url") val htmlUrl: String? = null,
  val author: CanvasAnnouncementAuthor? = null,
)

@Serializable
data class CanvasAnnouncementAuthor(
  @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class CanvasModuleRaw(
  val id: Long = 0L,
  val name: String = "",
  val items: List<CanvasModuleItemRaw> = emptyList(),
)

@Serializable
data class CanvasModuleItemRaw(
  val id: Long = 0L,
  val title: String = "",
  val type: String = "",
  @SerialName("content_id") val contentId: Long? = null,
  @SerialName("html_url") val htmlUrl: String? = null,
  val url: String? = null,
)

@Serializable
data class CanvasFileRaw(
  val id: Long = 0L,
  @SerialName("display_name") val displayName: String? = null,
  val filename: String? = null,
  val size: Long = 0L,
  @SerialName("content-type") val contentType: String? = null,
  val url: String? = null,
)

data class CanvasTaskItem(
  val externalId: String,
  val title: String,
  val courseId: Long?,
  val assignmentId: Long?,
  val courseName: String,
  val dueDate: LocalDate?,
  val dueTimeText: String,
  val htmlUrl: String?,
  val isSubmitted: Boolean,
  val plannableType: String,
  val pointsPossible: Double? = null,
  val score: Double? = null,
  val grade: String? = null,
)

data class CanvasAssignmentDetail(
  val id: Long,
  val courseId: Long,
  val courseName: String,
  val title: String,
  val descriptionText: String,
  val dueDateText: String,
  val pointsPossible: Double?,
  val submissionTypes: List<String>,
  val workflowState: String,
  val isSubmitted: Boolean,
  val score: Double?,
  val grade: String?,
  val isLate: Boolean,
  val isMissing: Boolean,
  val comments: List<String>,
  val htmlUrl: String?,
)

data class CanvasAnnouncement(
  val id: Long,
  val title: String,
  val authorName: String,
  val postedAtText: String,
  val bodyText: String,
  val htmlUrl: String?,
)

data class CanvasModuleItem(
  val id: Long,
  val title: String,
  val type: String,
  val contentId: Long?,
  val htmlUrl: String?,
  val apiUrl: String?,
)

data class CanvasModule(
  val id: Long,
  val name: String,
  val items: List<CanvasModuleItem>,
)

data class CanvasFileItem(
  val id: Long,
  val displayName: String,
  val sizeBytes: Long,
  val mimeType: String?,
  val downloadUrl: String?,
)

data class CanvasCourseDetailData(
  val course: CanvasCourse,
  val announcements: List<CanvasAnnouncement>,
  val assignments: List<CanvasTaskItem>,
  val modules: List<CanvasModule>,
  val files: List<CanvasFileItem>,
)

data class CanvasDashboardData(
  val user: CanvasUser,
  val courses: List<CanvasCourse>,
  val tasks: List<CanvasTaskItem>,
  val syncedAtEpochMillis: Long = System.currentTimeMillis(),
  val usedCookieFallback: Boolean = false,
)
