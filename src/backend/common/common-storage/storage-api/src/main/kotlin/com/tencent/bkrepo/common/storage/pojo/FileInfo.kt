package com.tencent.bkrepo.common.storage.pojo

data class FileInfo(
    val sha256: String,
    val md5: String,
    val size: Long
)
