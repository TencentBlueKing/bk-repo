package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.dao.repository.ArtifactDownloadCountRepository
import com.tencent.bkrepo.repository.model.TArtifactDownloadCount
import com.tencent.bkrepo.repository.pojo.download.count.CountResponseInfo
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadCountCreateRequest
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadCountQueryRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class ArtifactDownloadCountService : AbstractService() {
    @Autowired
    private lateinit var artifactDownloadCountRepository: ArtifactDownloadCountRepository

    @Transactional(rollbackFor = [Throwable::class])
    fun create(countCreateRequest: DownloadCountCreateRequest): Response<Void> {
        //如果没有就创建，有的话进行原子更新
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

    @Transactional(rollbackFor = [Throwable::class])
    fun exist(projectId: String, repoName: String, artifact: String, version: String?): Boolean {
        if (artifact.isBlank()) return false
        val criteria = criteria(projectId, repoName, artifact, version)
        criteria.and(TArtifactDownloadCount::currentDate.name).`is`(LocalDate.now())
        return mongoTemplate.exists(Query(criteria), TArtifactDownloadCount::class.java)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun find(countQueryRequest: DownloadCountQueryRequest): Response<CountResponseInfo> {
        with(countQueryRequest) {
            artifact.takeIf { it.isNotBlank() } ?: throw ErrorCodeException(
                CommonMessageCode.PARAMETER_MISSING,
                "artifact"
            )
            val criteria = criteria(projectId, repoName, artifact, version)
            criteria.and(TArtifactDownloadCount::currentDate.name).lte(endTime).gte(startTime)
            val resultList = mongoTemplate.find(Query(criteria), TArtifactDownloadCount::class.java)
            var totalSize = 0
            resultList.forEach { downloadCount ->
                totalSize += downloadCount.count
            }
            with(resultList[0]) {
                val countResponseInfo =
                    CountResponseInfo(artifact, version, count, startTime, endTime, projectId, repoName)
                return ResponseBuilder.success(countResponseInfo)
            }
        }
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