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

package com.tencent.bkrepo.scanner.executor.scancodeCheck

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.Volume
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseOverviewKey
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScanCodeToolkitScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScancodeItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScancodeToolItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.scanner.ScancodeToolkitScanner
import com.tencent.bkrepo.scanner.api.ScanClient
import com.tencent.bkrepo.scanner.executor.CommonScanExecutor
import com.tencent.bkrepo.scanner.executor.configuration.DockerProperties
import com.tencent.bkrepo.scanner.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.scanner.executor.util.CommonUtils.logMsg
import com.tencent.bkrepo.scanner.executor.util.CommonUtils.readJsonString
import com.tencent.bkrepo.scanner.executor.util.DockerScanHelper
import com.tencent.bkrepo.scanner.executor.util.FileUtils
import org.apache.commons.io.input.ReversedLinesFileReader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.Resource
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.stereotype.Component
import java.io.File

@Component(ScancodeToolkitScanner.TYPE)
@ConditionalOnProperty(DockerProperties.SCANNER_EXECUTOR_DOCKER_ENABLED, matchIfMissing = true)
class ScancodeToolkitExecutor @Autowired constructor(
    private val dockerClient: DockerClient,
    private val scanClient: ScanClient,
    private val scannerExecutorProperties: ScannerExecutorProperties
) : CommonScanExecutor() {

    @Value(BASH_FILE_TEMPLATE_CLASS_PATH)
    private lateinit var scanToolBashTemplate: Resource
    private val bashTemplate by lazy { scanToolBashTemplate.inputStream.use { it.reader().readText() } }

    private val dockerScanHelper = DockerScanHelper(scannerExecutorProperties, dockerClient)

    override fun doScan(taskWorkDir: File, scannerInputFile: File, task: ScanExecutorTask): SubScanTaskStatus {
        require(task.scanner is ScancodeToolkitScanner)
        val containerConfig = task.scanner.container
        File(taskWorkDir, task.scanner.container.outputDir).mkdirs()

        // 加载扫描脚本
        loadScanBashFile(task, taskWorkDir, scannerInputFile)

        // 执行扫描
        val containerCmd =
            DOCKER_BASH + listOf(BASH_CMD.format(containerConfig.workDir, BASH, BASH_FILE, RESULT_FILE_NAME_LOG))
        val result = dockerScanHelper.scan(
            image = containerConfig.image,
            binds = Binds(Bind(taskWorkDir.absolutePath, Volume(containerConfig.workDir))),
            args = containerCmd,
            taskWorkDir = taskWorkDir,
            scannerInputFile = scannerInputFile,
            task = task
        )
        if (!result) {
            return scanStatus(task, taskWorkDir, SubScanTaskStatus.TIMEOUT)
        }
        return scanStatus(task, taskWorkDir)
    }

    override fun stop(taskId: String): Boolean {
        return dockerScanHelper.stop(taskId)
    }

    override fun workDir() = File(scannerExecutorProperties.workDir)

    override fun scannerInputFile(taskWorkDir: File, task: ScanExecutorTask): File {
        val scanner = task.scanner
        require(scanner is ScancodeToolkitScanner)
        val fileName = FileUtils.sha256NameWithExt(task.fullPath, task.sha256)
        return File(File(taskWorkDir, scanner.container.inputDir), fileName)
    }

    /**
     * 解析扫描结果
     */
    override fun result(
        taskWorkDir: File,
        task: ScanExecutorTask,
        scanStatus: SubScanTaskStatus
    ): ScanCodeToolkitScanExecutorResult {
        val scanner = task.scanner
        require(scanner is ScancodeToolkitScanner)

        val inputFile = scannerInputFile(taskWorkDir, task)
        val resultFile = File(File(taskWorkDir, scanner.container.outputDir), LICENSE_SCAN_RESULT_FILE_NAME)

        val scancodeToolItem = readJsonString<ScancodeToolItem>(resultFile)
            ?: return ScanCodeToolkitScanExecutorResult(scanStatus.name, emptyMap(), emptySet())

        val scancodeItems = HashSet<ScancodeItem>()
        scancodeToolItem.files.forEach { file ->
            file.licenses.forEach { license ->
                val path = file.path.removePrefix("${inputFile.name}$EXT_SUFFIX")
                scancodeItems.add(ScancodeItem(license.spdxLicenseKey, path))
            }
        }

        return ScanCodeToolkitScanExecutorResult(
            overview = updateRiskAndOverview(scancodeItems),
            scanStatus = scanStatus.name,
            scancodeItem = scancodeItems
        )
    }

    /**
     * 加载扫描脚本
     *
     * @param scanTask 扫描任务
     * @param taskWorkDir 工作目录
     * @param scannerInputFile 待扫描文件
     *
     * @return 扫描脚本
     */
    private fun loadScanBashFile(
        scanTask: ScanExecutorTask,
        taskWorkDir: File,
        scannerInputFile: File
    ): File {
        require(scanTask.scanner is ScancodeToolkitScanner)
        val scanner = scanTask.scanner
        val dockerImage = scanner.container
        val inputFilePath = "${dockerImage.workDir}/${dockerImage.inputDir}/${scannerInputFile.name}"
        val outputFilePath = "${dockerImage.workDir}/${dockerImage.outputDir}/$LICENSE_SCAN_RESULT_FILE_NAME"
        val params = mapOf(
            TEMPLATE_KEY_INPUT_FILE to inputFilePath,
            TEMPLATE_KEY_RESULT_FILE to outputFilePath
        )
        val content = SpelExpressionParser()
            .parseExpression(bashTemplate, TemplateParserContext())
            .getValue(params, String::class.java)!!
        val bashFile = File(taskWorkDir, DEFAULT_CONFIG_BASH_PATH)
        bashFile.writeText(content)
        logger.info(logMsg(scanTask, "load scan bash success"))
        return bashFile
    }

    /**
     * 判断扫描状态
     */
    private fun scanStatus(
        task: ScanExecutorTask,
        workDir: File,
        status: SubScanTaskStatus = SubScanTaskStatus.FAILED
    ): SubScanTaskStatus {
        require(task.scanner is ScancodeToolkitScanner)
        val resultFile = File(File(workDir, task.scanner.container.outputDir), LICENSE_SCAN_RESULT_FILE_NAME)
        if (resultFile.exists()) {
            logger.info(logMsg(task, "scancode_toolkit result file exists"))
            return SubScanTaskStatus.SUCCESS
        }

        val logFile = File(workDir, RESULT_FILE_NAME_LOG)
        if (!logFile.exists()) {
            logger.info(logMsg(task, "scancode_toolkit log file not exists"))
            return status
        }
        ReversedLinesFileReader(logFile, Charsets.UTF_8).use {
            var line: String? = it.readLine() ?: return status
            val logs = ArrayList<String>()
            var count = 1
            while (count < scannerExecutorProperties.maxScannerLogLines && line != null) {
                line = it.readLine()?.apply {
                    logs.add(this)
                    count++
                }
            }

            logger.info(logMsg(task, "scan failed: ${logs.asReversed().joinToString("\n")}"))
        }
        return status
    }

    /**
     * 数量统计
     */
    private fun updateRiskAndOverview(scancodeItems: Set<ScancodeItem>): Map<String, Any?> {
        val overview = HashMap<String, Long>()
        // 不推荐和不合规可能重合，单独统计总数
        overview[LicenseOverviewKey.overviewKeyOf(TOTAL)] = scancodeItems.size.toLong()

        // 获取许可证详情信息
        val licenseIds = scancodeItems.mapTo(HashSet()) { it.licenseId }.toList()
        val licensesInfo = scanClient.licenseInfoByIds(licenseIds).data!!

        // 统计各类型许可证数量
        for (scancodeItem in scancodeItems) {
            val detail = licensesInfo[scancodeItem.licenseId]
            if (detail == null) {
                incOverview(overview, LicenseNature.UNKNOWN.natureName)
                continue
            }

            // license risk
            scancodeItem.riskLevel = detail.risk
            scancodeItem.riskLevel?.let { incOverview(overview, it) }

            // nature count
            if (detail.isDeprecatedLicenseId) {
                incOverview(overview, LicenseNature.UN_COMPLIANCE.natureName)
            }

            if (!detail.isTrust) {
                incOverview(overview, LicenseNature.UN_RECOMMEND.natureName)
            }
        }
        return overview
    }

    private fun incOverview(overview: MutableMap<String, Long>, level: String) {
        val overviewKey = LicenseOverviewKey.overviewKeyOf(level)
        overview[overviewKey] = overview.getOrDefault(overviewKey, 0L) + 1L
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScancodeToolkitExecutor::class.java)

        /**
         * 扫描器配置文件路径
         */
        private const val BASH_FILE_TEMPLATE_CLASS_PATH = "classpath:toolScan.sh"

        private const val LICENSE_SCAN_RESULT_FILE_NAME = "result.json"

        /**
         * scancode_toolkit工具相关命令
         **/
        private val DOCKER_BASH = arrayListOf("sh", "-c")

        private const val BASH = "sh"
        private const val BASH_FILE = "toolScan.sh"

        private const val BASH_CMD = "cd %s && %s %s > %s 2>&1"

        private const val EXT_SUFFIX = "-extract"

        // arrowhead输出日志路径
        private const val RESULT_FILE_NAME_LOG = "scanBash.log"

        // scanTool 脚本文件模板key
        private const val TEMPLATE_KEY_INPUT_FILE = "inputFile"
        private const val TEMPLATE_KEY_RESULT_FILE = "resultFile"

        // 报告许可总数
        private const val TOTAL = "total"

        /**
         * 扫描执行脚本，相对于工作目录
         */
        private const val DEFAULT_CONFIG_BASH_PATH = "/toolScan.sh"
    }
}
