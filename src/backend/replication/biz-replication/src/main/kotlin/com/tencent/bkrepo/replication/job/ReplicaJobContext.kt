package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.replication.api.ReplicaResource
import com.tencent.bkrepo.replication.model.TReplicaTask

data class ReplicaJobContext(
    val task: TReplicaTask,
    val replicaResource: ReplicaResource
)
