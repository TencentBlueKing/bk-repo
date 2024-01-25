package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.KEY
import com.tencent.bkrepo.job.RECORD_RESERVE_DAYS
import com.tencent.bkrepo.job.REPLICA_TYPE
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.ReplicaRecordCleanupJobProperties
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 清理超过保留时间的分发记录
 * 根据分发计划设置的 保留天数，历史数据默认为 60 天
 */
@Component
@EnableConfigurationProperties(ReplicaRecordCleanupJobProperties::class)
class ReplicaRecordCleanupJob(
    properties: ReplicaRecordCleanupJobProperties,
) : DefaultContextMongoDbJob<ReplicaRecordCleanupJob.ReplicaTask>(properties) {
    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_REPLICA_TASK)
    }

    override fun buildQuery(): Query {
        return Query()
    }

    override fun mapToEntity(row: Map<String, Any?>): ReplicaTask {
        return ReplicaTask(
            id = row[ID].toString(),
            key = row[KEY].toString(),
            replicaType = row[REPLICA_TYPE].toString(),
            recordReserveDays = row[RECORD_RESERVE_DAYS]?.toString()?.toLong()
        )
    }

    override fun entityClass(): KClass<ReplicaTask> {
        return ReplicaTask::class
    }

    override fun run(row: ReplicaTask, collectionName: String, context: JobContext) {
        val expireDate = if (row.recordReserveDays != null) {
            LocalDateTime.now().minusDays(row.recordReserveDays)
        } else {
            LocalDateTime.now().minusDays(60)
        }
        cleanUpReplicaTask(row.key, row.replicaType, expireDate)
    }


    private fun cleanUpReplicaTask(
        key: String,
        replicaType: String,
        expireDate: LocalDateTime,
    ) {
        if (replicaType == "REAL_TIME") {
            val recordQuery = Query(where(Record::taskKey).isEqualTo(key))
            var page = 0
            var recordList = mongoTemplate.find(
                recordQuery.with(PageRequest.of(page, PAGE_SIZE)),
                Record::class.java,
                COLLECTION_REPLICA_RECORD
            )
            while (recordList.isNotEmpty()) {
                recordList.forEach {
                    cleanUpRecordDetail(it.id, expireDate)
                    logger.info("cleanup replica record:[$key/${it.id}]")
                }
                page++
                recordList = mongoTemplate.find(
                    recordQuery.with(PageRequest.of(page, PAGE_SIZE)),
                    Record::class.java,
                    COLLECTION_REPLICA_RECORD
                )
            }
        } else {
            val criteria = where(Record::taskKey).isEqualTo(key)
                .and(RecordDetail::status).ne(ExecutionStatus.RUNNING)
                .and(Record::endTime).lt(expireDate)
            val recordQuery = Query(criteria).with(PageRequest.of(0, PAGE_SIZE))
            while (true) {
                val recordList = mongoTemplate.find(
                    recordQuery, Record::class.java, COLLECTION_REPLICA_RECORD
                ).takeIf { it.isNotEmpty() } ?: break
                recordList.forEach {
                    cleanUpRecordDetail(it.id)
                    logger.info("cleanup replica record:[$key/${it.id}]")
                }
            }
        }
    }

    private fun cleanUpRecordDetail(recordId: String) {
        val recordQuery = Query.query(Criteria.where(ID).isEqualTo(recordId))
        val recordDetailQuery = Query.query(Criteria.where(RecordDetail::recordId.name).isEqualTo(recordId))
        mongoTemplate.remove(recordDetailQuery, COLLECTION_REPLICA_RECORD_DETAIL)
        mongoTemplate.remove(recordQuery, COLLECTION_REPLICA_RECORD)
    }

    private fun cleanUpRecordDetail(recordId: String, expireDate: LocalDateTime) {
        val recordDetailQuery = Query.query(
            Criteria.where(RecordDetail::recordId.name).isEqualTo(recordId)
                .and(RecordDetail::status).ne(ExecutionStatus.RUNNING)
                .and(RecordDetail::endTime).lt(expireDate)
        )
        mongoTemplate.remove(recordDetailQuery, COLLECTION_REPLICA_RECORD_DETAIL)
    }

    data class ReplicaTask(
        val id: String,
        val key: String,
        val replicaType: String,
        val recordReserveDays: Long?
    )

    data class Record(
        val id: String,
        val taskKey: String,
        val status: ExecutionStatus,
        val endTime: LocalDateTime?
    )

    data class RecordDetail(
        val id: String,
        val recordId: String,
        val status: ExecutionStatus,
        val endTime: LocalDateTime?
    )

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_REPLICA_TASK = "replica_task"
        private const val COLLECTION_REPLICA_RECORD = "replica_record"
        private const val COLLECTION_REPLICA_RECORD_DETAIL = "replica_record_detail"
        private const val PAGE_SIZE = 1000
    }
}
