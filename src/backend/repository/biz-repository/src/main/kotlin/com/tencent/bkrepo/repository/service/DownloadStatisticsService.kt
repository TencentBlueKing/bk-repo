package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.repository.dao.repository.DownloadStatisticsRepository
import com.tencent.bkrepo.repository.model.TDownloadStatistics
import com.tencent.bkrepo.repository.pojo.download.count.DownloadStatisticsForSpecialDateInfoResponse
import com.tencent.bkrepo.repository.pojo.download.count.DownloadStatisticsResponseInfo
import com.tencent.bkrepo.repository.pojo.download.count.SpecialDateDownloadStatistics
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadStatisticsCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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

@Service
class DownloadStatisticsService : AbstractService() {

    @Autowired
    private lateinit var downloadStatisticsRepository: DownloadStatisticsRepository

    @Autowired
    private lateinit var repositoryService: RepositoryService

    @Transactional(rollbackFor = [Throwable::class])
    fun add(statisticsCreateRequest: DownloadStatisticsCreateRequest) {
        // 如果没有就创建，有的话进行原子更新
        val now = LocalDate.now()
        with(statisticsCreateRequest) {
            if (exist(projectId, repoName, artifact, version, now)) {
                update(this, now)
                return
            }
            val downloadCount = TDownloadStatistics(
                projectId = projectId,
                repoName = repoName,
                artifact = artifact,
                version = version,
                count = 1,
                date = now
            )
            try {
                downloadStatisticsRepository.insert(downloadCount)
                    .also { logger.info("Create artifact download count [$statisticsCreateRequest] success.") }
            } catch (ex: DuplicateKeyException) {
                logger.warn("insert downloadStatistics record failed: ${ex.message}")
                // 重新更新一次
                update(this, now)
            }
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun update(statisticsCreateRequest: DownloadStatisticsCreateRequest, now: LocalDate) {
        with(statisticsCreateRequest) {
            val criteria = criteria(projectId, repoName, artifact, version)
            criteria.and(TDownloadStatistics::date.name).`is`(now)
            val update = Update().apply { inc(TDownloadStatistics::count.name, 1) }
            mongoTemplate.findAndModify(Query(criteria), update, TDownloadStatistics::class.java)
                .also { logger.info("update download count form artifact [$artifact] success!") }
        }
    }

    fun exist(projectId: String, repoName: String, artifact: String, version: String?, now: LocalDate): Boolean {
        if (artifact.isBlank()) return false
        val criteria = criteria(projectId, repoName, artifact, version)
        criteria.and(TDownloadStatistics::date.name).`is`(now)
        return mongoTemplate.exists(Query(criteria), TDownloadStatistics::class.java)
    }

    fun query(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?,
        startDate: LocalDate,
        endDate: LocalDate
    ): DownloadStatisticsResponseInfo {
        artifact.takeIf { it.isNotBlank() } ?: throw ErrorCodeException(
            CommonMessageCode.PARAMETER_MISSING,
            "artifact"
        )
        repositoryService.checkRepository(projectId, repoName)
        val criteria = criteria(projectId, repoName, artifact, version)
        criteria.and(TDownloadStatistics::date.name).lte(endDate).gte(startDate)
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(DownloadStatisticsResponseInfo::count.name)
        )
        val aggregateResult =
            mongoTemplate.aggregate(aggregation, TDownloadStatistics::class.java, HashMap::class.java)
        val count = if (aggregateResult.mappedResults.size > 0) {
            aggregateResult.mappedResults[0][DownloadStatisticsResponseInfo::count.name] as? Int ?: 0
        } else 0
        return DownloadStatisticsResponseInfo(
            artifact,
            version,
            count,
            projectId,
            repoName
        )
    }

    fun queryForSpecial(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?
    ): DownloadStatisticsForSpecialDateInfoResponse {
        artifact.takeIf { it.isNotBlank() } ?: throw ErrorCodeException(
            CommonMessageCode.PARAMETER_MISSING,
            "artifact"
        )
        repositoryService.checkRepository(projectId, repoName)
        val today = LocalDate.now()
        val monthCount = queryMonthDownloadCount(projectId, repoName, artifact, version, today)
        if (monthCount == 0) {
            val dayCount = listOf(
                SpecialDateDownloadStatistics("month", 0), SpecialDateDownloadStatistics("week", 0),
                SpecialDateDownloadStatistics("today", 0)
            )
            return DownloadStatisticsForSpecialDateInfoResponse(artifact, version, dayCount, projectId, repoName)
        }
        val weekCount = queryWeekDownloadCount(projectId, repoName, artifact, version, today)
        val todayCount = queryTodayDownloadCount(projectId, repoName, artifact, version, today)
        val dayCount = listOf(
            SpecialDateDownloadStatistics("month", monthCount), SpecialDateDownloadStatistics("week", weekCount),
            SpecialDateDownloadStatistics("today", todayCount)
        )
        return DownloadStatisticsForSpecialDateInfoResponse(artifact, version, dayCount, projectId, repoName)
    }

    private fun queryMonthDownloadCount(
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
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(SpecialDateDownloadStatistics::count.name)
        )
        val monthResult =
            mongoTemplate.aggregate(monthAggregation, TDownloadStatistics::class.java, HashMap::class.java)
        return getAggregateCount(monthResult)
    }

    private fun queryWeekDownloadCount(
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
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(SpecialDateDownloadStatistics::count.name)
        )
        val weekResult =
            mongoTemplate.aggregate(weekAggregation, TDownloadStatistics::class.java, HashMap::class.java)
        return getAggregateCount(weekResult)
    }

    private fun queryTodayDownloadCount(
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
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(SpecialDateDownloadStatistics::count.name)
        )
        val todayResult =
            mongoTemplate.aggregate(todayAggregation, TDownloadStatistics::class.java, HashMap::class.java)
        return getAggregateCount(todayResult)
    }

    private fun getAggregateCount(aggregateResult: AggregationResults<java.util.HashMap<*, *>>): Int {
        return if (aggregateResult.mappedResults.size > 0) {
            aggregateResult.mappedResults[0][SpecialDateDownloadStatistics::count.name] as? Int ?: 0
        } else 0
    }

    private fun criteria(projectId: String, repoName: String, artifact: String, version: String?): Criteria {
        val criteria = Criteria.where(TDownloadStatistics::projectId.name).`is`(projectId)
            .and(TDownloadStatistics::repoName.name).`is`(repoName)
            .and(TDownloadStatistics::artifact.name).`is`(artifact)
        version?.let { criteria.and(TDownloadStatistics::version.name).`is`(it) }
        return criteria
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DownloadStatisticsService::class.java)
    }
}
