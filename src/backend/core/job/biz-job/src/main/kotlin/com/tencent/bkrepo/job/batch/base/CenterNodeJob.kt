package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.common.api.pojo.ClusterArchitecture
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.job.config.properties.BatchJobProperties
import org.springframework.beans.factory.annotation.Autowired

abstract class CenterNodeJob<C : JobContext>(batchJobProperties: BatchJobProperties) : BatchJob<C>(batchJobProperties) {
    @Autowired
    private lateinit var clusterProperties: ClusterProperties

    override fun shouldExecute(): Boolean {
        val centerNode = clusterProperties.role == ClusterNodeType.CENTER
        val commitEdgeEdgeNode = clusterProperties.role == ClusterNodeType.EDGE &&
            clusterProperties.architecture == ClusterArchitecture.COMMIT_EDGE
        return super.shouldExecute() && (centerNode || commitEdgeEdgeNode)
    }
}
