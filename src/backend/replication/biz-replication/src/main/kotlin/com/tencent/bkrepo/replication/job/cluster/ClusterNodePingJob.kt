package com.tencent.bkrepo.replication.job.cluster

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.cluster.ClusterProperties
import com.tencent.bkrepo.common.artifact.cluster.RoleType
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatusUpdateRequest
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 集群定时心跳检测
 */
@Component
class ClusterNodePingJob(
    private val clusterProperties: ClusterProperties,
    private val clusterNodeService: ClusterNodeService
) {
    @Scheduled(initialDelay = 10 * 1000L, fixedDelay = 30 * 1000L) // 每隔15s钟ping一次
    fun start() {
        if (!shouldExecute()) return
        val clusterNodeList = clusterNodeService.listClusterNodes(name = null, type = null)
        clusterNodeList.forEach {
            try {
                clusterNodeService.tryConnect(it.name)
                if (it.status == ClusterNodeStatus.UNHEALTHY) {
                    // 设置为HEALTHY状态
                    updateClusterNodeStatus(it.name, ClusterNodeStatus.HEALTHY)
                }
            } catch (exception: ErrorCodeException) {
                updateClusterNodeStatus(it.name, ClusterNodeStatus.UNHEALTHY, exception.message)
            }
        }
    }

    /**
     * 修改节点状态
     */
    private fun updateClusterNodeStatus(name: String, status: ClusterNodeStatus, errorReason: String? = null) {
        val request = ClusterNodeStatusUpdateRequest(
            name = name,
            status = status,
            errorReason = errorReason,
            operator = SYSTEM_USER
        )
        clusterNodeService.updateClusterNodeStatus(request)
    }

    /**
     * 只在中心节点执行
     */
    fun shouldExecute(): Boolean {
        return clusterProperties.role == RoleType.CENTER
    }
}
