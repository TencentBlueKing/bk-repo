package com.tencent.bkrepo.replication.pojo.request

data class KeyReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val id: String = "",
    val name: String = "",
    /** SSH 公钥原文，同步时需传递以便目标端可以导入；指纹由目标端重新计算 */
    val key: String = "",
    val fingerprint: String = "",
    val userId: String = "",
    val createAt: String = ""
)
