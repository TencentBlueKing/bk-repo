package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.rpm.pojo.IndexType
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PrimaryJob {

    @Autowired
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
    private lateinit var jobService: JobService

    // 每次任务间隔100ms
    @Scheduled(fixedDelay = 100)
    @SchedulerLock(name = "PrimaryJob", lockAtMostFor = "PT10M")
    fun checkPrimaryXml() {
        val startMillis = System.currentTimeMillis()
        val repoList = repositoryClient.pageByType(0, 100, "RPM").data?.records

        repoList?.let {
            for (repo in repoList) {
                logger.info("sync primary (${repo.projectId}|${repo.name}) start")
                val rpmConfiguration = repo.configuration as RpmLocalConfiguration
                val repodataDepth = rpmConfiguration.repodataDepth ?: 0
                val targetSet = mutableSetOf<String>()
                jobService.findRepoDataByRepo(repo, "/", repodataDepth, targetSet)
                for (repoDataPath in targetSet) {
                    logger.info("sync primary (${repo.projectId}|${repo.name}|$repoDataPath) start")
                    jobService.syncIndex(repo, repoDataPath, IndexType.PRIMARY)
                    logger.info("sync primary (${repo.projectId}|${repo.name}|$repoDataPath) done")
                }
                logger.info("sync primary (${repo.projectId}|${repo.name}) done")
            }
        }
        logger.info("sync primary, cost time: ${System.currentTimeMillis() - startMillis} ms")
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PrimaryJob::class.java)
    }
}
