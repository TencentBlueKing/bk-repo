package com.tencent.bkrepo.archive.event

import com.tencent.bkrepo.common.storage.monitor.Throughput

/**
 * 文件归档事件
 * */
data class FileArchivedEvent(
    val sha256: String,
    val storageCredentialsKey: String?,
    val throughput: Throughput,
)
