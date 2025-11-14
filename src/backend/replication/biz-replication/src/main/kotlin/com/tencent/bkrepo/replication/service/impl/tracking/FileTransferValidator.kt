package com.tencent.bkrepo.replication.service.impl.tracking

import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.repository.pojo.node.NodeInfo

/**
 * 文件传输验证器
 * 负责验证节点和集群信息
 */
class FileTransferValidator(
    private val localDataManager: LocalDataManager,
    private val clusterNodeService: ClusterNodeService
) {

    /**
     * 验证结果
     */
    data class ValidationResult(
        val isValid: Boolean,
        val nodeInfo: NodeInfo? = null,
        val clusterNodeInfo: ClusterNodeInfo? = null,
        val errorMessage: String? = null
    )

    /**
     * 验证跟踪记录的有效性
     */
    fun validate(record: TFederationMetadataTracking): ValidationResult {
        // 验证节点信息
        val nodeInfo = localDataManager.findNodeById(
            projectId = record.projectId,
            nodeId = record.nodeId
        )
        if (nodeInfo == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Node not found: ${record.nodePath}"
            )
        }

        // 验证集群信息
        val clusterNodeInfo = clusterNodeService.getByClusterId(record.remoteClusterId)
        if (clusterNodeInfo == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Cluster not found: ${record.remoteClusterId}"
            )
        }

        return ValidationResult(
            isValid = true,
            nodeInfo = nodeInfo,
            clusterNodeInfo = clusterNodeInfo
        )
    }
}

