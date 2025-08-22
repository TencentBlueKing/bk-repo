package com.tencent.bkrepo.media.dao

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.media.model.TMediaTranscodeJob
import com.tencent.bkrepo.media.pojo.transcode.MediaTranscodeJobStatus
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.data.mongodb.core.query.and
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

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
}