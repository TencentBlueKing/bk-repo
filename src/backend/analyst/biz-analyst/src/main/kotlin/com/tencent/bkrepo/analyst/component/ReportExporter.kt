package com.tencent.bkrepo.analyst.component

import com.tencent.bkrepo.analyst.configuration.ReportExportProperties
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.report.Component
import com.tencent.bkrepo.analyst.pojo.report.ComponentLicense
import com.tencent.bkrepo.analyst.pojo.report.Report
import com.tencent.bkrepo.analyst.pojo.report.SensitiveContent
import com.tencent.bkrepo.analyst.pojo.report.Vulnerability
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.LicenseResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.SecurityResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.SensitiveResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanExecutorResult
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.stream.constant.BinderType
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * 导出扫描结果报告到消息队列用于计算平台分析
 */
@org.springframework.stereotype.Component
class ReportExporter(
    private val reportProperties: ReportExportProperties,
    private val messageSupplier: MessageSupplier
) {
    fun export(subtask: TSubScanTask, result: ScanExecutorResult) {
        if (!shouldExport(subtask, result)) {
            return
        }
        check(result is StandardScanExecutorResult)
        val reports = buildReports(subtask, result)
        logger.info(
            "export ${reports.size} report of subtask[${subtask.id}] to " +
                "[${reportProperties.binderType}:${reportProperties.topic}]"
        )
        reports.forEach {
            messageSupplier.delegateToSupplier(
                data = it,
                topic = reportProperties.topic!!,
                binderType = BinderType.valueOf(reportProperties.binderType!!)
            )
        }
    }

    private fun shouldExport(subtask: TSubScanTask, result: ScanExecutorResult): Boolean {
        if (!reportProperties.enabled ||
            result !is StandardScanExecutorResult ||
            result.scanStatus != SubScanTaskStatus.SUCCESS.name) {
            return false
        }

        val projectWhiteList = reportProperties.projectWhiteList
        val projectsBlackList = reportProperties.projectBlackList
        val notInWhiteList = projectWhiteList.isNotEmpty() && subtask.projectId !in projectWhiteList
        val inBlackList = projectWhiteList.isEmpty()
            && projectsBlackList.isNotEmpty()
            && subtask.projectId in projectsBlackList
        val notInScannerWhiteList = reportProperties.scannerWhiteList.isNotEmpty()
            && subtask.scanner !in reportProperties.scannerWhiteList
        return !(notInWhiteList || inBlackList || notInScannerWhiteList)
    }

    /**
     * 构建待上报的报告列表，由于消息大小限制，需要对报告进行拆分
     */
    private fun buildReports(subtask: TSubScanTask, result: StandardScanExecutorResult): List<Report> {
        val reports = ArrayList<Report>()
        val fileNameExt = if (subtask.repoType == RepositoryType.GENERIC.name) {
            subtask.artifactName.substringAfterLast('.', "")
        } else {
            null
        }
        val baseReport = Report(
            taskId = subtask.id!!,
            projectId = subtask.projectId,
            artifactType = subtask.repoType,
            artifactName = subtask.artifactName,
            fileNameExt = fileNameExt,
            artifactVersion = subtask.version,
            artifactSize = subtask.packageSize,
            sha256 = subtask.sha256,
            scanner = subtask.scanner,
            startDateTime = subtask.startDateTime!!,
            finishedDateTime = LocalDateTime.now(),
            components = emptyList(),
            sensitiveContents = emptyList(),
        )
        result.output?.result?.securityResults
            ?.let { buildSecurityReports(baseReport, it) }
            ?.let { reports.addAll(it) }
        result.output?.result?.licenseResults
            ?.let { buildLicenseReports(baseReport, it) }
            ?.let { reports.addAll(it) }
        result.output?.result?.sensitiveResults
            ?.let { buildSensitiveReports(baseReport, it) }
            ?.let { reports.addAll(it) }

        return reports
    }

    private fun buildSecurityReports(baseReport: Report, results: List<SecurityResult>): List<Report> {
        val components = HashMap<String, Component>()
        for (result in results) {
            val componentName = result.pkgName ?: continue
            val component = components.getOrPut(componentName) { Component(componentName) }
            component.versions.addAll(result.pkgVersions)
            component.vulnerabilities.add(
                Vulnerability(result.vulId, result.cveId, result.cvss)
            )
        }
        return components.values.chunked(CHUNKED_SIZE).map { baseReport.copy(components = it) }
    }

    private fun buildLicenseReports(baseReport: Report, results: List<LicenseResult>): List<Report> {
        val components = HashMap<String, ComponentLicense>()
        for (result in results) {
            val componentName = result.pkgName ?: continue
            components.getOrPut(componentName) { ComponentLicense(componentName, licenseName = result.licenseName) }
        }
        return components.values.chunked(CHUNKED_SIZE).map {
            baseReport.copy(componentLicenses = it)
        }
    }

    private fun buildSensitiveReports(baseReport: Report, results: List<SensitiveResult>): List<Report> {
        val sensitiveContents = results.map { SensitiveContent(type = it.type, content = it.content) }
        return sensitiveContents.chunked(CHUNKED_SIZE).map { baseReport.copy(sensitiveContents = it) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReportExporter::class.java)

        /**
         * 分割大小
         */
        private const val CHUNKED_SIZE = 1000
    }
}
