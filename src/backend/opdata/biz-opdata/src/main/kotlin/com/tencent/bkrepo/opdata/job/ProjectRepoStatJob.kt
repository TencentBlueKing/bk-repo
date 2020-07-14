package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.config.InfluxDbConfig
import com.tencent.bkrepo.opdata.model.NodeModel
import com.tencent.bkrepo.opdata.model.ProjectModel
import com.tencent.bkrepo.opdata.model.RepoModel
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.influxdb.dto.BatchPoints
import org.influxdb.dto.Point
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * stat bkrepo running status
 *
 * @author: owenlxu
 * @date: 2020/01/03
 */
@Component
class ProjectRepoStatJob {

    @Autowired
    private lateinit var nodeModel: NodeModel

    @Autowired
    private lateinit var projectModel: ProjectModel

    @Autowired
    private lateinit var repoModel: RepoModel

    @Autowired
    private lateinit var influxDbConfig: InfluxDbConfig

    @Scheduled(cron = "00 00 */1 * * ?")
    @SchedulerLock(name = "ProjectRepoStatJob", lockAtMostFor = "PT1H")
    fun statProjectRepoSize() {
        logger.info("start to stat project metrics")
        val inluxdDb = influxDbConfig.influxDbUtils().getInstance() ?: kotlin.run {
            logger.error("init influxdb fail")
            return
        }
        val timeMillis = System.currentTimeMillis()
        val batchPoints = BatchPoints
            .database(influxDbConfig.database)
            .build()
        val projects = projectModel.getProjectList()
        projects.forEach {
            val projectId = it.name
            val repos = repoModel.getRepoListByProjectId(it.name)
            val table = "node_" + (projectId.hashCode() and 255).toString()
            repos.forEach {
                val repoName = it
                val result = nodeModel.getNodeSize(projectId, repoName)
                if (result.size != 0L && result.num != 0L) {
                    logger.info("project : [$projectId],repo: [$repoName],size:[$result]")
                    val point = Point.measurement("repoInfo")
                        .time(timeMillis, TimeUnit.MILLISECONDS)
                        .addField("size", result.size / (1024 * 1024 * 1024))
                        .addField("num", result.num)
                        .tag("projectId", projectId)
                        .tag("repoName", repoName)
                        .tag("table", table)
                        .build()
                    batchPoints.point(point)
                }
            }
        }
        inluxdDb.write(batchPoints)
        inluxdDb.close()
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
