package com.tencent.bkrepo.archive.job.archive

import com.tencent.bkrepo.archive.model.TArchiveFile
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * 归档文件包装器
 * */
data class ArchiveFileWrapper(
    /**
     * 待归档的文件
     * */
    val archiveFile: TArchiveFile,
    /**
     * 源文件路径
     * */
    var srcFilePath: Path? = null,
    /**
     * 压缩文件路径
     * */
    var compressedFilePath: Path? = null,
    /**
     * 错误信息
     * */
    var throwable: Throwable? = null,
    /**
     * 开始归档时间
     * */
    var startTime: LocalDateTime? = null,
)
