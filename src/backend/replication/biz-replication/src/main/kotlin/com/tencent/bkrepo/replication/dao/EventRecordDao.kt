package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.replication.model.TEventRecord
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 事件记录数据访问层
 */
@Repository
class EventRecordDao : SimpleMongoDao<TEventRecord>() {

    /**
     * 根据事件ID查找记录
     */
    fun findByEventId(eventId: String): TEventRecord? {
        return this.findById(eventId)
    }

    /**
     * 根据任务Key查找记录
     */
    fun findByTaskKey(taskKey: String): List<TEventRecord> {
        val query = Query.query(Criteria.where(TEventRecord::taskKey.name).isEqualTo(taskKey))
        return this.find(query)
    }

    /**
     * 保存事件记录
     */
    fun saveEventRecord(record: TEventRecord): TEventRecord {
        return this.save(record)
    }

    fun updateTaskStatus(eventId: String, taskKey: String, taskSucceeded: Boolean): TEventRecord? {
        val query = Query.query(Criteria.where(ID).isEqualTo(eventId))
        val update = Update()
            .set(TEventRecord::taskCompleted.name, true)
            .set(TEventRecord::taskSucceeded.name, taskSucceeded)
            .set(TEventRecord::lastModifiedDate.name, LocalDateTime.now())

        this.updateFirst(query, update)

        // 重新查询获取最新状态
        return this.findByEventId(eventId)
    }

    /**
     * 删除事件记录（当所有任务成功完成时）
     */
    fun deleteByEventId(eventId: String) {
        this.removeById(eventId)
    }

    /**
     * 根据任务Key删除事件记录
     */
    fun deleteByTaskKey(taskKey: String) {
        val query = Query.query(Criteria.where(TEventRecord::taskKey.name).isEqualTo(taskKey))
        this.remove(query)
    }

    /**
     * 查找需要重试的记录（重试次数小于指定值且不在重试中）
     * @param maxRetryTimes 最大重试次数
     * @param beforeTime 对于未完成的任务（taskCompleted=false），只有创建时间早于此时间的事件记录才会被返回
     *                   对于已完成但失败的任务（taskCompleted=true且taskSucceeded=false），不判断时间，直接返回
     * @return 需要重试的事件记录列表
     */
    fun findByTriedTimesLessThanAndRetryingFalse(
        maxRetryTimes: Int,
        beforeTime: LocalDateTime
    ): List<TEventRecord> {
        // 构建时间条件
        val timeCriteria = Criteria().orOperator(
            Criteria.where(TEventRecord::taskCompleted.name).isEqualTo(true)
                .and(TEventRecord::taskSucceeded.name).isEqualTo(false),
            Criteria.where(TEventRecord::taskCompleted.name).isEqualTo(false)
                .and(TEventRecord::createdDate.name).lt(beforeTime)
        )

        // 构建完整查询条件：重试次数小于等于最大值，不在重试中，且满足时间条件
        val criteria = Criteria()
            .andOperator(
                Criteria.where(TEventRecord::retryCount.name).lte(maxRetryTimes),
                Criteria.where(TEventRecord::retrying.name).isEqualTo(false),
                timeCriteria
            )

        val query = Query(criteria)
        return this.find(query)
    }

    /**
     * 更新重试状态
     */
    fun updateRetryStatus(recordId: String, retrying: Boolean) {
        val query = Query.query(Criteria.where(ID).isEqualTo(recordId))
        val update = Update.update(TEventRecord::retrying.name, retrying)
            .set(TEventRecord::lastModifiedDate.name, LocalDateTime.now())
        this.updateFirst(query, update)
    }

    /**
     * 增加重试次数
     */
    fun incrementRetryCount(recordId: String) {
        val query = Query.query(Criteria.where(ID).isEqualTo(recordId))
        val update = Update()
            .inc(TEventRecord::retryCount.name, 1)
            .set(TEventRecord::retrying.name, false)
            .set(TEventRecord::lastModifiedDate.name, LocalDateTime.now())
        this.updateFirst(query, update)
    }

    /**
     * 分页查询事件记录
     */
    fun findPage(
        pageNumber: Int,
        pageSize: Int,
        eventType: String?,
        taskCompleted: Boolean?,
        taskSucceeded: Boolean?,
        taskKey: String?,
        sortField: String?,
        sortDirection: String?
    ): Pair<List<TEventRecord>, Long> {
        val criteria = Criteria()

        eventType?.let { criteria.and(TEventRecord::eventType.name).isEqualTo(it) }
        taskCompleted?.let { criteria.and(TEventRecord::taskCompleted.name).isEqualTo(it) }
        taskSucceeded?.let { criteria.and(TEventRecord::taskSucceeded.name).isEqualTo(it) }
        taskKey?.let { criteria.and(TEventRecord::taskKey.name).isEqualTo(it) }
        val query = Query(criteria)

        // 排序
        if (sortField != null && sortDirection != null) {
            val direction = if (sortDirection.uppercase() == "ASC") {
                Sort.Direction.ASC
            } else {
                Sort.Direction.DESC
            }
            val field = when (sortField) {
                "createdDate" -> TEventRecord::createdDate.name
                "lastModifiedDate" -> TEventRecord::lastModifiedDate.name
                else -> TEventRecord::createdDate.name
            }
            query.with(Sort.by(direction, field))
        } else {
            query.with(Sort.by(Sort.Direction.DESC, TEventRecord::createdDate.name))
        }

        // 分页
        val total = this.count(query)
        query.skip((pageNumber - 1).toLong() * pageSize)
        query.limit(pageSize)

        val records = this.find(query)
        return Pair(records, total)
    }
}

