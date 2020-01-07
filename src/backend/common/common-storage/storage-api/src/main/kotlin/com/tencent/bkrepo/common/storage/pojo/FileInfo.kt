package com.tencent.bkrepo.common.storage.pojo

/**
 *
 * @author: carrypan
 * @date: 2019/12/27
 */
data class FileInfo(
    val sha256: String,
    val md5: String,
    val size: Long
)
