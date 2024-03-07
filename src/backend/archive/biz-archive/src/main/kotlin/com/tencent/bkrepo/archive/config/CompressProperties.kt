package com.tencent.bkrepo.archive.config

import org.springframework.util.unit.DataSize

data class CompressProperties(

    /**
     * 是否启用压缩
     * */
    var enabledCompress: Boolean = false,

    /**
     * compress thread num
     * */
    var compressThreads: Int = 2,

    /**
     * xz memory limit
     * */
    var xzMemoryLimit: DataSize = DataSize.ofGigabytes(1),
)
