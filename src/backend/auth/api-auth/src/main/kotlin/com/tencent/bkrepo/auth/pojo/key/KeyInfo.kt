package com.tencent.bkrepo.auth.pojo.key

import java.time.LocalDateTime

data class KeyInfo(
    val id: String,
    val name: String,
    /** SSH 公钥原文，联邦同步时需传递以便目标端保存完整记录 */
    val key: String,
    val fingerprint: String,
    val userId: String,
    val createAt: LocalDateTime
)
