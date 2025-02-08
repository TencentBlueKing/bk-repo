package com.tencent.bkrepo.common.metadata.pojo.node

import java.time.LocalDateTime

data class RestoreContext(
    val projectId: String,
    val repoName: String,
    val rootFullPath: String,
    val deletedTime: LocalDateTime,
    val conflictStrategy: ConflictStrategy,
    val operator: String,
    var restoreCount: Long = 0L,
    var conflictCount: Long = 0L,
    var skipCount: Long = 0L
)
