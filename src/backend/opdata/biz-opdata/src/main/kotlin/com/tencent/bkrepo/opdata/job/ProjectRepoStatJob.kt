package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.config.InfluxDbConfig
import com.tencent.bkrepo.opdata.model.ProjectModel
import com.tencent.bkrepo.opdata.model.RepoModel
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.influxdb.dto.BatchPoints
import org.influxdb.dto.Point
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ProjectRepoStatJob {

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

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
        var inluxdDb = influxDbConfig.influxDbUtils().getInstance()
        if (null == inluxdDb) {
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
                var repoSize = 0L
                var nodeNum = 0L
                val repoName = it
                val query = Query(
                    Criteria.where("folder").`is`(false)
                        .and("projectId").`is`(projectId)
                        .and("repoName").`is`(repoName)
                )
                val results = mongoTemplate.find(query, MutableMap::class.java, table)
                results.forEach {
                    val size = it.get("size") as Long
                    repoSize += size
                    nodeNum++
                }
                if (repoSize != 0L && nodeNum != 0L) {
                    val point = Point.measurement("repoInfo")
                        .time(timeMillis, TimeUnit.MILLISECONDS)
                        .addField("size", repoSize / (1024 * 1024 * 1024))
                        .addField("num", nodeNum)
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
        inluxdDb = null
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
