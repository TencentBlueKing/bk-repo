package com.tencent.bkrepo.archive.pojo

import com.tencent.bkrepo.common.api.constant.StringPool

class CompressedInfo(
    val status: Int, // 0 压缩中， 1 压缩成功
    val uncompressedSize: Long,
    val compressedSize: Long,
    val ratio: String = StringPool.calculateRatio(uncompressedSize, compressedSize),
)
