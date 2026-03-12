package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.service.cluster.StandaloneJob
import com.tencent.bkrepo.replication.model.TFederationGroup
import com.tencent.bkrepo.replication.service.FederationGroupService
import com.tencent.bkrepo.replication.service.FederationRepositoryService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 存量仓库联邦自动开启 BatchJob
 *
 * 分页扫描所有仓库，对尚未开启联邦的仓库，按 TFederationGroup 配置自动建立联邦。
 * 幂等：已有联邦配置的仓库会被 autoEnableFederation 内部逻辑跳过。
 * 建议在 TFederationGroup 配置完成后手动触发一次。
 */
@Component
class FederationAutoEnableBatchJob(
    private val federationGroupService: FederationGroupService,
    private val federationRepositoryService: FederationRepositoryService,
    private val mongoTemplate: MongoTemplate,
) {

    @StandaloneJob
    @Scheduled(cron = "\${replication.federation.autoEnableCron:0 0 3 ? * MON}")
    @SchedulerLock(name = "FederationAutoEnableBatchJob", lockAtMostFor = "PT168H")
    fun execute() {
        val allGroups = federationGroupService.listAll()
        if (allGroups.isEmpty()) {
            logger.info("No federation groups configured, skip batch job")
            return
        }

        var processedCount = 0
        var successCount = 0
        var pageNumber = 0
        val pageSize = 100

        while (true) {
            val repos = fetchRepoPage(pageNumber, pageSize)
            if (repos.isEmpty()) break
            repos.forEach { repo ->
                processedCount++
                successCount += processRepo(repo, allGroups)
            }
            if (repos.size < pageSize) break
            pageNumber++
        }
        logger.info("FederationAutoEnableBatchJob completed: processed=$processedCount, success=$successCount")
    }

    private fun fetchRepoPage(pageNumber: Int, pageSize: Int): List<RepositoryInfo> {
        return mongoTemplate.find(
            Query().skip((pageNumber * pageSize).toLong()).limit(pageSize),
            RepositoryInfo::class.java,
            "repository"
        )
    }

    private fun processRepo(repo: RepositoryInfo, allGroups: List<TFederationGroup>): Int {
        val matchedGroups = allGroups.filter { group ->
            group.autoEnableForNewRepo &&
                (group.projectScope == null || repo.projectId in group.projectScope)
        }
        var count = 0
        matchedGroups.forEach { group ->
            try {
                federationRepositoryService.autoEnableFederation(
                    projectId = repo.projectId,
                    repoName = repo.name,
                    federationGroupId = group.id!!,
                    currentClusterId = group.currentClusterId,
                    clusterIds = group.clusterIds
                )
                count++
            } catch (e: Exception) {
                logger.warn("Failed to auto-enable federation for [${repo.projectId}|${repo.name}]: ${e.message}")
            }
        }
        return count
    }

    /** 仅用于查询的最小投影结构，字段名与 MongoDB repository 集合对齐 */
    @Document("repository")
    private data class RepositoryInfo(
        @Id val id: String? = null,
        @Field("projectId") val projectId: String,
        @Field("name") val name: String
    )

    companion object {
        private val logger = LoggerFactory.getLogger(FederationAutoEnableBatchJob::class.java)
    }
}