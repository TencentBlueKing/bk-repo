package com.tencent.bkrepo.helm.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("mongo_lock")
@CompoundIndexes(
    CompoundIndex(
        name = "mongo_distributed_lock_inx",
        def = "{'key': 1, 'requestId': 1}",
        background = true
    )
)
data class TMongoLock(
    val key: String,
    val value: Int,
    val expire: LocalDateTime,
    val requestId: String
)
