package com.tencent.bkrepo.repository.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.repository.model.TDownloadStatistics
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetric
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetricResponse
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsResponse
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import com.tencent.bkrepo.repository.service.DownloadStatisticsService
import com.tencent.bkrepo.repository.service.RepositoryService
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.TemporalAdjusters.lastDayOfMonth

/**
 * 下载统计服务实现类
 */
@Service
class DownloadStatisticsServiceImpl(
    private val repositoryService: RepositoryService
) : AbstractService(), DownloadStatisticsService {

    @Transactional(rollbackFor = [Throwable::class])
    override fun add(statisticsAddRequest: DownloadStatisticsAddRequest) {
        with(statisticsAddRequest) {
            val criteria = criteria(projectId, repoName, artifact, version)
                .and(TDownloadStatistics::date.name).`is`(LocalDate.now())
            val query = Query(criteria)
            val update = Update().inc(TDownloadStatistics::count.name, 1)
            try {
                mongoTemplate.upsert(query, update, TDownloadStatistics::class.java)
            } catch (exception: DuplicateKeyException) {
                // retry because upsert operation is not atomic
                logger.warn("DuplicateKeyException: " + exception.message.orEmpty())
                mongoTemplate.upsert(query, update, TDownloadStatistics::class.java)
            }

            logger.info("Create artifact download statistics [$statisticsAddRequest] success.")
        }
    }

    override fun query(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): DownloadStatisticsResponse {
        artifact.takeIf { it.isNotBlank() } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "artifact")
        repositoryService.checkRepository(projectId, repoName)
        val criteria = criteria(projectId, repoName, artifact, version)
        if (startDate != null && endDate != null) {
            criteria.and(TDownloadStatistics::date.name).lte(endDate).gte(startDate)
        } else {
            startDate?.let { criteria.and(TDownloadStatistics::date.name).gte(it) }
            endDate?.let { criteria.and(TDownloadStatistics::date.name).lte(it) }
        }
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(DownloadStatisticsResponse::count.name)
        )
        val aggregateResult = mongoTemplate.aggregate(aggregation, TDownloadStatistics::class.java, HashMap::class.java)
        val count = if (aggregateResult.mappedResults.size > 0) {
            aggregateResult.mappedResults[0][DownloadStatisticsResponse::count.name] as? Int ?: 0
        } else 0
        return DownloadStatisticsResponse(projectId, repoName, artifact, version, count)
    }

    override fun queryForSpecial(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?
    ): DownloadStatisticsMetricResponse {
        artifact.takeIf { it.isNotBlank() } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "artifact")
        repositoryService.checkRepository(projectId, repoName)
        val today = LocalDate.now()
        val monthCount = queryMonthDownloadCount(projectId, repoName, artifact, version, today)
        if (monthCount == 0) {
            val dayCount = listOf(
                DownloadStatisticsMetric(
                    "month",
                    0
                ),
                DownloadStatisticsMetric("week", 0),
                DownloadStatisticsMetric("today", 0)
            )
            return DownloadStatisticsMetricResponse(projectId, repoName, artifact, version, dayCount)
        }
        val weekCount = queryWeekDownloadCount(projectId, repoName, artifact, version, today)
        val todayCount = queryTodayDownloadCount(projectId, repoName, artifact, version, today)
        val dayCount = listOf(
            DownloadStatisticsMetric(
                "month",
                monthCount
            ),
            DownloadStatisticsMetric(
                "week",
                weekCount
            ),
            DownloadStatisticsMetric(
                "today",
                todayCount
            )
        )
        return DownloadStatisticsMetricResponse(projectId, repoName, artifact, version, dayCount)
    }

    override fun queryMonthDownloadCount(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?,
        today: LocalDate
    ): Int {
        val firstDayOfThisMonth = today.with(TemporalAdjusters.firstDayOfMonth())
        val lastDayOfThisMonth = today.with(lastDayOfMonth())
        val monthCriteria =
            criteria(projectId, repoName, artifact, version).and(TDownloadStatistics::date.name)
                .gte(firstDayOfThisMonth).lte(lastDayOfThisMonth)
        val monthAggregation = Aggregation.newAggregation(
            Aggregation.match(monthCriteria),
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(DownloadStatisticsMetric::count.name)
        )
        val monthResult =
            mongoTemplate.aggregate(monthAggregation, TDownloadStatistics::class.java, HashMap::class.java)
        return getAggregateCount(monthResult)
    }

    override fun queryWeekDownloadCount(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?,
        today: LocalDate
    ): Int {
        val firstDayOfWeek =
            today.with(TemporalAdjusters.ofDateAdjuster { localDate -> localDate.minusDays(localDate.dayOfWeek.value - DayOfWeek.MONDAY.value.toLong()) })
        val lastDayOfWeek =
            today.with(TemporalAdjusters.ofDateAdjuster { localDate -> localDate.plusDays(DayOfWeek.SUNDAY.value.toLong() - localDate.dayOfWeek.value) })
        val weekCriteria =
            criteria(projectId, repoName, artifact, version).and(TDownloadStatistics::date.name)
                .gte(firstDayOfWeek).lte(lastDayOfWeek)
        val weekAggregation = Aggregation.newAggregation(
            Aggregation.match(weekCriteria),
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(DownloadStatisticsMetric::count.name)
        )
        val weekResult =
            mongoTemplate.aggregate(weekAggregation, TDownloadStatistics::class.java, HashMap::class.java)
        return getAggregateCount(weekResult)
    }

    override fun queryTodayDownloadCount(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?,
        today: LocalDate
    ): Int {
        val todayCriteria =
            criteria(projectId, repoName, artifact, version).and(TDownloadStatistics::date.name)
                .`is`(today)
        val todayAggregation = Aggregation.newAggregation(
            Aggregation.match(todayCriteria),
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(DownloadStatisticsMetric::count.name)
        )
        val todayResult =
            mongoTemplate.aggregate(todayAggregation, TDownloadStatistics::class.java, HashMap::class.java)
        return getAggregateCount(todayResult)
    }

    private fun getAggregateCount(aggregateResult: AggregationResults<java.util.HashMap<*, *>>): Int {
        return if (aggregateResult.mappedResults.size > 0) {
            aggregateResult.mappedResults[0][DownloadStatisticsMetric::count.name] as? Int ?: 0
        } else 0
    }

    private fun criteria(projectId: String, repoName: String, artifact: String, version: String?): Criteria {
        return Criteria.where(TDownloadStatistics::projectId.name).`is`(projectId)
            .and(TDownloadStatistics::repoName.name).`is`(repoName)
            .and(TDownloadStatistics::artifact.name).`is`(artifact)
            .apply {
                version?.let { and(TDownloadStatistics::version.name).`is`(it) }
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DownloadStatisticsServiceImpl::class.java)
    }
}
