package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceClusterResource
import com.tencent.bkrepo.auth.pojo.AddClusterRequest
import com.tencent.bkrepo.auth.pojo.Cluster
import com.tencent.bkrepo.auth.service.ClusterService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceClusterResourceImpl @Autowired constructor(
    private val clusterService: ClusterService
) : ServiceClusterResource {

    override fun add(request: AddClusterRequest): Response<Boolean> {
        return ResponseBuilder.success(clusterService.addCluster(request))
    }

    override fun list(): Response<List<Cluster>> {
        return ResponseBuilder.success(clusterService.listcluster())
    }

    override fun ping(clusterId: String): Response<Boolean> {
        return ResponseBuilder.success(clusterService.ping(clusterId))
    }

    override fun credential(): Response<Boolean> {
        return ResponseBuilder.success(true)
    }
}
