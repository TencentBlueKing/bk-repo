package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.rpm.pojo.IndexType
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OthersJob {

    @Autowired
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
    private lateinit var jobService: JobService

    // 每次任务间隔100ms
    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "OthersJob", lockAtMostFor = "PT10M")
    fun checkPrimaryXml() {
        val startMillis = System.currentTimeMillis()
        val repoList = repositoryClient.pageByType(0, 100, "RPM").data?.records

        repoList?.let {
            for (repo in repoList) {
                val rpmConfiguration = repo.configuration
                val repodataDepth = rpmConfiguration.getIntegerSetting("repodataDepth") ?: 0
                val targetSet = mutableSetOf<String>()
                jobService.findRepoDataByRepo(repo, "/", repodataDepth, targetSet)
                for (repoDataPath in targetSet) {
                    logger.info("sync other (${repo.projectId}|${repo.name}|$repoDataPath) start")
                    jobService.syncIndex(repo, repoDataPath, IndexType.OTHERS)
                    logger.info("sync other (${repo.projectId}|${repo.name}|$repoDataPath) done, cost time: ${System.currentTimeMillis() - startMillis} ms")
                }
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OthersJob::class.java)
    }
}
