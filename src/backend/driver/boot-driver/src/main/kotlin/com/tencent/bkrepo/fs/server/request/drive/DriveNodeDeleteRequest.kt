package com.tencent.bkrepo.fs.server.request.drive

import java.time.LocalDateTime

/**
 * DriveNode删除请求，由于存在硬链接，虽然后端存储的ino是唯一的，但是客户端的不同drive-node可能使用相同ino
 * 因此客户端无法使用ino进行删除，必须使用nodeId
 */
data class DriveNodeDeleteRequest(
    val projectId: String,
    val repoName: String,
    val nodeId: String,
    val lastModifiedDate: LocalDateTime? = null,
    val force: Boolean = false,
)
