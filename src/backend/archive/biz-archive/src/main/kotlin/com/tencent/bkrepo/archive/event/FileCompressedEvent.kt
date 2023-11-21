package com.tencent.bkrepo.archive.event

import com.tencent.bkrepo.common.storage.monitor.Throughput

/**
 * 文件归档事件
 * */
data class FileCompressedEvent(
    val sha256: String,
    val uncompressed: Long,
    val compressed: Long,
    val throughput: Throughput,
)
