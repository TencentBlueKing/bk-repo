package com.tencent.bkrepo.replication.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 用于跟踪federation同步时FEDERATED的状态变化
 * */
@Document("federation_metadata_tracking")
@CompoundIndexes(
    CompoundIndex(
        name = "node_idx", def = "{'taskKey': 1, 'nodeId':1}", background = true
    )
)
data class TFederationMetadataTracking(
    var id: String? = null,
    /**
     * 关联的任务key
     */
    val taskKey: String,

    /**
     * 远程集群id
     */
    val remoteClusterId: String,
    /**
     * 项目ID
     */
    val projectId: String,
    /**
     * 本地仓库名称
     */
    val localRepoName: String,

    /**
     * 远程项目ID
     */
    val remoteProjectId: String,
    /**
     * 远程仓库名称
     */
    val remoteRepoName: String,

    /**
     * 节点路径
     */
    val nodePath: String,

    /**
     * 节点id
     */
    val nodeId: String,

    /**
     * 文件开始传输时间
     */
    var createdDate: LocalDateTime = LocalDateTime.now(),

    /**
     * 重试次数
     */
    var retryCount: Int = 0,

    /**
     * 重试状态
     */
    var retrying: Boolean = false,

    /**
     * 失败原因
     */
    var failureReason: String? = null,

    /**
     * 最后更新时间
     */
    var lastModifiedDate: LocalDateTime = LocalDateTime.now(),
)
