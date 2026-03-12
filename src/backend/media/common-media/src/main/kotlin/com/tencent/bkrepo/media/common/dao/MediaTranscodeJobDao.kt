package com.tencent.bkrepo.media.common.dao

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJob
import com.tencent.bkrepo.media.common.pojo.transcode.MediaTranscodeJobStatus
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.springframework.data.mongodb.core.query.Criteria.where as strWhere

@Repository
class MediaTranscodeJobDao : SimpleMongoDao<TMediaTranscodeJob>() {
    fun updateStatus(
        projectId: String,
        repoName: String,
        fileName: String,
        status: MediaTranscodeJobStatus,
    ): UpdateResult {
        val query = Query(
            TMediaTranscodeJob::projectId.isEqualTo(projectId)
                .and(TMediaTranscodeJob::repoName).isEqualTo(repoName)
                .and(TMediaTranscodeJob::fileName).isEqualTo(fileName)
        )
        val update = Update().set(TMediaTranscodeJob::status.name, status)
            .set(TMediaTranscodeJob::updateTime.name, LocalDateTime.now())
        return updateFirst(query, update)
    }

    /**
     * 按项目ID分组统计队列和运行中的任务数
     */
    fun queueAndRunningJobCountGroupByProject(): Map<String, Long> {
        val matchOperation = Aggregation.match(
            where(TMediaTranscodeJob::status).`in`(
                MediaTranscodeJobStatus.QUEUE,
                MediaTranscodeJobStatus.INIT,
                MediaTranscodeJobStatus.RUNNING
            )
        )
        val groupOperation = Aggregation.group(TMediaTranscodeJob::projectId.name)
            .count().`as`("count")
        val aggregation = Aggregation.newAggregation(matchOperation, groupOperation)
        val results = aggregate(aggregation, org.bson.Document::class.java)
        return results.mappedResults.associate { doc ->
            (doc.getString("_id") ?: "") to (doc.getInteger("count", 0).toLong())
        }
    }

    /**
     * 统计指定项目的队列和运行中任务数
     */
    fun queueAndRunningJobCount(projectId: String): Long {
        return count(
            Query(
                where(TMediaTranscodeJob::status).`in`(
                    MediaTranscodeJobStatus.QUEUE,
                    MediaTranscodeJobStatus.INIT,
                    MediaTranscodeJobStatus.RUNNING
                ).and(TMediaTranscodeJob::projectId).isEqualTo(projectId)
            )
        )
    }

    /**
     * 统计不在指定项目列表中的队列和运行中任务数（用于默认配置的配额判断）
     */
    fun queueAndRunningJobCountExcludeProjects(excludeProjectIds: Set<String>): Long {
        val criteria = where(TMediaTranscodeJob::status).`in`(
            MediaTranscodeJobStatus.QUEUE,
            MediaTranscodeJobStatus.INIT,
            MediaTranscodeJobStatus.RUNNING
        )
        if (excludeProjectIds.isNotEmpty()) {
            criteria.and(TMediaTranscodeJob::projectId).nin(excludeProjectIds)
        }
        return count(Query(criteria))
    }

    /**
     * 按时间顺序取指定项目最旧的 WAITING 任务并将其状态改为 QUEUE
     */
    fun findAndQueueOldestWaitingJob(projectId: String): TMediaTranscodeJob? {
        val query: Query = Query(
            where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.WAITING)
                .and(TMediaTranscodeJob::projectId).isEqualTo(projectId)
        ).with(Sort.by(Sort.Direction.ASC, TMediaTranscodeJob::createdTime.name))
        val update = Update()
            .set(TMediaTranscodeJob::status.name, MediaTranscodeJobStatus.QUEUE)
            .currentDate(TMediaTranscodeJob::updateTime.name)
        val options = FindAndModifyOptions().returnNew(true)
        return findAndModify(query, update, options, TMediaTranscodeJob::class.java)
    }

    /**
     * 按时间顺序取不在指定项目列表中的最旧 WAITING 任务并将其状态改为 QUEUE（用于默认配置）
     */
    fun findAndQueueOldestWaitingJobExcludeProjects(excludeProjectIds: Set<String>): TMediaTranscodeJob? {
        val criteria = where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.WAITING)
        if (excludeProjectIds.isNotEmpty()) {
            criteria.and(TMediaTranscodeJob::projectId).nin(excludeProjectIds)
        }
        val query: Query = Query(criteria)
            .with(Sort.by(Sort.Direction.ASC, TMediaTranscodeJob::createdTime.name))
        val update = Update()
            .set(TMediaTranscodeJob::status.name, MediaTranscodeJobStatus.QUEUE)
            .currentDate(TMediaTranscodeJob::updateTime.name)
        val options = FindAndModifyOptions().returnNew(true)
        return findAndModify(query, update, options, TMediaTranscodeJob::class.java)
    }

    fun updateJobStatus(id: String, status: MediaTranscodeJobStatus): UpdateResult {
        val query = Query(strWhere("_id").isEqualTo(ObjectId(id)))
        val update = Update()
            .set(TMediaTranscodeJob::status.name, status)
            .currentDate(TMediaTranscodeJob::updateTime.name)
        return updateFirst(query, update)
    }

    fun updateJobsStatus(ids: Set<String>, status: MediaTranscodeJobStatus): UpdateResult {
        val query = Query(strWhere("_id").`in`(ids.map { ObjectId(it) }))
        val update = Update()
            .set(TMediaTranscodeJob::status.name, status)
            .currentDate(TMediaTranscodeJob::updateTime.name)
        return updateMulti(query, update)
    }

    /**
     * 删除一周前更新且状态为 SUCCESS 的作业。
     */
    fun deleteOldSuccessfulJobs(day: Long): DeleteResult {
        val outTime = Instant.now().minus(day, ChronoUnit.DAYS)
        val query = Query(
            where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.SUCCESS)
                .and(TMediaTranscodeJob::updateTime).lt(outTime)
        )
        val deleteResult = remove(query)
        return deleteResult
    }

    /**
     * 查询失败的任务
     */
    fun findFailedJobs(limit: Int = 100): List<TMediaTranscodeJob> {
        val query = Query(
            where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.FAIL)
        ).limit(limit)
        return find(query)
    }
}