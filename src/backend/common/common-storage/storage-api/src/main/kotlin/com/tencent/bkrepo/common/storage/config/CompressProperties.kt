package com.tencent.bkrepo.common.storage.config

data class CompressProperties(
    /**
     * 重复率，只有超过该值，文件才会被压缩
     * */
    val ratio: Float = 0.5f,

    /**
     * 处理文件压缩操作的临时目录
     */
    var path: String = System.getProperty("java.io.tmpdir"),
)
