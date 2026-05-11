package com.tencent.bkrepo.repository.pojo.clientupgrade

data class ClientVersionConfigUpsertRequest(
    val id: String? = null,
    val productId: String,
    val platform: String,
    val arch: String,
    val targetUserId: String? = null,
    val minVersion: String? = null,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String? = null,
    val enabled: Boolean = true,
)
