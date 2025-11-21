package com.tencent.bkrepo.replication.model

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 事件记录表
 * 用于记录消费前的事件消息，确保消息不丢失
 * 每个taskKey单独记录一条event，当任务执行完成后删除记录，只保留失败的或没有消费完的记录
 */
@Document("event_record")
@CompoundIndexes(
    CompoundIndex(
        name = "completed_idx", def = "{'taskCompleted': 1}", background = true
    ),
)
data class TEventRecord(
    var id: String? = null,

    /**
     * 事件类型：NORMAL（普通事件）或 FEDERATION（联邦事件）
     */
    val eventType: String = "NORMAL",

    /**
     * 事件对象
     */
    val event: ArtifactEvent,

    /**
     * 关联的任务key
     */
    val taskKey: String,

    /**
     * 任务是否已完成
     */
    var taskCompleted: Boolean = false,

    /**
     * 任务是否成功
     */
    var taskSucceeded: Boolean = false,

    /**
     * 重试次数
     */
    var retryCount: Int = 0,

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

