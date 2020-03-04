package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.AddClusterRequest
import com.tencent.bkrepo.auth.pojo.Cluster

interface ClusterService {

    fun addCluster(request: AddClusterRequest): Boolean

    fun ping(clusterId: String): Boolean

    fun listcluster(): List<Cluster>
}
