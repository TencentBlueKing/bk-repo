package com.tencent.bkrepo.analyst.component

import com.tencent.bkrepo.analyst.configuration.ReportExportProperties
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.report.Component
import com.tencent.bkrepo.analyst.pojo.report.Report
import com.tencent.bkrepo.analyst.pojo.report.Vulnerability
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.SecurityResult
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

        result.output?.result?.securityResults?.let {
            logger.info(
                "export subtask[${subtask.id}] report to [${reportProperties.binderType}:${reportProperties.topic}]"
            )
            messageSupplier.delegateToSupplier(
                data = buildReport(subtask, it),
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

    private fun buildReport(subtask: TSubScanTask, securityResults: List<SecurityResult>): Report {
        val components = HashMap<String, Component>()
        for (securityResult in securityResults) {
            val componentName = securityResult.pkgName ?: continue
            val component = components.getOrPut(componentName) { Component(componentName) }
            component.versions.addAll(securityResult.pkgVersions)
            component.vulnerabilities.add(
                Vulnerability(securityResult.vulId, securityResult.cveId, securityResult.cvss)
            )
        }
        val fileNameExt = if (subtask.repoType == RepositoryType.GENERIC.name) {
            subtask.artifactName.substringAfterLast('.', "")
        } else {
            null
        }

        return Report(
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
            components = components.values.toList()
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReportExporter::class.java)
    }
}
