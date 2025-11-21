package com.tencent.bkrepo.replication.service.impl.tracking

import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.replica.replicator.standalone.FederationReplicator
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.slf4j.LoggerFactory

/**
 * 文件传输处理器
 * 负责执行文件传输操作
 */
class FileTransferProcessor(
    private val replicaContextBuilder: ReplicaContextBuilder
) {

    /**
     * 处理单个文件传输
     */
    fun process(
        record: TFederationMetadataTracking,
        nodeInfo: NodeInfo,
        clusterNodeInfo: ClusterNodeInfo
    ): Boolean {
        try {
            // 构建 ReplicaContext
            val context = replicaContextBuilder.build(record, clusterNodeInfo)

            // 调用 FederationReplicator 执行文件传输
            val replicator = SpringContextUtils.getBean<FederationReplicator>()
            val success = replicator.pushFileToFederatedClusterPublic(context, nodeInfo)

            if (success) {
                logger.info("Successfully processed file transfer for node ${record.nodePath}")
                return true
            } else {
                logger.warn("Failed to process file transfer for node ${record.nodePath}")
                return false
            }
        } catch (e: Exception) {
            logger.warn("Error processing file transfer for node ${record.nodePath}", e)
            throw e
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileTransferProcessor::class.java)
    }
}

