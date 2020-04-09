package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.AddClusterRequest
import com.tencent.bkrepo.auth.pojo.Cluster
import com.tencent.bkrepo.auth.pojo.UpdateClusterRequest

interface ClusterService {

    fun addCluster(request: AddClusterRequest): Boolean

    fun ping(clusterId: String): Boolean

    fun delete(clusterId: String): Boolean

    fun updateCluster(clusterId: String, request: UpdateClusterRequest): Boolean

    fun listCluster(): List<Cluster>
}
