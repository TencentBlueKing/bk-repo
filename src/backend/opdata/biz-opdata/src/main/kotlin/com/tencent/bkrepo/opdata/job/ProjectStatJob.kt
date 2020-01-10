package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.model.NodeModel
import com.tencent.bkrepo.opdata.model.ProjectModel
import com.tencent.bkrepo.opdata.model.RepoModel
import com.tencent.bkrepo.opdata.model.TProjectMetrics
import com.tencent.bkrepo.opdata.pojo.RepoMetrics
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository
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

    @Scheduled(cron = "00 45 * * * ?")
    fun statProjectRepoSize() {
        logger.info("start to stat project metrics")
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
                val size = nodeModel.getNodeSize(projectId, repoName)
                repoCapSize += size
                val num = nodeModel.getNodeNum(projectId, repoName)
                repoNodeNum += num
                repoMetrics.add(RepoMetrics(repoName, size / (1024 * 1024 * 1024), num))
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
