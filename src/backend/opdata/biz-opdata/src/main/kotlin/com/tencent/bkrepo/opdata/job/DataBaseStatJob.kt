package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.config.InfluxDbConfig
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
class DataBaseStatJob {

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Autowired
    private lateinit var influxDbConfig: InfluxDbConfig

    @Scheduled(cron = "00 */2 * * * ?")
    @SchedulerLock(name = "DataBaseStatJob", lockAtMostFor = "PT1H")
    fun statDatabaseInfo() {
        val inluxdDb = influxDbConfig.influxDbUtils().influxDB
        val batchPoints = BatchPoints
            .database("bkrepo")
            .build()
        val timeMillis = System.currentTimeMillis()
        for (i in 0..255) {
            val table = "node_" + i.toString()
            val query = Query(Criteria.where("folder").`is`(false))
            val result = mongoTemplate.find(query, MutableMap::class.java, table)
            var tableSize = 0L
            var nodeNum = 0L
            result.forEach {
                val size = it.get("size") as Long
                tableSize += size
                nodeNum++
            }

            val point = Point.measurement("nodeInfo")
                .time(timeMillis, TimeUnit.MILLISECONDS)
                .addField("size", tableSize / (1024 * 1024))
                .addField("num", nodeNum)
                .tag("table", table)
                .build()
            batchPoints.point(point)
        }
        inluxdDb.write(batchPoints)
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
