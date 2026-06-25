package com.tencent.bkrepo.skill.pojo.response

data class ClawHubResolveResponse(
    val match: ClawHubResolveVersion? = null,
    val latestVersion: ClawHubResolveVersion? = null,
)

data class ClawHubResolveVersion(
    val version: String,
)
