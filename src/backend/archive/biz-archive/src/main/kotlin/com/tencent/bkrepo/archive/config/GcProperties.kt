package com.tencent.bkrepo.archive.config

import org.springframework.util.unit.DataSize
import java.time.Duration

data class GcProperties(
    var path: String = System.getProperty("java.io.tmpdir"),
    var diffThreads: Int = 1, // 文件差分：CPU 内存 IO
    var ratio: Float = 0.5f, // 重复率阈值
    var signFileCacheTime: Duration = Duration.ofHours(6), // 签名文件缓存事件
    var bigChecksumFileThreshold: DataSize = DataSize.ofMegabytes(100),
    var bigFileCompressPoolSize: Int = 1,
    var maxConcurrency: Int = 100,
)
