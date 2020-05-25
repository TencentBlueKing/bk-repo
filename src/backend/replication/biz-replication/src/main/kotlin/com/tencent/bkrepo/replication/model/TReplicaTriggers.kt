package com.tencent.bkrepo.replication.model

import org.springframework.data.mongodb.core.mapping.Document

@Document("replica_triggers")
data class TReplicaTriggers(
    var id: String,
    var state: String? = null,
    var keyName: String,
    var keyGroup: String
)
