/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.opdata.service

import cn.hutool.core.date.LocalDateTimeUtil
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.constant.CUSTOM
import com.tencent.bkrepo.common.artifact.constant.PIPELINE
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.operate.api.ProjectUsageStatisticsService
import com.tencent.bkrepo.common.operate.api.pojo.ProjectUsageStatistics
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.job.api.JobClient
import com.tencent.bkrepo.opdata.constant.TO_GIGABYTE
import com.tencent.bkrepo.opdata.extension.UsageComputerExtension
import com.tencent.bkrepo.opdata.model.TProjectMetrics
import com.tencent.bkrepo.opdata.pojo.ProjectBill
import com.tencent.bkrepo.opdata.pojo.ProjectBillStatement
import com.tencent.bkrepo.opdata.pojo.ProjectBillStatementRequest
import com.tencent.bkrepo.opdata.pojo.ProjectMetrics
import com.tencent.bkrepo.opdata.pojo.ProjectMetricsOption
import com.tencent.bkrepo.opdata.pojo.ProjectMetricsRequest
import com.tencent.bkrepo.opdata.pojo.enums.ProjectType
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository
import com.tencent.bkrepo.opdata.util.EasyExcelUtils
import com.tencent.bkrepo.opdata.util.MetricsCacheUtil
import com.tencent.bkrepo.opdata.util.MetricsHandlerThreadPoolExecutor
import com.tencent.bkrepo.opdata.util.StatDateUtil
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata.Companion.KEY_BG_NAME
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata.Companion.KEY_CENTER_NAME
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata.Companion.KEY_DEPT_NAME
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata.Companion.KEY_ENABLED
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata.Companion.KEY_PRODUCT_ID
import com.tencent.devops.plugin.api.PluginManager
import com.tencent.devops.plugin.api.applyExtension
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Service
class ProjectMetricsService (
    private val projectMetricsRepository: ProjectMetricsRepository,
    private val projectClient: ProjectClient,
    private val jobClient: JobClient,
    private val projectUsageStatisticsService: ProjectUsageStatisticsService,
    private val pluginManager: PluginManager,
    ){

    private val projectInfoCache: LoadingCache<String, Optional<ProjectInfo>> = CacheBuilder.newBuilder()
        .maximumSize(DEFAULT_PROJECT_CACHE_SIZE)
        .expireAfterWrite(1, TimeUnit.DAYS)
        .build(CacheLoader.from { key -> Optional.ofNullable(projectClient.getProjectInfo(key).data) })

    private val activeProjectsCache:  LoadingCache<String, MutableSet<String>> by lazy {
        val cacheLoader = object : CacheLoader<String, MutableSet<String>>() {
            override fun load(key: String): MutableSet<String> {
                return when (key) {
                    DOWNLOAD_ACTIVE_PROJECTS -> {
                        jobClient.downloadActiveProjects().data
                            ?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf<String>()
                    }
                    ACTIVE_PROJECTS -> {
                        jobClient.activeProjects().data
                            ?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf<String>()
                    }
                    UPLOAD_ACTIVE_PROJECTS -> {
                        jobClient.uploadActiveProjects().data
                            ?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf<String>()
                    }
                    else -> {
                        mutableSetOf<String>()
                    }
                }
            }
        }
        CacheBuilder.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(cacheLoader)
    }

    fun page(option: ProjectMetricsOption): Page<TProjectMetrics> {
        with(option) {
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val queryResult = if (!projectId.isNullOrEmpty()) {
                projectMetricsRepository.findByProjectIdAndCreatedDateOrderByCreatedDateDesc(
                    projectId!!, createdDate, pageRequest
                )
            } else {
                projectMetricsRepository.findByCreatedDateOrderByCreatedDateDesc(createdDate, pageRequest)
            }
            queryResult.content.forEach {
                it.capSize = it.capSize / TO_GIGABYTE
                it.repoMetrics.forEach {  repo ->
                    repo.size = repo.size / TO_GIGABYTE
                }
            }
            return Pages.ofResponse(pageRequest, queryResult.totalElements, queryResult.content)
        }
    }

    fun list(metricsRequest: ProjectMetricsRequest): List<ProjectMetrics> {
       return getProjectMetrics(metricsRequest)
    }

    fun download(metricsRequest: ProjectMetricsRequest) {
        metricsRequest.showDelta = true
        val records = getProjectMetrics(metricsRequest)
        // 导出
        val includeColumns = mutableSetOf(
            ProjectMetrics::projectId.name,
            ProjectMetrics::capSize.name,
            ProjectMetrics::capSizeOfOneDayBefore.name,
            ProjectMetrics::capSizeOfOneWeekBefore.name,
            ProjectMetrics::capSizeOfOneMonthBefore.name,
            ProjectMetrics::pipelineCapSize.name,
            ProjectMetrics::pCapSizeOfOneDayBefore.name,
            ProjectMetrics::pCapSizeOfOneWeekBefore.name,
            ProjectMetrics::pCapSizeOfOneMonthBefore.name,
            ProjectMetrics::customCapSize.name,
            ProjectMetrics::cCapSizeOfOneDayBefore.name,
            ProjectMetrics::cCapSizeOfOneWeekBefore.name,
            ProjectMetrics::cCapSizeOfOneMonthBefore.name,
            ProjectMetrics::bgName.name,
            ProjectMetrics::deptName.name,
            ProjectMetrics::centerName.name,
            ProjectMetrics::createdDate.name,
            ProjectMetrics::productId.name,
            ProjectMetrics::enabled.name,
            ProjectMetrics::receiveBytes.name,
            ProjectMetrics::responseByte.name,
            ProjectMetrics::receiveBytesOneWeekBefore.name,
            ProjectMetrics::responseByteOneWeekBefore.name,
            ProjectMetrics::receiveBytesOneMonthBefore.name,
            ProjectMetrics::responseByteOneMonthBefore.name,
            )
        val fileName = "大于${metricsRequest.limitSize/TO_GIGABYTE}GB的项目信息"
        EasyExcelUtils.download(records, fileName, ProjectMetrics::class.java, includeColumns)
    }

    fun downloadBillStatement(billStatementRequest: ProjectBillStatementRequest) {
        val records = getProjectBillStatement(billStatementRequest)
        // 导出
        val includeColumns = mutableSetOf(
            ProjectBillStatement::projectId.name,
            ProjectBillStatement::bgName.name,
            ProjectBillStatement::enabled.name,
            ProjectBillStatement::capSize.name,
            ProjectBillStatement::pipelineCapSize.name,
            ProjectBillStatement::customCapSize.name,
            ProjectBillStatement::totalCost.name,
            ProjectBillStatement::startDate.name,
            ProjectBillStatement::endDate.name,
            )
        val fileName = "项目账单明细"
        EasyExcelUtils.download(records, fileName, ProjectBillStatement::class.java, includeColumns)
    }

    private fun getProjectBillStatement(billStatementRequest: ProjectBillStatementRequest): List<ProjectBillStatement> {
        val startDate = LocalDateTime.parse(
            billStatementRequest.startDate, DateTimeFormatter.ISO_DATE_TIME
        ).toLocalDate().atStartOfDay()
        val endDate = LocalDateTime.parse(
            billStatementRequest.endDate, DateTimeFormatter.ISO_DATE_TIME
        ).toLocalDate().atStartOfDay()
        val projectMetrics = MetricsCacheUtil.getProjectMetrics(endDate.format(DateTimeFormatter.ISO_DATE_TIME))
        val projectUsages = getMetricsResult(
            type = null,
            limitSize = 0,
            currentMetrics = projectMetrics,
        )
        val futureList = mutableListOf<Future<ProjectBill>>()
        try {
            projectUsages.forEach {
                futureList.add(
                    MetricsHandlerThreadPoolExecutor.instance.submit(
                    Callable {
                        val totalCost = getBillStatement(billStatementRequest, it.projectId)
                        ProjectBill(
                            projectId = it.projectId,
                            totalCost = totalCost
                        )
                    }
                ))
            }
        } catch (ignore: Exception) {
        }
        val result = mutableListOf<ProjectBillStatement>()
        futureList.forEach {
            try {
                val bill = it.get()
                val projectBill = convertToProjectBillStatement(
                    bgNames = billStatementRequest.bgNames,
                    projectId = bill.projectId,
                    totalCost = bill.totalCost,
                    startDate = startDate,
                    endDate = endDate,
                    list = projectUsages,
                )
                if (projectBill != null) {
                    result.add(projectBill)
                }
            } catch (e: Exception) {
                logger.warn("get bill result error : $e")
            }
        }
        return result
    }

    private fun convertToProjectBillStatement(
        projectId: String,
        bgNames: List<String>,
        totalCost: Double,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        list: List<ProjectMetrics>
    ): ProjectBillStatement? {

        val currentProjectMetric = list.firstOrNull { it.projectId == projectId}
        if (
            bgNames.isNotEmpty() &&
            !currentProjectMetric?.bgName.isNullOrEmpty() &&
            !bgNames.contains(currentProjectMetric?.bgName)
        )  {
            return null
        }
        return ProjectBillStatement(
            projectId = projectId,
            bgName = currentProjectMetric?.bgName,
            enabled = currentProjectMetric?.enabled,
            capSize = currentProjectMetric?.capSize ?: 0,
            pipelineCapSize = currentProjectMetric?.pipelineCapSize ?: 0,
            customCapSize = currentProjectMetric?.customCapSize ?: 0,
            totalCost = totalCost,
            startDate = startDate,
            endDate = endDate
        )
    }


    private fun getBillStatement(
        billStatementRequest: ProjectBillStatementRequest,
        projectId: String,
    ): Double {
        val startDate = LocalDateTime.parse(
            billStatementRequest.startDate, DateTimeFormatter.ISO_DATE_TIME
        ).toLocalDate().atStartOfDay()
        val endDate = LocalDateTime.parse(
            billStatementRequest.endDate, DateTimeFormatter.ISO_DATE_TIME
        ).toLocalDate().atStartOfDay()
        val durationDays = LocalDateTimeUtil.between(startDate, endDate.plusDays(1)).toDays()
        var date = startDate
        val projectMetrics = projectMetricsRepository.findAllByProjectIdAndCreatedDateBetween(
            projectId = projectId, start = startDate.minusDays(1), end = endDate.plusDays(1)
        )
        var bill: Double = 0.0
        while (!date.isAfter(endDate)) {
            val projectMetric = projectMetrics.firstOrNull { it.createdDate == date }
            val usage = projectMetric?.capSize?.div(billStatementRequest.limitSize) ?: 0L
            pluginManager.applyExtension<UsageComputerExtension> {
                bill += billComputerExpression(usage.toDouble(), durationDays.toDouble())
            }
            date = date.plusDays(1)
        }
        return bill
    }


    fun getActiveProjects(): MutableSet<String> {
        return activeProjectsCache.get(ACTIVE_PROJECTS)
    }

    fun getDownloadActiveProjects(): MutableSet<String> {
        return activeProjectsCache.get(DOWNLOAD_ACTIVE_PROJECTS)
    }

    fun getUploadActiveProjects(): MutableSet<String> {
        return activeProjectsCache.get(UPLOAD_ACTIVE_PROJECTS)
    }


    /**
     * 定时将db中的数据更新到缓存中
     */
    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = INIT_DELAY, timeUnit = TimeUnit.MINUTES)
    fun loadCache() {
        val createdDate = StatDateUtil.getStatDate()
        val cacheDateList = listOf(
            createdDate,
            createdDate.minusDays(1).toLocalDate().atStartOfDay(),
            createdDate.minusDays(7).toLocalDate().atStartOfDay(),
            createdDate.minusDays(30).toLocalDate().atStartOfDay()
        )
        cacheDateList.forEach {
            MetricsCacheUtil.getProjectMetrics(
                it.format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }

        ProjectType.values().forEach {
            MetricsCacheUtil.gerProjectNodeNum(it.name)
        }

        listOf(ACTIVE_PROJECTS, DOWNLOAD_ACTIVE_PROJECTS, UPLOAD_ACTIVE_PROJECTS).forEach {
            activeProjectsCache.get(it)
        }

        activeProjectsCache.get(ACTIVE_PROJECTS).forEach {
            try {
                getProject(it)
            } catch (ignore: RemoteErrorCodeException) {
            }
        }
    }

    private fun getProjectMetrics(metricsRequest: ProjectMetricsRequest): List<ProjectMetrics> {
        val createdDate = if (metricsRequest.default) {
            StatDateUtil.getStatDate()
        } else {
            LocalDate.now().minusDays(metricsRequest.minusDay).atStartOfDay()
        }
        var oneDayBeforeMetrics: List<TProjectMetrics>? = null
        var oneWeekBeforeMetrics: List<TProjectMetrics>? = null
        var oneMonthBeforeMetrics: List<TProjectMetrics>? = null

        if (metricsRequest.showDelta) {
            val oneDayBefore = createdDate.minusDays(1).toLocalDate().atStartOfDay()
            val oneWeekBefore = createdDate.minusDays(7).toLocalDate().atStartOfDay()
            val oneMonthBefore = createdDate.minusDays(30).toLocalDate().atStartOfDay()

            oneDayBeforeMetrics = MetricsCacheUtil.getProjectMetrics(
                oneDayBefore.format(DateTimeFormatter.ISO_DATE_TIME)
            )
            oneWeekBeforeMetrics = MetricsCacheUtil.getProjectMetrics(
                oneWeekBefore.format(DateTimeFormatter.ISO_DATE_TIME)
            )
            oneMonthBeforeMetrics = MetricsCacheUtil.getProjectMetrics(
                oneMonthBefore.format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }


        var currentMetrics = MetricsCacheUtil.getProjectMetrics(
            createdDate.format(DateTimeFormatter.ISO_DATE_TIME)
        )
        val activeProjects =  getActiveProjects()

        when (metricsRequest.projectFlag) {
            SHOW_ACTIVE_PROJECTS -> {
                oneDayBeforeMetrics = oneDayBeforeMetrics?.filter { activeProjects.contains(it.projectId) }
                oneWeekBeforeMetrics = oneWeekBeforeMetrics?.filter { activeProjects.contains(it.projectId) }
                oneMonthBeforeMetrics = oneMonthBeforeMetrics?.filter { activeProjects.contains(it.projectId) }
                currentMetrics = currentMetrics.filter { activeProjects.contains(it.projectId) }
            }
            SHOW_INACTIVE_PROJECTS-> {
                oneDayBeforeMetrics = oneDayBeforeMetrics?.filter { !activeProjects.contains(it.projectId) }
                oneWeekBeforeMetrics = oneWeekBeforeMetrics?.filter { !activeProjects.contains(it.projectId) }
                oneMonthBeforeMetrics = oneMonthBeforeMetrics?.filter { !activeProjects.contains(it.projectId) }
                currentMetrics = currentMetrics.filter { !activeProjects.contains(it.projectId) }
            }
            else -> {}
        }

        return getMetricsResult(
            type = metricsRequest.type,
            limitSize = metricsRequest.limitSize,
            currentMetrics = currentMetrics,
            oneDayBeforeMetrics = oneDayBeforeMetrics,
            oneWeekBeforeMetrics = oneWeekBeforeMetrics,
            oneMonthBeforeMetrics = oneMonthBeforeMetrics,
            currentProjectUsageStatistics = sumRecentDaysUsage(createdDate, 1L),
            oneWeekBeforeProjectUsageStatistics = sumRecentDaysUsage(createdDate, 7L),
            oneMonthBeforeProjectUsageStatistics = sumRecentDaysUsage(createdDate, 30L),
        )
    }

    private fun sumRecentDaysUsage(createDate: LocalDateTime, days: Long): Map<String, ProjectUsageStatistics> {
        val start = createDate.minusDays(days - 1).toLocalDate().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = createDate.toLocalDate().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // 默认查询时不会包含end，此处需要包含end这个时间点的数据，因此加1
        return projectUsageStatisticsService.sum(start, end + 1L)
    }

    private fun getMetricsResult(
        type: String?,
        limitSize: Long,
        currentMetrics: List<TProjectMetrics>,
        oneDayBeforeMetrics: List<TProjectMetrics>? = null,
        oneWeekBeforeMetrics: List<TProjectMetrics>? = null,
        oneMonthBeforeMetrics: List<TProjectMetrics>? = null,
        currentProjectUsageStatistics: Map<String, ProjectUsageStatistics>? = null,
        oneWeekBeforeProjectUsageStatistics: Map<String, ProjectUsageStatistics>? = null,
        oneMonthBeforeProjectUsageStatistics: Map<String, ProjectUsageStatistics>? = null,
    ): List<ProjectMetrics> {
        val result = mutableListOf<ProjectMetrics>()
        for (current in currentMetrics) {
            val projectInfo = getProjectMetrics(
                current = current,
                type = type,
                oneDayBefore = oneDayBeforeMetrics?.firstOrNull { it.projectId == current.projectId },
                oneWeekBefore = oneWeekBeforeMetrics?.firstOrNull { it.projectId == current.projectId },
                oneMonthBefore = oneMonthBeforeMetrics?.firstOrNull { it.projectId == current.projectId },
                currentProjectUsageStatistics = currentProjectUsageStatistics?.get(current.projectId),
                oneWeekBeforeProjectUsageStatistics = oneWeekBeforeProjectUsageStatistics?.get(current.projectId),
                oneMonthBeforeProjectUsageStatistics = oneMonthBeforeProjectUsageStatistics?.get(current.projectId),
            )
            if (projectInfo.capSize >= limitSize) {
                val project = getProject(current.projectId)
                result.add(ProjectMetrics(
                    projectId = current.projectId,
                    capSize = projectInfo.capSize / TO_GIGABYTE,
                    capSizeOfOneDayBefore = projectInfo.capSizeOfOneDayBefore / TO_GIGABYTE ,
                    capSizeOfOneWeekBefore = projectInfo.capSizeOfOneWeekBefore / TO_GIGABYTE,
                    capSizeOfOneMonthBefore = projectInfo.capSizeOfOneMonthBefore / TO_GIGABYTE,
                    nodeNum = projectInfo.nodeNum,
                    createdDate = current.createdDate,
                    pipelineCapSize = projectInfo.pipelineCapSize / TO_GIGABYTE,
                    pCapSizeOfOneDayBefore = projectInfo.pCapSizeOfOneDayBefore / TO_GIGABYTE,
                    pCapSizeOfOneWeekBefore = projectInfo.pCapSizeOfOneWeekBefore / TO_GIGABYTE,
                    pCapSizeOfOneMonthBefore = projectInfo.pCapSizeOfOneMonthBefore / TO_GIGABYTE,
                    customCapSize = projectInfo.customCapSize / TO_GIGABYTE,
                    cCapSizeOfOneDayBefore = projectInfo.cCapSizeOfOneDayBefore / TO_GIGABYTE,
                    cCapSizeOfOneWeekBefore = projectInfo.cCapSizeOfOneWeekBefore / TO_GIGABYTE,
                    cCapSizeOfOneMonthBefore = projectInfo.cCapSizeOfOneMonthBefore / TO_GIGABYTE,
                    bgName = project?.metadata?.firstOrNull { it.key == KEY_BG_NAME }?.value as? String?,
                    deptName = project?.metadata?.firstOrNull { it.key == KEY_DEPT_NAME }?.value as? String,
                    centerName = project?.metadata?.firstOrNull { it.key == KEY_CENTER_NAME }?.value as? String,
                    productId = project?.metadata?.firstOrNull { it.key == KEY_PRODUCT_ID }?.value as? Int,
                    enabled = project?.metadata?.firstOrNull { it.key == KEY_ENABLED }?.value as? Boolean,
                    receiveBytes = projectInfo.receiveBytes / TO_GIGABYTE,
                    receiveBytesOneWeekBefore = projectInfo.receiveBytesOneWeekBefore / TO_GIGABYTE,
                    receiveBytesOneMonthBefore = projectInfo.receiveBytesOneMonthBefore / TO_GIGABYTE,
                    responseByte = projectInfo.responseByte / TO_GIGABYTE,
                    responseByteOneWeekBefore = projectInfo.responseByteOneWeekBefore / TO_GIGABYTE,
                    responseByteOneMonthBefore = projectInfo.responseByteOneMonthBefore / TO_GIGABYTE,
                ))
            }
        }
        return result.sortedByDescending { it.capSize }
    }


    private fun getProjectMetrics(
        current: TProjectMetrics,
        type: String?,
        oneDayBefore: TProjectMetrics? = null,
        oneWeekBefore: TProjectMetrics? = null,
        oneMonthBefore: TProjectMetrics? = null,
        currentProjectUsageStatistics: ProjectUsageStatistics? = null,
        oneWeekBeforeProjectUsageStatistics: ProjectUsageStatistics? = null,
        oneMonthBeforeProjectUsageStatistics: ProjectUsageStatistics? = null,
    ): ProjectMetrics {
        val project = getProject(current.projectId)
        return if (type.isNullOrEmpty()) {

            val pipelineCapSize = filterByRepoName(current, PIPELINE)
            val pCapSizeOfOneDayBefore = filterByRepoName(oneDayBefore, PIPELINE)
            val pCapSizeOfOneWeekBefore = filterByRepoName(oneWeekBefore, PIPELINE)
            val pCapSizeOfOneMonthBefore = filterByRepoName(oneMonthBefore, PIPELINE)


            val customCapSize = filterByRepoName(current, CUSTOM)
            val cCapSizeOfOneDayBefore = filterByRepoName(oneDayBefore, CUSTOM)
            val cCapSizeOfOneWeekBefore = filterByRepoName(oneWeekBefore, CUSTOM)
            val cCapSizeOfOneMonthBefore = filterByRepoName(oneMonthBefore, CUSTOM)

            ProjectMetrics(
                projectId = current.projectId,
                nodeNum = current.nodeNum,
                capSize = current.capSize,
                capSizeOfOneDayBefore = current.capSize - (oneDayBefore?.capSize ?: 0),
                capSizeOfOneWeekBefore = current.capSize - (oneWeekBefore?.capSize ?: 0),
                capSizeOfOneMonthBefore = current.capSize - (oneMonthBefore?.capSize ?: 0),
                createdDate = current.createdDate,
                pipelineCapSize = pipelineCapSize,
                pCapSizeOfOneDayBefore = pipelineCapSize - pCapSizeOfOneDayBefore,
                pCapSizeOfOneWeekBefore = pipelineCapSize - pCapSizeOfOneWeekBefore,
                pCapSizeOfOneMonthBefore = pipelineCapSize - pCapSizeOfOneMonthBefore,
                customCapSize = customCapSize,
                cCapSizeOfOneDayBefore = customCapSize - cCapSizeOfOneDayBefore,
                cCapSizeOfOneWeekBefore = customCapSize - cCapSizeOfOneWeekBefore,
                cCapSizeOfOneMonthBefore = customCapSize - cCapSizeOfOneMonthBefore,
                bgName = project?.metadata?.firstOrNull { it.key == KEY_BG_NAME }?.value as? String,
                deptName = project?.metadata?.firstOrNull { it.key == KEY_DEPT_NAME }?.value as? String,
                centerName = project?.metadata?.firstOrNull { it.key == KEY_CENTER_NAME }?.value as? String,
                productId = project?.metadata?.firstOrNull { it.key == KEY_PRODUCT_ID }?.value as? Int,
                enabled = project?.metadata?.firstOrNull { it.key == KEY_ENABLED }?.value as? Boolean,
                receiveBytes = currentProjectUsageStatistics?.receiveBytes.ifNull(0L),
                receiveBytesOneWeekBefore = oneWeekBeforeProjectUsageStatistics?.receiveBytes.ifNull(0L),
                receiveBytesOneMonthBefore = oneMonthBeforeProjectUsageStatistics?.receiveBytes.ifNull(0L),
                responseByte = currentProjectUsageStatistics?.responseBytes.ifNull(0L),
                responseByteOneWeekBefore = oneWeekBeforeProjectUsageStatistics?.responseBytes.ifNull(0L),
                responseByteOneMonthBefore = oneMonthBeforeProjectUsageStatistics?.responseBytes.ifNull(0L),
            )
        } else {

            val (sizeOfRepoType, nodeNumOfRepoType) = filterByRepoType(current, type)
            val (sizeOfOneDayBefore, _) = filterByRepoType(oneDayBefore, type)
            val (sizeOfOneWeekBefore, _) = filterByRepoType(oneWeekBefore, type)
            val (sizeOfOneMonthBefore, _) = filterByRepoType(oneMonthBefore, type)

            ProjectMetrics(
                projectId = current.projectId,
                nodeNum = nodeNumOfRepoType,
                capSize = sizeOfRepoType,
                capSizeOfOneDayBefore = sizeOfRepoType - sizeOfOneDayBefore,
                capSizeOfOneWeekBefore = sizeOfRepoType - sizeOfOneWeekBefore,
                capSizeOfOneMonthBefore = sizeOfRepoType - sizeOfOneMonthBefore,
                createdDate = current.createdDate,
                pipelineCapSize = 0,
                customCapSize = 0,
                bgName = project?.metadata?.firstOrNull { it.key == KEY_BG_NAME }?.value as? String,
                deptName = project?.metadata?.firstOrNull { it.key == KEY_DEPT_NAME }?.value as? String,
                centerName = project?.metadata?.firstOrNull { it.key == KEY_CENTER_NAME }?.value as? String,
                productId = project?.metadata?.firstOrNull { it.key == KEY_PRODUCT_ID }?.value as? Int,
                enabled = project?.metadata?.firstOrNull { it.key == KEY_ENABLED }?.value as? Boolean,
                receiveBytes = currentProjectUsageStatistics?.receiveBytes.ifNull(0L),
                receiveBytesOneWeekBefore = oneWeekBeforeProjectUsageStatistics?.receiveBytes.ifNull(0L),
                receiveBytesOneMonthBefore = oneMonthBeforeProjectUsageStatistics?.receiveBytes.ifNull(0L),
                responseByte = currentProjectUsageStatistics?.responseBytes.ifNull(0L),
                responseByteOneWeekBefore = oneWeekBeforeProjectUsageStatistics?.responseBytes.ifNull(0L),
                responseByteOneMonthBefore = oneMonthBeforeProjectUsageStatistics?.responseBytes.ifNull(0L),
            )
        }
    }

    private fun Long?.ifNull(default: Long) = this ?: default

    private fun getProject(projectId: String): ProjectInfo? {
        return projectInfoCache.get(projectId).orElse(null)
    }

    private fun filterByRepoName(metric: TProjectMetrics?, repoName: String): Long {
        return metric?.repoMetrics?.firstOrNull { it.repoName == repoName }?.size ?: 0
    }

    private fun filterByRepoType(metric: TProjectMetrics?, repoType: String): Pair<Long, Long> {
        var sizeOfRepoType: Long = 0
        var nodeNumOfRepoType: Long = 0
        metric?.repoMetrics?.forEach { repo ->
            if (repo.type == repoType) {
                sizeOfRepoType += repo.size
                nodeNumOfRepoType += repo.num
            }
        }
        return Pair(sizeOfRepoType, nodeNumOfRepoType)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectMetricsService::class.java)

        private const val DEFAULT_PROJECT_CACHE_SIZE = 100_000L
        private const val FIXED_DELAY = 30L
        private const val INIT_DELAY = 3L
        private const val DOWNLOAD_ACTIVE_PROJECTS = "downloadActiveProjects"
        private const val ACTIVE_PROJECTS = "activeProjects"
        private const val UPLOAD_ACTIVE_PROJECTS = "uploadActiveProjects"
        private const val SHOW_ACTIVE_PROJECTS = 1
        private const val SHOW_INACTIVE_PROJECTS = 2

    }
}
