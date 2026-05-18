package com.tencent.bkrepo.common.artifact.crypt

data class CryptKey(
    val key: String,
    val timestamp: Long,
    val expiredSeconds: Long
)