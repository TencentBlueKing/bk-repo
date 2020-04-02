package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.model.NodeModel
import com.tencent.bkrepo.opdata.model.ProjectModel
import com.tencent.bkrepo.opdata.model.RepoModel
import com.tencent.bkrepo.opdata.model.TProjectMetrics
import com.tencent.bkrepo.opdata.pojo.RepoMetrics
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ProjectStatJob {

    @Autowired
    private lateinit var projectModel: ProjectModel

    @Autowired
    private lateinit var repoModel: RepoModel

    @Autowired
    private lateinit var nodeModel: NodeModel

    @Autowired
    private lateinit var projectMetricsRepository: ProjectMetricsRepository

    @Scheduled(cron = "00 15 * * * ?")
    @SchedulerLock(name = "ProjectStatJob", lockAtMostFor = "PT1H")
    fun statProjectRepoSize() {
        logger.info("start to stat node table metrics")
        val projects = projectModel.getProjectList()
        var result = mutableListOf<TProjectMetrics>()
        projects.forEach {
            var repoCapSize = 0L
            var repoNodeNum = 0L
            val projectId = it.name
            val repos = repoModel.getRepoListByProjectId(it.name)
            var repoMetrics = mutableListOf<RepoMetrics>()
            repos.forEach {
                val repoName = it.name
                val nodeSize = nodeModel.getNodeSize(projectId, repoName)
                repoCapSize += nodeSize.size
                repoNodeNum += nodeSize.num
                repoMetrics.add(RepoMetrics(repoName, nodeSize.size / (1024 * 1024 * 1024), nodeSize.num))
            }
            result.add(TProjectMetrics(projectId, repoNodeNum, repoCapSize / (1024 * 1024 * 1024), repoMetrics))
        }
        projectMetricsRepository.deleteAll()
        projectMetricsRepository.insert(result)
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
