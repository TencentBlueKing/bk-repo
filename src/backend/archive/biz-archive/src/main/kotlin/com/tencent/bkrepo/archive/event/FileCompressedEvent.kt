package com.tencent.bkrepo.archive.event

import com.tencent.bkrepo.common.storage.monitor.Throughput

/**
 * 归档压缩事件
 * */
data class FileCompressedEvent(
    val sha256: String,
    val uncompressed: Long,
    val compressed: Long,
    val throughput: Throughput,
)
