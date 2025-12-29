package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.replication.model.TEventRecord
import com.tencent.bkrepo.replication.pojo.event.EventRecordDeleteRequest
import com.tencent.bkrepo.replication.pojo.event.EventRecordListOption
import com.tencent.bkrepo.replication.pojo.event.EventRecordRetryRequest

/**
 * 事件记录服务接口
 */
interface EventRecordService {

    /**
     * 分页查询事件记录
     */
    fun listPage(option: EventRecordListOption): Page<TEventRecord>

    /**
     * 根据事件ID查询事件记录
     */
    fun findByEventId(eventId: String): TEventRecord?

    /**
     * 重试指定的事件记录
     */
    fun retryEventRecord(request: EventRecordRetryRequest): Boolean

    /**
     * 重试指定的事件记录（内部方法）
     */
    fun retryEventRecord(eventRecord: TEventRecord): Boolean

    /**
     * 获取需要重试的事件记录
     * @param maxRetryTimes 最大重试次数
     * @param beforeTime 重试时间阈值
     */
    fun getRecordsForRetry(maxRetryTimes: Int, beforeTime: java.time.LocalDateTime): List<TEventRecord>

    /**
     * 更新重试状态
     */
    fun updateRetryStatus(recordId: String, retrying: Boolean)

    /**
     * 增加重试次数
     */
    fun incrementRetryCount(recordId: String)

    /**
     * 删除事件记录（支持按ID或taskKey删除）
     */
    fun deleteEventRecord(request: EventRecordDeleteRequest): Boolean
}

