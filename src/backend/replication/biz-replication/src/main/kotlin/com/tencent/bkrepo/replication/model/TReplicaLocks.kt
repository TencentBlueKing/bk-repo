package com.tencent.bkrepo.replication.model

import org.springframework.data.mongodb.core.mapping.Document

@Document("replica_locks")
data class TReplicaLocks(
    var id: String,
    var type: String? = null,
    var keyName: String,
    var keyGroup: String
)
