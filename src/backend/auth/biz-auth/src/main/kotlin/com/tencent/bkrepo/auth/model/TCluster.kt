package com.tencent.bkrepo.auth.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("cluster")
@CompoundIndexes(
    CompoundIndex(name = "clusterId_idx", def = "{'clusterId': 1}", unique = true, background = true)
)
data class TCluster(
    var clusterId: String,
    var cert: String = "",
    var clusterAddr: String,
    val credentialStatus: Boolean? = false
)
