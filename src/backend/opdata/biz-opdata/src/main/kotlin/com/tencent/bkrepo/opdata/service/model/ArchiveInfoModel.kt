package com.tencent.bkrepo.opdata.service.model

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.opdata.config.OpArchiveOrGcProperties
import com.tencent.bkrepo.opdata.service.model.GcInfoModel
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class ArchiveInfoModel @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val opArchiveOrGcProperties: OpArchiveOrGcProperties,
) {

    private var archiveInfo: Map<String, Array<Long>> = emptyMap()

    @Volatile
    private var refreshing = false

    /**
     * <repo,[archiveNum,archiveSize]>
     * */
    fun info(): Map<String, Array<Long>> {
        if (archiveInfo.isEmpty() && !refreshing) {
            archiveInfo = stat()
        }
        return archiveInfo
    }

    @Scheduled(cron = "0 0 4 * * ?")
    @SchedulerLock(name = "ArchiveInfoStatJob", lockAtMostFor = "PT24H")
    fun refresh() {
        archiveInfo = stat()
    }

    private fun stat(): Map<String, Array<Long>> {
        refreshing = true
        if (!opArchiveOrGcProperties.archiveEnabled) return emptyMap()
        logger.info("Start update archive metrics.")
        val statistics = ConcurrentHashMap<String, Array<AtomicLong>>()

        if (opArchiveOrGcProperties.archiveProjects.isEmpty()) {
            processAllProjects(statistics)
        } else {
            processSpecificProjects(statistics)
        }

        statistics[SUM] = GcInfoModel.Companion.reduce(statistics)
        logger.info("Update archive metrics successful.")
        refreshing = false
        return statistics.mapValues { arrayOf(it.value[0].get(), it.value[1].get()) }
    }

    private fun processAllProjects(statistics: ConcurrentHashMap<String, Array<AtomicLong>>) {
        // 处理所有项目的归档数据
        val query = Query().cursorBatchSize(GcInfoModel.Companion.BATCH_SIZE)
        // 遍历节点表
        processProject(query, statistics)
    }

    private fun processSpecificProjects(statistics: ConcurrentHashMap<String, Array<AtomicLong>>) {
        // 处理指定项目的归档数据
        opArchiveOrGcProperties.archiveProjects.forEach { project ->
            val query = Query(Criteria.where("name").isEqualTo(project))
            processProject(query, statistics)
        }
    }

    private fun processProject(query: Query, statistics: ConcurrentHashMap<String, Array<AtomicLong>>) {
        mongoTemplate.find(query, Project::class.java, "project").forEach { project ->
            val repoArchiveStatInfoStr = project.metadata.find { it.key == "archiveStatInfo" }?.value?.toString()
                ?: return
            try {
                val repoArchiveStatInfo =
                    repoArchiveStatInfoStr.readJsonString<ConcurrentHashMap<String, RepoArchiveStatInfo>>()
                updateStatistics(statistics, project.name, repoArchiveStatInfo)
            } catch (e: Exception) {
                logger.error("Parse repoArchiveStatInfo failed.", e)
                return
            }
        }
    }

    private fun updateStatistics(
        statistics: ConcurrentHashMap<String, Array<AtomicLong>>,
        projectId: String,
        projectArchiveInfo: ConcurrentHashMap<String, RepoArchiveStatInfo>
    ) {
        projectArchiveInfo.forEach { (repoName, repo) ->
            val repoStr = "$projectId/$repoName"
            // 数组信息: [归档文件数量, 归档文件总大小]
            val counts = statistics.getOrPut(repoStr) { arrayOf(AtomicLong(), AtomicLong()) }
            counts[0].addAndGet(repo.num)
            counts[1].addAndGet(repo.size)
        }

    }

    data class Project(val name: String, val displayName: String, val metadata: List<ProjectMetadata> = emptyList())

    data class RepoArchiveStatInfo(
        val repoName: String,
        var size: Long,
        var num: Long,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveInfoModel::class.java)
        private const val SUM = "SUM"
    }
}