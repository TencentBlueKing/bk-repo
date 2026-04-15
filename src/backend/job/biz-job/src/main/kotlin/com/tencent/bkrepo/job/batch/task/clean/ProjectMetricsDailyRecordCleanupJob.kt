package com.tencent.bkrepo.job.batch.task.clean

import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.ProjectMetricsDailyRecordCleanupJobProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class ProjectMetricsDailyRecordCleanupJob(
    private val properties: ProjectMetricsDailyRecordCleanupJobProperties,
    private val mongoTemplate: MongoTemplate,
) : DefaultContextJob(properties) {

    data class ProjectMetricsDailyRecord(
        val createdDate: LocalDateTime,
        val createdDay: String? = null,
    )

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun doStart0(jobContext: JobContext) {
        val expireDate = LocalDate.now().minusDays(properties.keepDays).atStartOfDay()
        val expireDay = expireDate.format(DATE_FORMATTER)
        val indexedCleanupResult = mongoTemplate.remove(
            Query(where(ProjectMetricsDailyRecord::createdDay).lt(expireDay)),
            COLLECTION_NAME
        )
        val deletedCount = indexedCleanupResult.deletedCount
        jobContext.success.addAndGet(deletedCount)
        jobContext.total.addAndGet(deletedCount)
    }

    companion object {
        private const val COLLECTION_NAME = "project_metrics_daily_record"
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
