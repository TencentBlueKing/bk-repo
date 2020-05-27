package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.dao.repository.ArtifactDownloadCountRepository
import com.tencent.bkrepo.repository.model.TArtifactDownloadCount
import com.tencent.bkrepo.repository.pojo.download.count.CountResponseInfo
import com.tencent.bkrepo.repository.pojo.download.count.CountWithSpecialDayInfoResponse
import com.tencent.bkrepo.repository.pojo.download.count.SpecialDayCount
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadCountCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
class ArtifactDownloadCountService : AbstractService() {

    @Autowired
    private lateinit var artifactDownloadCountRepository: ArtifactDownloadCountRepository

    @Autowired
    private lateinit var repositoryService: RepositoryService

    @Transactional(rollbackFor = [Throwable::class])
    fun create(countCreateRequest: DownloadCountCreateRequest): Response<Void> {
        // 如果没有就创建，有的话进行原子更新
        with(countCreateRequest) {
            if (exist(projectId, repoName, artifact, version)) {
                val criteria = criteria(projectId, repoName, artifact, version)
                criteria.and(TArtifactDownloadCount::currentDate.name).`is`(LocalDate.now())
                val update = Update().apply { inc(TArtifactDownloadCount::count.name, 1) }
                mongoTemplate.findAndModify(Query(criteria), update, TArtifactDownloadCount::class.java)
                    .also { logger.info("update download count form artifact [$artifact] success!") }
                return ResponseBuilder.success()
            }
            val downloadCount = TArtifactDownloadCount(
                projectId = projectId,
                repoName = repoName,
                artifact = artifact,
                version = version,
                count = 1,
                currentDate = LocalDate.now()
            )
            artifactDownloadCountRepository.insert(downloadCount)
                .also { logger.info("Create artifact download count [$countCreateRequest] success.") }
            return ResponseBuilder.success()
        }
    }

    fun exist(projectId: String, repoName: String, artifact: String, version: String?): Boolean {
        if (artifact.isBlank()) return false
        val criteria = criteria(projectId, repoName, artifact, version)
        criteria.and(TArtifactDownloadCount::currentDate.name).`is`(LocalDate.now())
        return mongoTemplate.exists(Query(criteria), TArtifactDownloadCount::class.java)
    }

    fun find(projectId: String, repoName: String, artifact: String, version: String?, startDay: LocalDate, endDay: LocalDate): CountResponseInfo {
            artifact.takeIf { it.isNotBlank() } ?: throw ErrorCodeException(
                CommonMessageCode.PARAMETER_MISSING,
                "artifact"
            )
            repositoryService.checkRepository(projectId, repoName)
            val criteria = criteria(projectId, repoName, artifact, version)
            criteria.and(TArtifactDownloadCount::currentDate.name).lte(endDay).gte(startDay)
            val aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group().sum(TArtifactDownloadCount::count.name).`as`(CountResponseInfo::count.name)
            )
            val aggregateResult =
                mongoTemplate.aggregate(aggregation, TArtifactDownloadCount::class.java, HashMap::class.java)
            val count = if (aggregateResult.mappedResults.size > 0) {
                aggregateResult.mappedResults[0][CountResponseInfo::count.name] as? Int ?: 0
            } else 0
            return CountResponseInfo(
                artifact,
                version,
                count,
                projectId,
                repoName
            )
    }

    fun query(projectId: String, repoName: String, artifact: String, version: String?): CountWithSpecialDayInfoResponse {
        artifact.takeIf { it.isNotBlank() } ?: throw ErrorCodeException(
            CommonMessageCode.PARAMETER_MISSING,
            "artifact"
        )
        repositoryService.checkRepository(projectId, repoName)
        val today = LocalDate.now()
        val monthCount = queryMonthDownloadCount(projectId, repoName, artifact, version, today)
        if (monthCount == 0) {
            val dayCount = listOf(
                SpecialDayCount("month", 0), SpecialDayCount("week", 0),
                SpecialDayCount("today", 0)
            )
            return CountWithSpecialDayInfoResponse(artifact, version, dayCount, projectId, repoName)
        }
        val weekCount = queryWeekDownloadCount(projectId, repoName, artifact, version, today)
        val todayCount = queryTodayDownloadCount(projectId, repoName, artifact, version, today)
        val dayCount = listOf(
            SpecialDayCount("month", monthCount), SpecialDayCount("week", weekCount),
            SpecialDayCount("today", todayCount)
        )
        return CountWithSpecialDayInfoResponse(artifact, version, dayCount, projectId, repoName)
    }

    private fun queryMonthDownloadCount(projectId: String, repoName: String, artifact: String, version: String?, today: LocalDate): Int {
        val firstDayOfThisMonth = today.with(TemporalAdjusters.firstDayOfMonth())
        val lastDayOfThisMonth = today.with(lastDayOfMonth())
        val monthCriteria =
            criteria(projectId, repoName, artifact, version).and(TArtifactDownloadCount::currentDate.name)
                .gte(firstDayOfThisMonth).lte(lastDayOfThisMonth)
        val monthAggregation = Aggregation.newAggregation(
            Aggregation.match(monthCriteria),
            Aggregation.group().sum(TArtifactDownloadCount::count.name).`as`(SpecialDayCount::count.name)
        )
        val monthResult =
            mongoTemplate.aggregate(monthAggregation, TArtifactDownloadCount::class.java, HashMap::class.java)
        return getAggregateCount(monthResult)
    }

    private fun queryWeekDownloadCount(projectId: String, repoName: String, artifact: String, version: String?, today: LocalDate): Int {
        val firstDayOfWeek =
            today.with(TemporalAdjusters.ofDateAdjuster { localDate -> localDate.minusDays(localDate.dayOfWeek.value - DayOfWeek.MONDAY.value.toLong()) })
        val lastDayOfWeek =
            today.with(TemporalAdjusters.ofDateAdjuster { localDate -> localDate.plusDays(DayOfWeek.SUNDAY.value.toLong() - localDate.dayOfWeek.value) })
        val weekCriteria =
            criteria(projectId, repoName, artifact, version).and(TArtifactDownloadCount::currentDate.name)
                .gte(firstDayOfWeek).lte(lastDayOfWeek)
        val weekAggregation = Aggregation.newAggregation(
            Aggregation.match(weekCriteria),
            Aggregation.group().sum(TArtifactDownloadCount::count.name).`as`(SpecialDayCount::count.name)
        )
        val weekResult =
            mongoTemplate.aggregate(weekAggregation, TArtifactDownloadCount::class.java, HashMap::class.java)
        return getAggregateCount(weekResult)
    }

    private fun queryTodayDownloadCount(projectId: String, repoName: String, artifact: String, version: String?, today: LocalDate): Int {
        val todayCriteria =
            criteria(projectId, repoName, artifact, version).and(TArtifactDownloadCount::currentDate.name)
                .`is`(today)
        val todayAggregation = Aggregation.newAggregation(
            Aggregation.match(todayCriteria),
            Aggregation.group().sum(TArtifactDownloadCount::count.name).`as`(SpecialDayCount::count.name)
        )
        val todayResult =
            mongoTemplate.aggregate(todayAggregation, TArtifactDownloadCount::class.java, HashMap::class.java)
        return getAggregateCount(todayResult)
    }

    private fun getAggregateCount(aggregateResult: AggregationResults<java.util.HashMap<*, *>>): Int {
        return if (aggregateResult.mappedResults.size > 0) {
            aggregateResult.mappedResults[0][SpecialDayCount::count.name] as? Int ?: 0
        } else 0
    }

    private fun criteria(projectId: String, repoName: String, artifact: String, version: String?): Criteria {
        val criteria = Criteria.where(TArtifactDownloadCount::projectId.name).`is`(projectId)
            .and(TArtifactDownloadCount::repoName.name).`is`(repoName)
            .and(TArtifactDownloadCount::artifact.name).`is`(artifact)
        version?.let { criteria.and(TArtifactDownloadCount::version.name).`is`(it) }
        return criteria
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactDownloadCountService::class.java)
    }
}
