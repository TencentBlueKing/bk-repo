package com.tencent.bkrepo.scanner.executor.scancodeCheck

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.Volume
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScanCodeToolkitScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScancodeItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScancodeToolItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.scanner.ScancodeToolkitScanner
import com.tencent.bkrepo.scanner.api.ScanClient
import com.tencent.bkrepo.scanner.executor.ScanExecutor
import com.tencent.bkrepo.scanner.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.scanner.executor.util.DockerUtils
import com.tencent.bkrepo.scanner.executor.util.DockerUtils.pullImage
import com.tencent.bkrepo.scanner.executor.util.DockerUtils.removeContainer
import com.tencent.bkrepo.scanner.executor.util.DockerUtils.startContainer
import com.tencent.bkrepo.scanner.executor.util.FileUtils
import org.apache.commons.io.input.ReversedLinesFileReader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.stereotype.Component
import java.io.File
import java.io.UncheckedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

@Component(ScancodeToolkitScanner.TYPE)
class ScancodeToolkitExecutor @Autowired constructor(
    private val dockerClient: DockerClient,
    private val scanClient: ScanClient,
    private val scannerExecutorProperties: ScannerExecutorProperties
) : ScanExecutor {

    @Value(BASH_FILE_TEMPLATE_CLASS_PATH)
    private lateinit var scanToolBashTemplate: Resource

    // 任务对应容器id
    private val taskContainerIdMap = ConcurrentHashMap<String, String>()

    override fun scan(task: ScanExecutorTask): ScanExecutorResult {
        require(task.scanner is ScancodeToolkitScanner)
        val scanner = task.scanner
        // 创建工作目录
        val workDir = createWorkDir(scanner.rootPath, task.taskId)
        logger.info(logMsg(task, "create work dir success, $workDir"))
        try {

            val scannerInputFile = File(File(workDir, scanner.container.inputDir), task.sha256)
            scannerInputFile.parentFile.mkdirs()
            task.inputStream.use { taskInputStream ->
                scannerInputFile.outputStream().use { taskInputStream.copyTo(it) }
            }
            logger.info(logMsg(task, "read file success"))

            // 加载扫描配置文件
            loadScanBashFile(task, workDir, scannerInputFile)
            logger.info(logMsg(task, "load config success"))

            // 执行扫描
            File(workDir, scanner.container.outputDir).mkdir()
            val scanStatus = doScan(workDir, task, scannerInputFile)

            return result(
                scannerInputFile,
                File(workDir, scanner.container.outputDir),
                scanStatus
            )
        } finally {
            // 清理工作目录
            if (task.scanner.cleanWorkDir) {
                FileUtils.deleteRecursively(workDir)
            }
        }
    }

    override fun stop(taskId: String): Boolean {
        val containerId = taskContainerIdMap[taskId] ?: return false
        dockerClient.removeContainerCmd(containerId).withForce(true).exec()
        return true
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
    private fun loadScanBashFile(
        scanTask: ScanExecutorTask,
        workDir: File,
        scannerInputFile: File
    ): File {
        require(scanTask.scanner is ScancodeToolkitScanner)
        val scanner = scanTask.scanner
        val dockerImage = scanner.container
        val template = scanToolBashTemplate.inputStream.use { it.reader().readText() }
        val inputFilePath =
            "${dockerImage.inputDir.removePrefix(StringPool.SLASH)}${StringPool.SLASH}${scannerInputFile.name}"
        val outputDir = dockerImage.outputDir.removePrefix(StringPool.SLASH)
        val params = mapOf(
            TEMPLATE_KEY_INPUT_FILE to inputFilePath,
            TEMPLATE_KEY_RESULT_FILE to "$outputDir${StringPool.SLASH}$LICENSE_SCAN_RESULT_FILE_NAME"
        )

        val content = SpelExpressionParser()
            .parseExpression(template, TemplateParserContext())
            .getValue(params, String::class.java)!!
        val configFile = File(workDir, scanner.configBashPath)
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
    private fun doScan(workDir: File, task: ScanExecutorTask, inputFile: File): SubScanTaskStatus {
        require(task.scanner is ScancodeToolkitScanner)
        // 文件
        val maxScanDuration = task.scanner.maxScanDuration(inputFile.length())
        // 限制单文件大小，避免扫描器文件创建的文件过大
        val maxFileSize = (Long.MAX_VALUE / 3L).coerceAtMost(inputFile.length()) * 12L
        val maxFilesSize = max(scannerExecutorProperties.fileSizeLimit.toBytes(), maxFileSize)

        val containerConfig = task.scanner.container
        // 拉取镜像
        dockerClient.pullImage(containerConfig.image)

        // 容器内工作目录
        val hostConfig = DockerUtils.dockerHostConfig(
            Binds(Bind(workDir.absolutePath, Volume(containerConfig.workDir))), maxFilesSize
        )

        val containerCmd =
            DOCKER_BASH + listOf(BASH_CMD.format(containerConfig.workDir, BASH, BASH_FILE, RESULT_FILE_NAME_LOG))

        // 容器
        val containerId = dockerClient.createContainerCmd(containerConfig.image)
            .withHostConfig(hostConfig)
            .withCmd(containerCmd)
            .withTty(true)
            .withStdinOpen(true)
            .exec().id

        taskContainerIdMap[task.taskId] = containerId
        logger.info(logMsg(task, "run container instance Id [$workDir, $containerId]"))
        try {
            val result = dockerClient.startContainer(containerId, maxScanDuration * 15L)
            logger.info(logMsg(task, "task docker run result[$result], [$workDir, $containerId]"))
            if (!result) {
                return scanStatus(task, workDir, SubScanTaskStatus.TIMEOUT)
            }
            return scanStatus(task, workDir)
        } catch (e: UncheckedIOException) {
            if (e.cause is SocketTimeoutException) {
                logger.error(logMsg(task, "socket timeout[${e.message}]"))
                return scanStatus(task, workDir, SubScanTaskStatus.TIMEOUT)
            }
            throw e
        } finally {
            taskContainerIdMap.remove(task.taskId)
            dockerClient.removeContainer(containerId, logMsg(task, "remove container failed"))
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
        val workDir = File(File(scannerExecutorProperties.workDir, rootPath), taskId)
        if (!workDir.deleteRecursively() || !workDir.mkdirs()) {
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, workDir.absolutePath)
        }
        return workDir
    }

    /**
     * 通过扫描结果
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

    private fun logMsg(task: ScanExecutorTask, msg: String) = with(task) {
        "$msg, parentTaskId[$parentTaskId], subTaskId[$taskId], sha256[$sha256], scanner[${scanner.name}]"
    }

    /**
     * 解析扫描结果
     */
    fun result(
        inputFile: File,
        outputDir: File,
        scanStatus: SubScanTaskStatus
    ): ScanCodeToolkitScanExecutorResult {
        val licenseByTool = HashSet<String>()
        val scancodeToolItem = readJsonString<ScancodeToolItem>(File(outputDir, LICENSE_SCAN_RESULT_FILE_NAME))
        // 获取license信息
        scancodeToolItem?.files?.flatMapTo(HashSet()) {
            it.licenses.mapTo(HashSet()) { license -> license.spdxLicenseKey }
        }?.let { licenseByTool.addAll(it) }
        logger.info("tool scan result license set:$licenseByTool")

        if (licenseByTool.isEmpty()) return ScanCodeToolkitScanExecutorResult(scanStatus.name, emptyMap(), emptyList())
        val licenseIdToInfo = scanClient.licenseInfoByIds(licenseByTool.toList()).data
        val scancodeItems = arrayListOf<ScancodeItem>()
        scancodeToolItem?.files?.map {
            it.licenses.forEach { license ->
                val detail = licenseIdToInfo?.get(license.spdxLicenseKey)
                val path = it.path.removePrefix("${inputFile.name}$EXT_SUFFIX")
                val scancodeItem = if (detail == null) {
                    ScancodeItem(
                        license.spdxLicenseKey, "", "", null, path, null,
                        null, true, null, null
                    )
                } else {
                    ScancodeItem(
                        licenseId = detail.licenseId,
                        fullName = detail.name,
                        description = detail.reference,
                        recommended = !detail.isDeprecatedLicenseId,
                        isFsfLibre = detail.isFsfLibre,
                        isOsiApproved = detail.isOsiApproved,
                        compliance = detail.isTrust,
                        riskLevel = detail.risk,
                        dependentPath = path,
                        unknown = false
                    )
                }
                scancodeItems.add(scancodeItem)
            }
        }
        return ScanCodeToolkitScanExecutorResult(
            overview = overview(scancodeItems),
            scanStatus = scanStatus.name,
            scancodeItem = scancodeItems
        )
    }

    /**
     * 数量统计
     */
    private fun overview(scancodeItem: MutableList<ScancodeItem>): Map<String, Any?> {
        val overview = HashMap<String, Long>()
        LicenseNature.values().forEach {
            overview[it.natureName] = 0L
        }
        var unCompliance = 0L
        var unRecommend = 0L
        var unknown = 0L
        scancodeItem.forEach {
            // license risk
            val overviewKey = ScanCodeToolkitScanExecutorResult.overviewKeyOfLicenseRisk(it.riskLevel)
            overview[overviewKey] = overview.getOrDefault(overviewKey, 0L) + 1L
            // nature count
            if (it.recommended != null && !it.recommended!!) unRecommend++
            if (it.compliance != null && !it.compliance!!) unCompliance++
            if (it.unknown) unknown++
        }
        overview[ScanCodeToolkitScanExecutorResult.overviewKeyOf(LicenseNature.UN_COMPLIANCE.natureName)] = unCompliance
        overview[ScanCodeToolkitScanExecutorResult.overviewKeyOf(LicenseNature.UN_RECOMMEND.natureName)] = unRecommend
        overview[ScanCodeToolkitScanExecutorResult.overviewKeyOf(LicenseNature.UNKNOWN.natureName)] = unknown

        // 不推荐和不合规可能重合，单独统计总数
        overview[ScanCodeToolkitScanExecutorResult.overviewKeyOf(TOTAL)] = scancodeItem.size.toLong()

        logger.info("overview:${overview.toJsonString()}")

        return overview
    }

    private inline fun <reified T> readJsonString(file: File): T? {
        return if (file.exists()) {
            file.inputStream().use { it.readJsonString<T>() }
        } else {
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScancodeToolkitExecutor::class.java)

        /**
         * 扫描器配置文件路径
         */
        private const val BASH_FILE_TEMPLATE_CLASS_PATH = "classpath:toolScan.sh"

        private const val DEFAULT_STOP_CONTAINER_TIMEOUT_SECONDS = 30

        private const val SIGNAL_KILL = "KILL"

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
    }
}
