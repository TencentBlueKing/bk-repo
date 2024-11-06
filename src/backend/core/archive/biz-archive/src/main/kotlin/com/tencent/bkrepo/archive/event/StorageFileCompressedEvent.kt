package com.tencent.bkrepo.archive.event

import com.tencent.bkrepo.common.storage.monitor.Throughput

/**
 * 存储文件压缩事件
 * */
data class StorageFileCompressedEvent(
    val sha256: String,
    val baseSha256: String,
    val uncompressed: Long,
    val compressed: Long,
    val storageCredentialsKey: String?,
    val throughput: Throughput,
)
