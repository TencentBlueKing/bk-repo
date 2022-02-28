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

package com.tencent.bkrepo.scanner.executor.binauditor

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.WaitContainerResultCallback
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.tencent.bkrepo.common.api.constant.StringPool.SLASH
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.ApplicationItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.BinAuditorScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.BinAuditorScanExecutorResult.Companion.overviewKeyOfCve
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.BinAuditorScanExecutorResult.Companion.overviewKeyOfLicenseRisk
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.BinAuditorScanExecutorResult.Companion.overviewKeyOfSensitive
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.BinAuditorScanner
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.CheckSecItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.CveSecItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.ResultFilterRule
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.SensitiveItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.utils.normalizedLevel
import com.tencent.bkrepo.scanner.executor.ScanExecutor
import com.tencent.bkrepo.scanner.executor.configuration.DockerProperties.Companion.SCANNER_EXECUTOR_DOCKER_ENABLED
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.Resource
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStream
import kotlin.reflect.full.memberProperties

@Component(BinAuditorScanner.TYPE)
@ConditionalOnProperty(SCANNER_EXECUTOR_DOCKER_ENABLED, matchIfMissing = true)
class BinAuditorScanExecutor @Autowired constructor(
    private val dockerClient: DockerClient
) : ScanExecutor<BinAuditorScanner> {

    @Value(CONFIG_FILE_TEMPLATE_CLASS_PATH)
    private lateinit var binAuditorConfigTemplate: Resource

    override fun scan(
        task: ScanExecutorTask<BinAuditorScanner>,
        callback: (ScanExecutorResult) -> Unit
    ) {
        val startTimestamp = System.currentTimeMillis()
        logger.info(logMsg(task, "start to scan"))
        val scanner = task.scanner
        // 创建工作目录
        val workDir = createWorkDir(scanner.rootPath, task.taskId)
        logger.info(logMsg(task, "create work dir success, $workDir"))
        try {
            // 加载待扫描文件
            val scannerInputFilePath = "${scanner.container.inputDir}$SLASH${task.sha256}"
            val scannerInputFile = loadFile(workDir, scannerInputFilePath, task.inputStream)
            logger.info(logMsg(task, "load file success"))

            // 加载扫描配置文件
            loadConfigFile(task, workDir, scannerInputFile)
            logger.info(logMsg(task, "load config success"))

            // 执行扫描
            doScan(workDir, task)
            val finishedTimestamp = System.currentTimeMillis()
            val timeSpent = finishedTimestamp - startTimestamp
            logger.info(logMsg(task, "scan finished took time $timeSpent ms"))
            var result = result(startTimestamp, finishedTimestamp, File(workDir, scanner.container.outputDir))
            result = scanner.resultFilterRule?.let { filter(it, result) } ?: result
            callback(result)
        } catch (e: Exception) {
            logger.error(logMsg(task, "scan failed"), e)
            throw e
        } finally {
            // 清理工作目录
            if (task.scanner.cleanWorkDir) {
                workDir.deleteRecursively()
            }
        }
    }

    /**
     * 创建工作目录
     *
     * @param rootPath 扫描器根目录
     * @param taskId 任务id
     *
     * @return 工作目录
     */
    private fun createWorkDir(rootPath: String, taskId: String): File {
        // 创建工作目录
        val workDir = File(rootPath, taskId)
        if (!workDir.deleteRecursively() || !workDir.mkdirs()) {
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, workDir.absolutePath)
        }
        return workDir
    }

    /**
     * 加载待扫描的文件
     *
     * @param workDir 工作目录
     * @param filePath 加载待扫描文件后的存储路径
     * @param inputStream 待扫描文件输入流
     *
     * @return 待扫描文件
     */
    private fun loadFile(workDir: File, filePath: String, inputStream: InputStream): File {
        val scannerInputFile = File(workDir, filePath)
        FileUtils.copyInputStreamToFile(inputStream, scannerInputFile)
        return scannerInputFile
    }

    /**
     * 加载BinAuditor扫描器配置文件
     *
     * @param scanTask 扫描任务
     * @param workDir 工作目录
     * @param scannerInputFile 待扫描文件
     *
     * @return BinAuditor扫描器配置文件
     */
    private fun loadConfigFile(
        scanTask: ScanExecutorTask<BinAuditorScanner>,
        workDir: File,
        scannerInputFile: File
    ): File {
        val scanner = scanTask.scanner
        val nvTools = scanner.nvTools
        val dockerImage = scanner.container
        val template = binAuditorConfigTemplate.file.readText()
        val inputFilePath = "${dockerImage.inputDir.removePrefix(SLASH)}$SLASH${scannerInputFile.name}"
        val outputDir = dockerImage.outputDir.removePrefix(SLASH)
        val params = mapOf(
            TEMPLATE_KEY_INPUT_FILE to inputFilePath,
            TEMPLATE_KEY_OUTPUT_DIR to outputDir,
            TEMPLATE_KEY_NV_TOOLS_ENABLED to nvTools.enabled,
            TEMPLATE_KEY_NV_TOOLS_USERNAME to nvTools.username,
            TEMPLATE_KEY_NV_TOOLS_KEY to nvTools.key,
            TEMPLATE_KEY_NV_TOOLS_HOST to nvTools.host
        )

        val content = SpelExpressionParser()
            .parseExpression(template, TemplateParserContext())
            .getValue(params, String::class.java)!!

        val configFile = File(workDir, scanner.configFilePath)
        configFile.writeText(content)
        return configFile
    }

    private fun doScan(workDir: File, task: ScanExecutorTask<BinAuditorScanner>) {
        val containerConfig = task.scanner.container

        val bind = Volume(containerConfig.workDir)
        val binds = Binds(Bind(workDir.absolutePath, bind))
        val containerId = dockerClient.createContainerCmd(containerConfig.image)
            .withHostConfig(HostConfig().withBinds(binds))
            .withCmd(containerConfig.args)
            .withTty(true)
            .withStdinOpen(true)
            .exec().id
        logger.info(logMsg(task, "run container instance Id [$workDir, $containerId]"))
        try {
            dockerClient.startContainerCmd(containerId).exec()
            val resultCallback = WaitContainerResultCallback()
            dockerClient.waitContainerCmd(containerId).exec(resultCallback)
            resultCallback.awaitCompletion()
            logger.info(logMsg(task, "task docker run success [$workDir, $containerId]"))
        } finally {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec()
        }
    }

    /**
     * 解析扫描结果
     */
    private fun result(
        startTimestamp: Long,
        finishedTimestap: Long,
        outputDir: File
    ): BinAuditorScanExecutorResult {
        val cveSecResultFile = File(outputDir, RESULT_FILE_NAME_CVE_SEC_ITEMS)
        val cveSecItems = readJsonString<List<Map<String, Any?>>>(cveSecResultFile)
            ?.map { CveSecItem.parseCveSecItems(it) }
            ?: emptyList()

        val checkSecItems =
            readJsonString<List<CheckSecItem>>(File(outputDir, RESULT_FILE_NAME_CHECK_SEC_ITEMS)) ?: emptyList()

        val applicationItems =
            readJsonString<List<ApplicationItem>>(File(outputDir, RESULT_FILE_NAME_APPLICATION_ITEMS))
                ?.map { it.copy(licenseRisk = normalizedLevel(it.licenseRisk)) }
                ?: emptyList()

        val sensitiveItems =
            readJsonString<List<SensitiveItem>>(File(outputDir, RESULT_FILE_NAME_SENSITIVE_INFO_ITEMS)) ?: emptyList()

        return BinAuditorScanExecutorResult(
            startTimestamp = startTimestamp,
            finishedTimestamp = finishedTimestap,
            overview = overview(applicationItems, sensitiveItems, cveSecItems),
            checkSecItems = checkSecItems,
            applicationItems = applicationItems,
            sensitiveItems = sensitiveItems,
            cveSecItems = cveSecItems
        )
    }

    private fun filter(
        resultFilerRule: ResultFilterRule,
        binAuditorScanExecutorResult: BinAuditorScanExecutorResult
    ): BinAuditorScanExecutorResult {
        val excludeSensitiveItemRule = resultFilerRule.sensitiveItemFilterRule.excludes
        val sensitiveItems = binAuditorScanExecutorResult.sensitiveItems.filter { sensitiveItem ->
            !SensitiveItem::class.memberProperties.any { excludeSensitiveItemRule[it.name] == it.get(sensitiveItem) }
        }
        return binAuditorScanExecutorResult.copy(sensitiveItems = sensitiveItems)
    }

    private fun overview(
        applicationItems: List<ApplicationItem>,
        sensitiveItems: List<SensitiveItem>,
        cveSecItems: List<CveSecItem>
    ): Map<String, Any?> {
        val overview = HashMap<String, Long>()

        // license risk
        applicationItems.forEach {
            val overviewKey = overviewKeyOfLicenseRisk(it.licenseRisk)
            overview[overviewKey] = overview.getOrDefault(overviewKey, 0L) + 1L
        }

        // sensitive count
        sensitiveItems.forEach {
            val overviewKey = overviewKeyOfSensitive(it.type)
            overview[overviewKey] = overview.getOrDefault(overviewKey, 0L) + 1L
        }

        // cve count
        cveSecItems.forEach {
            val overviewKey = overviewKeyOfCve(it.level ?: it.cvssRank)
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

    private fun logMsg(task: ScanExecutorTask<BinAuditorScanner>, msg: String) = with(task) {
        "$msg, parentTaskId[$parentTaskId], subTaskId[$taskId], sha256[$sha256], scanner[${scanner.name}]]"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BinAuditorScanExecutor::class.java)

        /**
         * standalone模式 taskId占位符
         */
        private const val CONFIG_FILE_TEMPLATE_CLASS_PATH = "classpath:standalone.toml"

        // BinAuditor配置文件模板key
        private const val TEMPLATE_KEY_INPUT_FILE = "inputFile"
        private const val TEMPLATE_KEY_OUTPUT_DIR = "outputDir"
        private const val TEMPLATE_KEY_NV_TOOLS_ENABLED = "nvToolsEnabled"
        private const val TEMPLATE_KEY_NV_TOOLS_USERNAME = "nvToolsUsername"
        private const val TEMPLATE_KEY_NV_TOOLS_KEY = "nvToolsKey"
        private const val TEMPLATE_KEY_NV_TOOLS_HOST = "nvToolsHost"

        // BinAuditor扫描结果文件名
        /**
         * 证书扫描结果文件名
         */
        private const val RESULT_FILE_NAME_APPLICATION_ITEMS = "application_items.json"

        /**
         * 安全审计结果文件名
         */
        private const val RESULT_FILE_NAME_CHECK_SEC_ITEMS = "checksec_items.json"

        /**
         * CVE扫描结果文件名
         */
        private const val RESULT_FILE_NAME_CVE_SEC_ITEMS = "cvesec_items.json"

        /**
         * 敏感信息扫描结果文件名
         */
        private const val RESULT_FILE_NAME_SENSITIVE_INFO_ITEMS = "sensitive_info_items.json"
    }
}
