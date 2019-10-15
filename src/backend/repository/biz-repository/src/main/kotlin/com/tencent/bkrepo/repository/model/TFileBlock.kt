package com.tencent.bkrepo.repository.model

/**
 * 文件分块信息
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
data class TFileBlock(
    var sequence: Int,
    var size: Long,
    var sha256: String
)
