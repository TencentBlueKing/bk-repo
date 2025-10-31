package com.tencent.bkrepo.replication.model

import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 同步失败记录表
 * 用于记录node和version分发失败的情况，支持重试机制
 */
@Document("replica_failure_record")
data class TReplicaFailureRecord(
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
    val repoName: String,
    /**
     * 远程项目ID
     */
    val remoteProjectId: String,
    /**
     * 远程仓库名称
     */
    val remoteRepoName: String,

    /**
     * 失败类型：PATH(节点分发失败)、PACKAGE(版本分发失败)
     */
    val failureType: ReplicaObjectType,
    /**
     * 失败的对象标识
     */
    val packageKey: String? = null,
    val packageVersion: String? = null,
    val fullPath: String? = null,
    /**
     * 失败原因
     */
    val failureReason: String? = null,

    /**
     * 重试次数
     */
    val retryCount: Int,

    /**
     * 重试状态
     */
    var retrying: Boolean = false,

    /**
     * 创建时间
     */
    val createdDate: LocalDateTime = LocalDateTime.now(),
    /**
     * 更新时间
     */
    var lastModifiedDate: LocalDateTime = LocalDateTime.now()
)

