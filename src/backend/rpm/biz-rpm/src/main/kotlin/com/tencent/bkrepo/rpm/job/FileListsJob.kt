package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.util.RpmCollectionUtils
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class FileListsJob {

    @Autowired
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
    private lateinit var jobService: JobService

    @Scheduled(fixedDelay = 60 * 1000)
    @SchedulerLock(name = "FileListsJob", lockAtMostFor = "PT60M")
    fun updateFileListsIndex() {
        logger.info("update filelists index start")
        val startMillis = System.currentTimeMillis()
        val repoList = repositoryClient.pageByType(0, 100, "RPM").data?.records

        repoList?.let {
            for (repo in repoList) {
                val rpmConfiguration = repo.configuration as RpmLocalConfiguration
                if (rpmConfiguration.enabledFileLists != true) {
                    logger.info("filelists[${repo.projectId}|${repo.name}] disabled, skip")
                    continue
                }
                logger.info("update filelists index[${repo.projectId}|${repo.name}] start")
                val repodataDepth = rpmConfiguration.repodataDepth ?: 0
                val targetSet = RpmCollectionUtils.filterByDepth(jobService.findRepoDataByRepo(repo), repodataDepth)
                for (repoDataPath in targetSet) {
                    logger.info("update filelists index[${repo.projectId}|${repo.name}|$repoDataPath] start")
                    jobService.batchUpdateIndex(repo, repoDataPath, IndexType.FILELISTS, 30)
                    logger.info("update filelists index[${repo.projectId}|${repo.name}|$repoDataPath] done")
                }
                logger.info("update filelists index[${repo.projectId}|${repo.name}] done")
            }
        }
        logger.info("update filelists index done, cost time: ${System.currentTimeMillis() - startMillis} ms")
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FileListsJob::class.java)
    }
}
