/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.replication.replica.type.edge

import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.CommitEdgeEdgeCondition
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.cluster.ClusterArtifactReplicaClient
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeStatusUpdateRequest
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.context.annotation.Conditional
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Conditional(CommitEdgeEdgeCondition::class)
class EdgeNodeReportJob(
    private val clusterProperties: ClusterProperties,
    private val clusterNodeService: ClusterNodeService
) {

    private val centerArtifactReplicaClient: ClusterArtifactReplicaClient
        by lazy { FeignClientFactory.create(clusterProperties.center, "replication", clusterProperties.self.name) }

    @Scheduled(initialDelay = INIT_DELAY, fixedRate = FIXED_RATE)
    fun report() {
        val name = clusterProperties.self.name!!
        try {
            centerArtifactReplicaClient.heartbeat(name)
            updateClusterNodeStatus(name, ClusterNodeStatus.HEALTHY)
        } catch (e: Exception) {
            updateClusterNodeStatus(name, ClusterNodeStatus.UNHEALTHY, e.message)
        }
    }

    private fun updateClusterNodeStatus(name: String, status: ClusterNodeStatus, errorReason: String? = null) {
        val request = ClusterNodeStatusUpdateRequest(
            name = name,
            status = status,
            errorReason = errorReason,
            operator = SYSTEM_USER
        )
        clusterNodeService.updateClusterNodeStatus(request)
    }

    companion object {
        private const val INIT_DELAY = 30000L
        private const val FIXED_RATE = 30000L
    }
}