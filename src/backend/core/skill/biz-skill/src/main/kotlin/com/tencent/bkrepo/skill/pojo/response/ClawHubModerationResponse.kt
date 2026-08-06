package com.tencent.bkrepo.skill.pojo.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ClawHubModerationResponse(
    val moderation: ClawHubModeration?,
)

data class ClawHubModeration(
    val isSuspicious: Boolean,
    val isMalwareBlocked: Boolean,
    val verdict: String,
    val reasonCodes: List<String>,
    val updatedAt: Long? = null,
    val engineVersion: String? = null,
    val summary: String? = null,
    val legacyReason: String? = null,
    val evidence: List<ClawHubModerationEvidence> = emptyList(),
)

data class ClawHubModerationEvidence(
    val code: String,
    val severity: String,
    val file: String,
    val line: Int,
    val message: String,
    val evidence: String,
)
