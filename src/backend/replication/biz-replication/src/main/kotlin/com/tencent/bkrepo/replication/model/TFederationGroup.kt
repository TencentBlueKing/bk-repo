package com.tencent.bkrepo.replication.model

import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 联邦集群组：记录哪些集群互为联邦成员，以及是否对新仓库自动开启联邦
 */
@Document("federation_group")
data class TFederationGroup(
    val id: String? = null,
    @Indexed(unique = true)
    val name: String,
    /** 当前节点的 ClusterNode.id，用于标识本实例在联邦组中的身份 */
    val currentClusterId: String,
    /** 参与联邦的所有 ClusterNode.id 列表（含当前节点） */
    val clusterIds: List<String>,
    /** 是否对新建仓库自动开启联邦同步 */
    val autoEnableForNewRepo: Boolean = true,
    /** 限定自动开启联邦的项目范围，null 表示全部项目 */
    val projectScope: List<String>? = null,
    val createdBy: String,
    val createdDate: LocalDateTime = LocalDateTime.now(),
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime = LocalDateTime.now()
)
