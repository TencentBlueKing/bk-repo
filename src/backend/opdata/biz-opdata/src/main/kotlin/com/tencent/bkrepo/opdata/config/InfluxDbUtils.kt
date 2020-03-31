package com.tencent.bkrepo.opdata.config

import org.influxdb.InfluxDB
import org.influxdb.InfluxDBFactory
import org.slf4j.LoggerFactory

class InfluxDbUtils(
    private val userName: String?,
    private val password: String?,
    private val url: String?,
    var database: String?,
    retentionPolicy: String?
) {
    private val retentionPolicy: String

    // database instance
    public var influxDB: InfluxDB

    /**
     * connect database
     *
     * @return influxDb实例
     */
    private fun influxDbBuild(): InfluxDB {
        if (influxDB == null) {
            influxDB = InfluxDBFactory.connect(url, userName, password)
        }

        try {
            // val result = influxDB!!.query(Query("SHOW DATABASES")) as List<String>
            // influxDB!!.query(Query("CREATE DATABASE", database))
            influxDB.setDatabase(database)
        } catch (e: Exception) {
            logger.error("create influx db failed, error: {}", e.message)
        } finally {
            influxDB.setRetentionPolicy(retentionPolicy)
        }
        influxDB!!.setLogLevel(InfluxDB.LogLevel.BASIC)
        return influxDB
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InfluxDbUtils::class.java)
        // 数据保存策略
        var policyNamePix = "logRetentionPolicy_"
    }

    init {
        this.retentionPolicy = if (retentionPolicy == null || "" == retentionPolicy) "autogen" else retentionPolicy
        influxDB = influxDbBuild()
    }
}
