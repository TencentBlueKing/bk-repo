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

package com.tencent.bkrepo.scanner.image

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
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
import com.tencent.bkrepo.scanner.executor.util.FileUtils
import org.apache.commons.io.input.ReversedLinesFileReader
import org.slf4j.LoggerFactory
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.standard.SpelExpressionParser
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ArrowheadScanExecutor(
    private val workDir: File = File(System.getProperty("java.io.tmpdir")),
    private val maxScannerLogLines: Int = 200
) : ScanExecutor {
    private val arrowheadConfigTemplate by lazy {
        val loader = ArrowheadScanExecutor::class.java.classLoader
        loader.getResourceAsStream(CONFIG_FILE_TEMPLATE)!!.use {
            it.reader().readText()
        }
    }
    private val taskIdProcessMap = ConcurrentHashMap<String, Process>()

    override fun scan(task: ScanExecutorTask): ScanExecutorResult {
        val scanner = task.scanner
        require(scanner is ArrowheadScanner)
        val taskWorkDir = createTaskWorkDir(scanner.rootPath, task.taskId)
        logger.info(logMsg(task, "create work dir success, $taskWorkDir"))
        try {
            // 加载待扫描文件，Arrowhead依赖文件名后缀判断文件类型进行解析，所以需要加上文件名后缀
            val fileExtension = task.fullPath.substringAfterLast(CharPool.DOT, "")
            val scannerInputFile = File(File(taskWorkDir, scanner.container.inputDir), "${task.sha256}.$fileExtension")
            scannerInputFile.parentFile.mkdirs()
            task.inputStream.use { taskInputStream ->
                scannerInputFile.outputStream().use { taskInputStream.copyTo(it) }
            }
            logger.info(logMsg(task, "read file success"))

            // 加载扫描配置文件
            loadConfigFile(task, taskWorkDir, scannerInputFile)
            logger.info(logMsg(task, "load config success"))

            // 执行扫描
            val scanStatus = doScan(taskWorkDir, task, scannerInputFile.length())
            return result(
                File(taskWorkDir, scanner.container.outputDir),
                scanStatus
            )
        } finally {
            // 清理工作目录
            if (scanner.cleanWorkDir) {
                FileUtils.deleteRecursively(taskWorkDir)
            }
        }
    }

    override fun stop(taskId: String): Boolean {
        return taskIdProcessMap[taskId]?.destroyForcibly()?.isAlive == false
    }

    private fun createTaskWorkDir(rootPath: String, taskId: String): File {
        // 创建工作目录
        val taskWorkDir = File(File(workDir, rootPath), taskId)
        if (!taskWorkDir.deleteRecursively() || !taskWorkDir.mkdirs()) {
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, taskWorkDir.absolutePath)
        }
        return taskWorkDir
    }

    /**
     * 加载扫描器配置文件
     *
     * @param scanTask 扫描任务
     * @param workDir 工作目录
     * @param scannerInputFile 待扫描文件
     *
     * @return 扫描器配置文件
     */
    private fun loadConfigFile(
        scanTask: ScanExecutorTask,
        workDir: File,
        scannerInputFile: File
    ): File {
        val scanner = scanTask.scanner
        require(scanner is ArrowheadScanner)
        val knowledgeBase = scanner.knowledgeBase
        val dockerImage = scanner.container
        val inputFilePath = dockerImage.inputDir.removePrefix(StringPool.SLASH) +
            "${StringPool.SLASH}${scannerInputFile.name}"
        val outputDir = dockerImage.outputDir.removePrefix(StringPool.SLASH)
        val params = mapOf(
            TEMPLATE_KEY_INPUT_FILE to inputFilePath,
            TEMPLATE_KEY_OUTPUT_DIR to outputDir,
            TEMPLATE_KEY_LOG_FILE to RESULT_FILE_NAME_LOG,
            TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_ID to knowledgeBase.secretId,
            TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_KEY to knowledgeBase.secretKey,
            TEMPLATE_KEY_KNOWLEDGE_BASE_ENDPOINT to knowledgeBase.endpoint
        )

        val content = SpelExpressionParser()
            .parseExpression(arrowheadConfigTemplate, TemplateParserContext())
            .getValue(params, String::class.java)!!

        val configFile = File(workDir, scanner.configFilePath)
        configFile.writeText(content)
        return configFile
    }

    /**
     * 创建容器执行扫描
     * @param workDir 工作目录,将挂载到容器中
     * @param task 扫描任务
     *
     * @return true 扫描成功， false 扫描失败
     */
    private fun doScan(workDir: File, task: ScanExecutorTask, fileSize: Long): SubScanTaskStatus {
        val scanner = task.scanner
        require(scanner is ArrowheadScanner)

        val maxScanDuration = scanner.maxScanDuration(fileSize)


        val process = Runtime.getRuntime().exec(arrayOf("/bin/bash", "-c", "./arrowhead"))
        try {
            taskIdProcessMap[task.taskId] = process
            logger.info(logMsg(task, "running arrowhead [$workDir]"))
            val result = process.waitFor(maxScanDuration, TimeUnit.MILLISECONDS)
            logger.info(logMsg(task, "arrowhead run result[$result], [$workDir]"))
            if (!result) {
                return scanStatus(task, workDir, SubScanTaskStatus.TIMEOUT)
            }
            return scanStatus(task, workDir)
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                logger.info("destroy arrowhead process, process is alive[${process.isAlive}]")
            }
            taskIdProcessMap.remove(task.taskId)
        }
    }

    private fun scanStatus(
        task: ScanExecutorTask,
        workDir: File,
        status: SubScanTaskStatus = SubScanTaskStatus.FAILED
    ): SubScanTaskStatus {
        val logFile = File(workDir, RESULT_FILE_NAME_LOG)
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

    private inline fun <reified T> readJsonString(file: File): T? {
        return if (file.exists()) {
            file.inputStream().use { it.readJsonString<T>() }
        } else {
            null
        }
    }

    private fun logMsg(task: ScanExecutorTask, msg: String) = with(task) {
        "$msg, parentTaskId[$parentTaskId], subTaskId[$taskId], sha256[$sha256], scanner[${scanner.name}]"
    }

    companion object {
        // arrowhead配置文件模板key
        private const val TEMPLATE_KEY_INPUT_FILE = "inputFile"
        private const val TEMPLATE_KEY_OUTPUT_DIR = "outputDir"
        private const val TEMPLATE_KEY_LOG_FILE = "logFile"
        private const val TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_ID = "knowledgeBaseSecretId"
        private const val TEMPLATE_KEY_KNOWLEDGE_BASE_SECRET_KEY = "knowledgeBaseSecretKey"
        private const val TEMPLATE_KEY_KNOWLEDGE_BASE_ENDPOINT = "knowledgeBaseEndpoint"

        /**
         * arrowhead输出日志路径
         */
        private const val RESULT_FILE_NAME_LOG = "sysauditor.log"

        /**
         * 证书扫描结果文件名
         */
        private const val RESULT_FILE_NAME_APPLICATION_ITEMS = "application_items.json"

        /**
         * CVE扫描结果文件名
         */
        private const val RESULT_FILE_NAME_CVE_SEC_ITEMS = "cvesec_items.json"

        /**
         * 扫描器配置文件路径
         */
        private const val CONFIG_FILE_TEMPLATE = "standalone.toml"
        private val logger = LoggerFactory.getLogger(ArrowheadScanExecutor::class.java)
    }
}
