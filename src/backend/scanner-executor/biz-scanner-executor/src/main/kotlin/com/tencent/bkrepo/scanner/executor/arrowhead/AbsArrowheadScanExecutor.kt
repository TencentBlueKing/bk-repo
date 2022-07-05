/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.scanner.executor.arrowhead

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.scanner.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.ApplicationItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.ArrowheadScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.ArrowheadScanner
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.CheckSecItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.CveSecItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.SensitiveItem
import com.tencent.bkrepo.scanner.executor.ScanExecutor
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.scanner.executor.util.CommonUtils.logMsg
import com.tencent.bkrepo.scanner.executor.util.CommonUtils.readJsonString
import com.tencent.bkrepo.scanner.executor.util.FileUtils
import org.apache.commons.io.input.ReversedLinesFileReader
import org.slf4j.LoggerFactory
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.standard.SpelExpressionParser
import java.io.File

abstract class AbsArrowheadScanExecutor : ScanExecutor {

    override fun scan(task: ScanExecutorTask): ScanExecutorResult {
        require(task.scanner is ArrowheadScanner)
        val scanner = task.scanner
        // 创建工作目录
        val taskWorkDir = createTaskWorkDir(scanner.rootPath, task.taskId)
        logger.info(logMsg(task, "create work dir success, $taskWorkDir"))
        try {
            // 加载待扫描文件
            val scannerInputFile = loadFile(taskWorkDir, task)
            // 加载扫描配置文件
            val configFile = loadConfigFile(task, taskWorkDir, scannerInputFile)
            // 执行扫描
            val scanStatus = doScan(taskWorkDir, configFile, task, scannerInputFile.length())
            return result(File(taskWorkDir, scanner.container.outputDir), scanStatus)
        } finally {
            // 清理工作目录
            if (task.scanner.cleanWorkDir) {
                FileUtils.deleteRecursively(taskWorkDir)
            }
        }
    }

    protected abstract fun workDir(): File

    protected abstract fun configTemplate(): String

    /**
     * 创建容器执行扫描
     * @param taskWorkDir 工作目录,将挂载到容器中
     * @param configFile arrowhead扫描配置文件
     * @param task 扫描任务
     * @param fileSize 文件大小
     *
     * @return true 扫描成功， false 扫描失败
     */
    protected abstract fun doScan(
        taskWorkDir: File,
        configFile: File,
        task: ScanExecutorTask,
        fileSize: Long
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
            logger.info(logMsg(task, "arrowhead log file not exists"))
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

            logger.info(logMsg(task, "scan failed: ${arrowheadLog.asReversed().joinToString("\n")}"))
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
        scannerInputFile: File
    ): File {
        require(scanTask.scanner is ArrowheadScanner)
        val scanner = scanTask.scanner
        val knowledgeBase = scanner.knowledgeBase
        val containerConfig = scanner.container
        val template = configTemplate()
        val inputFilePath = "${containerConfig.inputDir.removePrefix(StringPool.SLASH)}/${scannerInputFile.name}"
        val outputDir = containerConfig.outputDir.removePrefix(StringPool.SLASH)
        val params = mapOf(
            TEMPLATE_KEY_INPUT_FILE to inputFilePath,
            TEMPLATE_KEY_OUTPUT_DIR to outputDir,
            TEMPLATE_KEY_LOG_FILE to RESULT_FILE_NAME_LOG,
            TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_ID to knowledgeBase.secretId,
            TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_KEY to knowledgeBase.secretKey,
            TEMPLATE_KEY_KNOWLEDGE_BASE_ENDPOINT to knowledgeBase.endpoint
        )

        val content = SpelExpressionParser()
            .parseExpression(template, TemplateParserContext())
            .getValue(params, String::class.java)!!

        val configFile = File(taskWorkDir, scanner.configFilePath)
        configFile.writeText(content)
        logger.info(logMsg(scanTask, "load config success"))
        return configFile
    }

    /**
     * 解析扫描结果
     */
    private fun result(
        outputDir: File,
        scanStatus: SubScanTaskStatus
    ): ArrowheadScanExecutorResult {

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
                ?.map { ApplicationItem.normalize(it) }
                ?: emptyList()

        val checkSecItems = emptyList<CheckSecItem>()
        val sensitiveItems = emptyList<SensitiveItem>()

        return ArrowheadScanExecutorResult(
            scanStatus = scanStatus.name,
            overview = overview(applicationItems, sensitiveItems, cveSecItems),
            checkSecItems = checkSecItems,
            applicationItems = applicationItems,
            sensitiveItems = sensitiveItems,
            cveSecItems = cveSecItems
        )
    }

    private fun overview(
        applicationItems: List<ApplicationItem>,
        sensitiveItems: List<SensitiveItem>,
        cveSecItems: List<CveSecItem>
    ): Map<String, Any?> {
        val overview = HashMap<String, Long>()

        // license risk
        applicationItems.forEach {
            it.license?.let { license ->
                val overviewKey = ArrowheadScanExecutorResult.overviewKeyOfLicenseRisk(license.risk)
                overview[overviewKey] = overview.getOrDefault(overviewKey, 0L) + 1L
            }
        }

        // sensitive count
        sensitiveItems.forEach {
            val overviewKey = ArrowheadScanExecutorResult.overviewKeyOfSensitive(it.type)
            overview[overviewKey] = overview.getOrDefault(overviewKey, 0L) + 1L
        }

        // cve count
        cveSecItems.forEach {
            val overviewKey = CveOverviewKey.overviewKeyOf(it.cvssRank)
            overview[overviewKey] = overview.getOrDefault(overviewKey, 0L) + 1L
        }

        return overview
    }

    private fun loadFile(taskWorkDir: File, task: ScanExecutorTask): File {
        val scanner = task.scanner as ArrowheadScanner
        // 加载待扫描文件，Arrowhead依赖文件名后缀判断文件类型进行解析，所以需要加上文件名后缀
        val fileExtension = task.fullPath.substringAfterLast(CharPool.DOT, "")
        val scannerInputFile = File(File(taskWorkDir, scanner.container.inputDir), "${task.sha256}.$fileExtension")
        scannerInputFile.parentFile.mkdirs()
        task.inputStream.use { taskInputStream ->
            scannerInputFile.outputStream().use { taskInputStream.copyTo(it) }
        }
        logger.info(logMsg(task, "read file success"))
        return scannerInputFile
    }

    /**
     * 创建工作目录
     *
     * @param rootPath 扫描器根目录
     * @param taskId 任务id
     *
     * @return 工作目录
     */
    private fun createTaskWorkDir(rootPath: String, taskId: String): File {
        // 创建工作目录
        val taskWorkDir = File(File(workDir(), rootPath), taskId)
        if (!taskWorkDir.deleteRecursively() || !taskWorkDir.mkdirs()) {
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, taskWorkDir.absolutePath)
        }
        return taskWorkDir
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbsArrowheadScanExecutor::class.java)

        // arrowhead配置文件模板key
        protected const val TEMPLATE_KEY_INPUT_FILE = "inputFile"
        protected const val TEMPLATE_KEY_OUTPUT_DIR = "outputDir"
        protected const val TEMPLATE_KEY_LOG_FILE = "logFile"
        protected const val TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_ID = "knowledgeBaseSecretId"
        protected const val TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_KEY = "knowledgeBaseSecretKey"
        protected const val TEMPLATE_KEY_KNOWLEDGE_BASE_ENDPOINT = "knowledgeBaseEndpoint"

        // arrowhead输出日志路径
        private const val RESULT_FILE_NAME_LOG = "sysauditor.log"

        // arrowhead扫描结果文件名
        /**
         * 证书扫描结果文件名
         */
        protected const val RESULT_FILE_NAME_APPLICATION_ITEMS = "application_items.json"

        /**
         * CVE扫描结果文件名
         */
        protected const val RESULT_FILE_NAME_CVE_SEC_ITEMS = "cvesec_items.json"
    }
}
