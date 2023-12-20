package com.tencent.bkrepo.common.storage.config

data class CompressProperties(
    /**
     * 重复率，只有超过该值，文件才会被压缩
     * */
    val ratio: Float = 0.5f,
)
