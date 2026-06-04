package com.tencent.bkrepo.repository.pojo.clientupgrade

import java.time.LocalDateTime

data class ClientVersionConfigVo(
    val id: String?,
    val productId: String,
    val platform: String,
    val arch: String,
    val targetUserId: String,
    val minVersion: String?,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String?,
    val enabled: Boolean,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,
)
