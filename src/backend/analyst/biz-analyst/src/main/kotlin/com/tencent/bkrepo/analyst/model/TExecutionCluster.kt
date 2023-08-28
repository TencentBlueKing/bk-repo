package com.tencent.bkrepo.analyst.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 扫描任务执行集群
 */
@Document("execution_cluster")
@CompoundIndexes(CompoundIndex(name = "name_idx", def = "{'name': 1}", unique = true))
data class TExecutionCluster(
    val id: String? = null,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,

    /**
     * 集群名
     */
    val name: String,
    /**
     * 集群类型
     */
    val type: String,
    /**
     * 集群描述
     */
    val description: String = "",
    /**
     * 集群配置
     */
    val config: String,
)
