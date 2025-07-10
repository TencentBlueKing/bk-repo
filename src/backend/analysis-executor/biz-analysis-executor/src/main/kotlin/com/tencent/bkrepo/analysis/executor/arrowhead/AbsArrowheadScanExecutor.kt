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

package com.tencent.bkrepo.analysis.executor.arrowhead

import com.tencent.bkrepo.analysis.executor.CommonScanExecutor
import com.tencent.bkrepo.analysis.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.analysis.executor.util.CommonUtils.buildLogMsg
import com.tencent.bkrepo.analysis.executor.util.CommonUtils.readJsonString
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.ApplicationItem
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.ArrowheadScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.ArrowheadScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.CheckSecItem
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.CveSecItem
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.SensitiveItem
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import org.apache.commons.io.input.ReversedLinesFileReader
import org.slf4j.LoggerFactory
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.standard.SpelExpressionParser
import java.io.File

abstract class AbsArrowheadScanExecutor : CommonScanExecutor() {

    override fun doScan(
        taskWorkDir: File,
        scannerInputFile: File,
        sha256: String,
        task: ScanExecutorTask
    ): SubScanTaskStatus {
        val analysisSubType = if (task.repoType == RepositoryType.DOCKER.name) {
            ANALYSIS_SUB_TYPE_DOCKER
        } else {
            ANALYSIS_SUB_TYPE_BINARY_PACKAGE
        }
        // 加载扫描配置文件
        val configFile = loadConfigFile(task, taskWorkDir, scannerInputFile, analysisSubType)

        return doScan(taskWorkDir, scannerInputFile, configFile, task)
    }

    override fun scannerInputFile(taskWorkDir: File, task: ScanExecutorTask): File {
        val scanner = task.scanner
        require(scanner is ArrowheadScanner)
        return File(File(taskWorkDir, scanner.container.inputDir), task.file.name)
    }

    protected abstract fun configTemplate(): String

    /**
     * 执行扫描
     * @param taskWorkDir 工作目录,将挂载到容器中
     * @param scannerInputFile 待扫描文件
     * @param configFile arrowhead扫描配置文件
     * @param task 扫描任务
     *
     * @return 扫描结果
     */
    protected abstract fun doScan(
        taskWorkDir: File,
        scannerInputFile: File,
        configFile: File,
        task: ScanExecutorTask
    ): SubScanTaskStatus

    /**
     * 解析arrowhead输出日志，判断扫描结果
     */
    protected fun scanStatus(
        task: ScanExecutorTask,
        taskWorkDir: File,
        status: SubScanTaskStatus = SubScanTaskStatus.FAILED,
        maxScannerLogLines: Int = 200
    ): SubScanTaskStatus {
        val logFile = File(taskWorkDir, RESULT_FILE_NAME_LOG)
        if (!logFile.exists()) {
            logger.info(buildLogMsg(task, "arrowhead log file not exists"))
            return status
        }

        ReversedLinesFileReader(logFile, Charsets.UTF_8).use {
            var line: String? = it.readLine() ?: return status
            if (line!!.trimEnd().endsWith("Done")) {
                return SubScanTaskStatus.SUCCESS
            }

            val arrowheadLog = ArrayList<String>()
            var count = 1
            while (count < maxScannerLogLines && line != null) {
                line = it.readLine()?.apply {
                    arrowheadLog.add(this)
                    count++
                }
            }

            logger.info(buildLogMsg(task, "scan failed: ${arrowheadLog.asReversed().joinToString("\n")}"))
        }

        return status
    }

    /**
     * 加载扫描器配置文件
     *
     * @param scanTask 扫描任务
     * @param taskWorkDir 工作目录
     * @param scannerInputFile 待扫描文件
     *
     * @return 扫描器配置文件
     */
    private fun loadConfigFile(
        scanTask: ScanExecutorTask,
        taskWorkDir: File,
        scannerInputFile: File,
        analysisSubType: String
    ): File {
        require(scanTask.scanner is ArrowheadScanner)
        val scanner = scanTask.scanner
        val knowledgeBase = scanner.knowledgeBase
        val containerConfig = scanner.container
        val template = configTemplate()
        val inputFilePath = "${containerConfig.inputDir.removePrefix(StringPool.SLASH)}/${scannerInputFile.name}"
        val outputDir = containerConfig.outputDir.removePrefix(StringPool.SLASH)
        val params = mutableMapOf(
            TEMPLATE_KEY_ANALYSIS_SUB_TYPE to analysisSubType,
            TEMPLATE_KEY_OUTPUT_DIR to outputDir,
            TEMPLATE_KEY_LOG_FILE to RESULT_FILE_NAME_LOG,
            TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_ID to knowledgeBase.secretId,
            TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_KEY to knowledgeBase.secretKey,
            TEMPLATE_KEY_KNOWLEDGE_BASE_ENDPOINT to knowledgeBase.endpoint
        )

        if (analysisSubType == ANALYSIS_SUB_TYPE_BINARY_PACKAGE) {
            params[TEMPLATE_KEY_INPUT_FILE] = inputFilePath
        } else {
            params[TEMPLATE_KEY_DOCKER_INPUT_FILE] = inputFilePath
        }

        val content = SpelExpressionParser()
            .parseExpression(template, TemplateParserContext())
            .getValue(params, String::class.java)!!

        val configFile = File(taskWorkDir, scanner.configFilePath)
        configFile.writeText(content)
        logger.info(buildLogMsg(scanTask, "load config success"))
        return configFile
    }

    override fun result(taskWorkDir: File, task: ScanExecutorTask, scanStatus: SubScanTaskStatus): ScanExecutorResult {
        val scanner = task.scanner
        require(scanner is ArrowheadScanner)
        val outputDir = File(taskWorkDir, scanner.container.outputDir)
        val cveMap = HashMap<String, CveSecItem>()
        readJsonString<List<CveSecItem>>(File(outputDir, RESULT_FILE_NAME_CVE_SEC_ITEMS))
            ?.forEach {
                // 按（组件-POC_ID）对漏洞去重
                // POC_ID为arrowhead使用的漏洞库内部漏洞编号，与CVE_ID、CNNVD_ID、CNVD_ID一一对应
                val cveSecItem = cveMap.getOrPut("${it.component}-${it.pocId}") { CveSecItem.normalize(it) }
                cveSecItem.versions.add(cveSecItem.version)
            }
        val cveSecItems = cveMap.values.toList()

        val applicationItems =
            readJsonString<List<ApplicationItem>>(File(outputDir, RESULT_FILE_NAME_APPLICATION_ITEMS))
                ?.distinct()
                ?.map { ApplicationItem.normalize(it) }
                ?: emptyList()

        val checkSecItems = emptyList<CheckSecItem>()
        val sensitiveItems = emptyList<SensitiveItem>()

        return ArrowheadScanExecutorResult(
            scanStatus = scanStatus.name,
            checkSecItems = checkSecItems,
            applicationItems = applicationItems,
            sensitiveItems = sensitiveItems,
            cveSecItems = cveSecItems
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbsArrowheadScanExecutor::class.java)

        // arrowhead配置文件模板key
        private const val TEMPLATE_KEY_ANALYSIS_SUB_TYPE = "analysisSubType"
        private const val TEMPLATE_KEY_INPUT_FILE = "inputFile"
        private const val TEMPLATE_KEY_DOCKER_INPUT_FILE = "dockerInputFile"
        private const val TEMPLATE_KEY_OUTPUT_DIR = "outputDir"
        private const val TEMPLATE_KEY_LOG_FILE = "logFile"
        private const val TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_ID = "knowledgeBaseSecretId"
        private const val TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_KEY = "knowledgeBaseSecretKey"
        private const val TEMPLATE_KEY_KNOWLEDGE_BASE_ENDPOINT = "knowledgeBaseEndpoint"

        // arrowhead输出日志路径
        private const val RESULT_FILE_NAME_LOG = "sysauditor.log"

        // arrowhead扫描结果文件名
        /**
         * 证书扫描结果文件名
         */
        private const val RESULT_FILE_NAME_APPLICATION_ITEMS = "application_items.json"

        /**
         * CVE扫描结果文件名
         */
        private const val RESULT_FILE_NAME_CVE_SEC_ITEMS = "cvesec_items.json"
        const val ANALYSIS_SUB_TYPE_BINARY_PACKAGE = "BinaryPackage"
        const val ANALYSIS_SUB_TYPE_DOCKER = "Docker"
    }
}
