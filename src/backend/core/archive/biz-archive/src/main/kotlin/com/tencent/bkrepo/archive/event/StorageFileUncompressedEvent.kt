package com.tencent.bkrepo.archive.event

import com.tencent.bkrepo.common.storage.monitor.Throughput

/**
 * 存储文件解压事件
 * */
data class StorageFileUncompressedEvent(
    val sha256: String,
    val uncompressed: Long,
    val compressed: Long,
    val storageCredentialsKey: String?,
    val throughput: Throughput,
)
