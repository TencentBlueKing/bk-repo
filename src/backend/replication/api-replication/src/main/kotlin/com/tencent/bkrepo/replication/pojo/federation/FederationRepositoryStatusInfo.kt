package com.tencent.bkrepo.replication.pojo.federation

import java.time.LocalDateTime

/**
 * 联邦仓库状态信息
 *
 */
data class FederationRepositoryStatusInfo(
    /**
     * 联邦ID
     */
    val federationId: String,

    /**
     * 联邦名称
     */
    val federationName: String,

    /**
     * 项目ID
     */
    val projectId: String,

    /**
     * 仓库名称
     */
    val repoName: String,

    /**
     * 当前集群ID
     */
    val currentClusterId: String,

    /**
     * 当前集群名称
     */
    val currentClusterName: String,

    /**
     * 联邦成员总数
     */
    val totalMembers: Int,

    /**
     * 健康成员数
     */
    val healthyMembers: Int,

    /**
     * 延迟成员数
     */
    val delayedMembers: Int,

    /**
     * 错误成员数
     */
    val errorMembers: Int,

    /**
     * 禁用成员数
     */
    val disabledMembers: Int,

    /**
     * 成员状态列表
     */
    val members: List<FederationMemberStatusInfo>,

    /**
     * 是否正在全量同步
     */
    val isFullSyncing: Boolean,

    /**
     * 最后一次全量同步开始时间
     */
    val lastFullSyncStartTime: LocalDateTime? = null,

    /**
     * 最后一次全量同步完成时间
     */
    val lastFullSyncEndTime: LocalDateTime? = null,

    /**
     * 全量同步耗时（毫秒）
     */
    val fullSyncDuration: Long? = null,

    /**
     * 文件延迟数
     */
    val fileLag: Long = 0,

    /**
     * 事件延迟数
     */
    val eventLag: Long = 0,

    /**
     * 失败记录数
     */
    val failureCount: Long = 0,

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
     * 总传输字节数
     */
    val totalBytesTransferred: Long = 0,

    /**
     * 平均传输速率（bytes/s）
     */
    val avgTransferRate: Double = 0.0,

    /**
     * 创建时间
     */
    val createdDate: LocalDateTime,

    /**
     * 最后修改时间
     */
    val lastModifiedDate: LocalDateTime
)


