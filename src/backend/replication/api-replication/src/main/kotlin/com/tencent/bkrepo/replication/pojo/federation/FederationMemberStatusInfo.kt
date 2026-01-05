package com.tencent.bkrepo.replication.pojo.federation

import java.time.LocalDateTime

/**
 * 联邦成员状态信息
 *
 */
data class FederationMemberStatusInfo(
    /**
     * 集群ID
     */
    val clusterId: String,

    /**
     * 集群名称
     */
    val clusterName: String,

    /**
     * 集群URL
     */
    val clusterUrl: String,

    /**
     * 任务key
     */
    val taskKey: String,

    /**
     * 项目ID
     */
    val projectId: String,

    /**
     * 仓库名称
     */
    val repoName: String,

    /**
     * 成员状态
     */
    val status: FederationMemberStatus,

    /**
     * 是否启用
     */
    val enabled: Boolean,

    /**
     * 是否可连接
     */
    val connected: Boolean,

    /**
     * 最后同步时间
     */
    val lastSyncTime: LocalDateTime? = null,

    /**
     * 最后连接时间
     */
    val lastConnectTime: LocalDateTime? = null,

    /**
     * 延迟事件数
     */
    val eventLag: Long = 0,

    /**
     * 文件延迟数
     */
    val fileLag: Long = 0,

    /**
     * 失败记录数
     */
    val failureCount: Long = 0,

    /**
     * 错误信息
     */
    val errorMessage: String? = null,


    /**
     * 总同步制品数
     */
    val totalSyncArtifacts: Long = 0,

    /**
     * 成功同步制品数
     */
    val successSyncArtifacts: Long = 0,

    /**
     * 失败同步制品数
     */
    val failedSyncArtifacts: Long = 0,

    /**
     * 总同步文件数
     */
    val totalSyncFiles: Long = 0,

    /**
     * 成功同步文件数
     */
    val successSyncFiles: Long = 0,

    /**
     * 失败同步文件数
     */
    val failedSyncFiles: Long = 0,

    /**
     * 已同步字节数
     */
    val syncedBytes: Long = 0,

    /**
     * 同步平均速率（bytes/s）
     */
    val avgSyncRate: Double = 0.0,

    )


