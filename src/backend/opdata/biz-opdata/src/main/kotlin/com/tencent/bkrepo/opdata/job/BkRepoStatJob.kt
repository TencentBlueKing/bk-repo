package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.model.TBkRepoMetrics
import com.tencent.bkrepo.opdata.repository.BkRepoMetricsRepository
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.Calendar

/**
 * stat bkrepo running status at
 * 00 45 23 * * ?
 * @author: owenlxu
 * @date: 2020/01/03
 */
@Component
class BkRepoStatJob {

    @Autowired
    private lateinit var bkRepoMetricsRepository: BkRepoMetricsRepository

    @Autowired
    private lateinit var projectMetricsRepository: ProjectMetricsRepository

    @Scheduled(cron = "00 45 23 * * ?")
    @SchedulerLock(name = "BkRepoStatJob", lockAtMostFor = "PT1H")
    fun statBkRepoInfo() {
        logger.info("start to stat bkrepo metrics")
        val projectsMetrics = projectMetricsRepository.findAll()
        var tms = Calendar.getInstance()
        val date =
            tms.get(Calendar.YEAR).toString() + "-" + tms.get(Calendar.MONTH).toString() + "-" + tms.get(Calendar.DAY_OF_MONTH).toString()
        var capSize = 0L
        var nodeNum = 0L
        var projectNum = 0L
        projectsMetrics.forEach {
            projectNum += 1
            capSize += it.capSize
            nodeNum += it.nodeNum
        }
        val data = TBkRepoMetrics(date, projectNum, nodeNum, capSize)
        bkRepoMetricsRepository.insert(data)
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
