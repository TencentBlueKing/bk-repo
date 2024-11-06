package com.tencent.bkrepo.archive.event

import com.tencent.bkrepo.common.storage.monitor.Throughput

/**
 * 文件恢复事件
 * */
data class FileRestoredEvent(
    val sha256: String,
    val storageCredentialsKey: String?,
    val throughput: Throughput,
)
