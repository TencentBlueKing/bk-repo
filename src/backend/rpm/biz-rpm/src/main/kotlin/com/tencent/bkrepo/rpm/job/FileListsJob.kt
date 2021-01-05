package com.tencent.bkrepo.rpm.job

<<<<<<< HEAD
=======
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.repository.api.RepositoryClient
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
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
<<<<<<< HEAD
=======
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    private lateinit var jobService: JobService

    @Scheduled(fixedDelay = 60 * 1000)
    @SchedulerLock(name = "FileListsJob", lockAtMostFor = "PT60M")
    fun updateFilelistsIndex() {
        logger.info("update filelists index start")
        val startMillis = System.currentTimeMillis()
<<<<<<< HEAD
        val repoList = jobService.getAllRpmRepo()
        repoList?.let {
            for (repo in repoList) {
                val rpmConfiguration = repo.configuration
                val enabledFileLists = rpmConfiguration.getBooleanSetting("enabledFileLists") ?: false
                if (!enabledFileLists) {
=======
        val repoList = repositoryClient.pageByType(0, 100, "RPM").data?.records

        repoList?.let {
            for (repo in repoList) {
                val rpmConfiguration = repo.configuration as RpmLocalConfiguration
                if (rpmConfiguration.enabledFileLists != true) {
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
                    logger.info("filelists[${repo.projectId}|${repo.name}] disabled, skip")
                    continue
                }
                logger.info("update filelists index[${repo.projectId}|${repo.name}] start")
<<<<<<< HEAD
                val repodataDepth = rpmConfiguration.getIntegerSetting("repodataDepth") ?: 0
=======
                val repodataDepth = rpmConfiguration.repodataDepth ?: 0
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
                val targetSet = RpmCollectionUtils.filterByDepth(jobService.findRepodataDirs(repo), repodataDepth)
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
