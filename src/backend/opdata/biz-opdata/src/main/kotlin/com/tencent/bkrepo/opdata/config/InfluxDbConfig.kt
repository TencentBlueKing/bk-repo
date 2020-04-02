package com.tencent.bkrepo.opdata.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class InfluxDbConfig {
    @Value("\${spring.influx.url:''}")
    private val influxDBUrl: String? = null
    @Value("\${spring.influx.user:''}")
    private val userName: String? = null
    @Value("\${spring.influx.password:''}")
    private val password: String? = null
    @Value("\${spring.influx.database:''}")
    public val database: String? = null

    fun influxDbUtils(): InfluxDbUtils {
        return InfluxDbUtils(userName, password, influxDBUrl, database, "")
    }
}
