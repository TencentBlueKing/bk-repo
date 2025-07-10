/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.batch.task.usage

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.retry
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.ProjectMonthMetricReportJobProperties
import com.tencent.bkrepo.job.pojo.project.TProjectMetricsDailyAvgRecord
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 上报每月用量
 */
@Component
@EnableConfigurationProperties(ProjectMonthMetricReportJobProperties::class)
class ProjectMonthMetricReportJob(
    val properties: ProjectMonthMetricReportJobProperties,
    private val mongoTemplate: MongoTemplate
) : DefaultContextJob(properties) {

    override fun doStart0(jobContext: JobContext) {
        if (!filterProperties()) return
        // 优先上报配置需要的月份数据
        if (properties.monthList.isNotEmpty()) {
            val costDate = mutableSetOf<String>()
            costDate.addAll(properties.monthList)
            costDate.forEach {
                findAndReportMonthData(it)
            }
        }
        // 然后上报配置需要的日数据
        if (properties.dayList.isNotEmpty()) {
            val costDay = mutableSetOf<String>()
            costDay.addAll(properties.dayList)
            costDay.forEach {
                findAndReportDailyData(it)
            }
        }
        // 最后上报前一天的数据
        val yesterday = LocalDate.now().minusDays(1)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        findAndReportDailyData(yesterday)
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    private fun filterProperties(): Boolean {
        if (properties.reportHost.isBlank() || properties.reportUrl.isBlank()) return false
        if (properties.reportPlatformKey.isBlank() || properties.reportServiceName.isBlank()) return false
        return true
    }

    private fun findAndReportDailyData(reportDay: String) {
        logger.info("start to report day $reportDay usage...")
        val criteria = Criteria.where(TProjectMetricsDailyAvgRecord::costDateDay.name).isEqualTo(reportDay)
        findAndReportData(criteria)
        logger.info("report day $reportDay usage finished")
    }

    private fun findAndReportMonthData(reportMonth: String) {
        logger.info("start to report month $reportMonth usage...")
        val criteria = Criteria.where(TProjectMetricsDailyAvgRecord::costDate.name).isEqualTo(reportMonth)
        findAndReportData(criteria)
        logger.info("report month $reportMonth usage finished")
    }

    private fun findAndReportData(criteria: Criteria) {
        val query = Query.query(criteria).cursorBatchSize(BATCH_SIZE)
        val projectMonthUsage: MutableList<ProjectMonthUsage> = mutableListOf()
        mongoTemplate.find(
            query, TProjectMetricsDailyAvgRecord::class.java,
            COLLECTION_NAME_PROJECT_METRICS_DAILY_AVG_RECORD
        ).forEach {
            projectMonthUsage.add(convertToProjectMonthUsage(it))
            if (projectMonthUsage.size >= properties.batchUploadSize) {
                buildAndReportUsageData(projectMonthUsage)
                projectMonthUsage.clear()
            }
        }
        if (projectMonthUsage.isNotEmpty()) {
            buildAndReportUsageData(projectMonthUsage)
        }
    }

    private fun buildAndReportUsageData(projectMonthUsage: MutableList<ProjectMonthUsage>) {
        logger.info("report project month usage with size ${projectMonthUsage.size}")
        val bkMonthUsage = BkMonthUsage(
            dataSourceName = properties.reportServiceName,
            bills = projectMonthUsage,
            month = projectMonthUsage.first().costDate
        )
        val bkMonthUsageSummary = BkMonthUsageSummary(bkMonthUsage)
        reportUsageData(bkMonthUsageSummary)
    }

    private fun reportUsageData(monthUsageSummary: BkMonthUsageSummary) {
        val requestBody = JsonUtils.objectMapper.writeValueAsString(monthUsageSummary)
            .toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
        val url = "${properties.reportHost}/${properties.reportUrl}"
        try {
            val request = Request.Builder().url(url).header(PLATFORM_KEY_HEADER, properties.reportPlatformKey)
                .post(requestBody).build()
            retry(RETRY_COUNT, 1) {
                doRequest(okHttpClient, request)
            }
        } catch (exception: Exception) {
            logger.warn("report usage data error:", exception)
        }
    }

    private fun doRequest(
        okHttpClient: OkHttpClient,
        request: Request,
    ) {
        try {
            okHttpClient.newBuilder().build().newCall(request).execute().use {
                if (!it.isSuccessful) {
                    throw RuntimeException("report usage request error, response code is ${it.code}")
                }
                val bkResponse = JsonUtils.objectMapper.readValue(it.body!!.byteStream(), BkResponse::class.java)
                if (bkResponse.result || bkResponse.code == 200) {
                    return
                }
                val logMsg = "report usage request url ${request.url} failed, " +
                    "code: ${bkResponse.code}, error is: ${bkResponse.message}"
                logger.warn(logMsg)
                throw RuntimeException(logMsg)
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private fun convertToProjectMonthUsage(project: TProjectMetricsDailyAvgRecord): ProjectMonthUsage {
        return ProjectMonthUsage(
            projectId = project.projectId,
            costDateDay = project.costDateDay,
            name = project.name,
            flag = project.flag,
            costDate = project.costDate,
            usage = project.usage,
            bgName = project.bgName,
        )
    }

    data class BkMonthUsageSummary(
        @JsonProperty(value = "data_source_bills", required = true)
        var dataSourceBills: BkMonthUsage
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class BkMonthUsage(
        @JsonProperty(value = "data_source_name", required = true)
        var dataSourceName: String,
        var bills: MutableList<ProjectMonthUsage> = mutableListOf(),
        var month: String? = null,
        @JsonProperty(value = "is_overwrite")
        var isOverwrite: Boolean? = null
    )

    data class ProjectMonthUsage(
        @JsonProperty(value = "cost_date")
        var costDate: String,
        @JsonProperty(value = "project_id")
        var projectId: String,
        var name: String,
        @JsonProperty(value = "service_type")
        var serviceType: String = "制品库服务",
        var kind: String = "BKREPO",
        var usage: Double,
        @JsonProperty(value = "bg_name")
        var bgName: String,
        var flag: Boolean,
        @JsonProperty(value = "cost_date_day")
        var costDateDay: String,
    )

    data class BkResponse(
        var result: Boolean,
        var code: Int,
        var message: String,
        var errors: String? = null
    )

    private val okHttpClient = okhttp3.OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectMonthMetricReportJob::class.java)
        private const val COLLECTION_NAME_PROJECT_METRICS_DAILY_AVG_RECORD = "project_metrics_daily_avg_record"
        private const val PLATFORM_KEY_HEADER = "Platform-Key"
        private const val RETRY_COUNT = 3
    }
}
