package com.tencent.bkrepo.media.common.dao

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJob
import com.tencent.bkrepo.media.common.pojo.transcode.MediaTranscodeJobStatus
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
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

    fun queueAndRunningJobCount(): Long {
        return count(
            Query(
                where(TMediaTranscodeJob::status).`in`(
                    MediaTranscodeJobStatus.QUEUE,
                    MediaTranscodeJobStatus.INIT,
                    MediaTranscodeJobStatus.RUNNING
                )
            )
        )
    }

    fun findAndQueueOldestWaitingJob(): TMediaTranscodeJob? {
        val query: Query = Query(where(TMediaTranscodeJob::status).isEqualTo(MediaTranscodeJobStatus.WAITING))
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
}