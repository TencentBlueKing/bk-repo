package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.job.config.properties.BatchJobProperties
import org.springframework.beans.factory.annotation.Autowired

abstract class CenterNodeJob<C : JobContext>(batchJobProperties: BatchJobProperties) : BatchJob<C>(batchJobProperties) {
    @Autowired
    private lateinit var clusterProperties: ClusterProperties

    override fun shouldExecute(): Boolean {
        return super.shouldExecute() && clusterProperties.role == ClusterNodeType.CENTER
    }
}
