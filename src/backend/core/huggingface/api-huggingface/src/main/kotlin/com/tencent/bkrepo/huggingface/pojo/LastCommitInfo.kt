package com.tencent.bkrepo.huggingface.pojo

import java.time.LocalDateTime

data class LastCommitInfo(
    val oid: String,
    val title: String,
    val date: LocalDateTime
)
