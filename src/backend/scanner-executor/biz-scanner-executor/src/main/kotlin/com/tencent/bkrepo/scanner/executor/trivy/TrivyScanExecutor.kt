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

package com.tencent.bkrepo.scanner.executor.trivy

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ulimit
import com.github.dockerjava.api.model.Volume
import com.tencent.bkrepo.common.api.constant.CharPool.COLON
import com.tencent.bkrepo.common.api.constant.CharPool.DASH
import com.tencent.bkrepo.common.api.constant.CharPool.SLASH
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.PUBLIC_GLOBAL_PROJECT
import com.tencent.bkrepo.common.artifact.constant.PUBLIC_VULDB_REPO
import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.scanner.pojo.scanner.CveOverviewKey.Companion.overviewKeyOf
import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.scanner.pojo.scanner.trivy.TrivyScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.trivy.TrivyScanResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.trivy.TrivyScanner
import com.tencent.bkrepo.common.scanner.pojo.scanner.trivy.VulnerabilityItem
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import com.tencent.bkrepo.scanner.executor.ScanExecutor
import com.tencent.bkrepo.scanner.executor.configuration.DockerProperties.Companion.SCANNER_EXECUTOR_DOCKER_ENABLED
import com.tencent.bkrepo.scanner.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.scanner.executor.pojo.Manifest
import com.tencent.bkrepo.scanner.executor.pojo.ManifestV1
import com.tencent.bkrepo.scanner.executor.pojo.ManifestV2
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.scanner.executor.util.DockerUtils
import com.tencent.bkrepo.scanner.executor.util.FileUtils.deleteRecursively
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.UncheckedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

@Component(TrivyScanner.TYPE)
@ConditionalOnProperty(SCANNER_EXECUTOR_DOCKER_ENABLED, matchIfMissing = true)
class TrivyScanExecutor @Autowired constructor(
    private val dockerClient: DockerClient,
    private val scannerExecutorProperties: ScannerExecutorProperties,
    private val repositoryClient: RepositoryClient,
    private val storageService: StorageService,
    private val nodeClient: NodeClient
) : ScanExecutor {

    private val taskContainerIdMap = ConcurrentHashMap<String, String>()

    override fun scan(task: ScanExecutorTask): ScanExecutorResult {
        require(task.scanner is TrivyScanner)
        val scanner = task.scanner
        // 创建工作目录
        val workDir = createWorkDir(scanner.rootPath, task.taskId)
        logger.info(logMsg(task, "create work dir success, $workDir"))
        try {
            //  加载待扫描文件
            val fileName = task.fullPath.replace(SLASH, DASH)
            val scannerInputFile = File(File(workDir, scanner.container.inputDir), fileName)
            val outputDir = File(workDir, scanner.container.outputDir)
            outputDir.mkdirs()
            scannerInputFile.parentFile.mkdirs()
            scannerInputFile.createNewFile()
            scannerInputFile.outputStream().use {
                generateScanFile(it, task)
            }
            // 执行扫描
            val scanStatus = doScan(workDir, task, scannerInputFile)
            return result(File(workDir, scanner.container.outputDir), scanStatus)
        } finally {
            // 清理工作目录
            if (task.scanner.cleanWorkDir) {
                deleteRecursively(workDir)
            }
        }
    }

    private fun generateScanFile(fileOutputStream: FileOutputStream, task: ScanExecutorTask) {
        val manifest = task.inputStream.readJsonString<ManifestV2>()
        logger.info(logMsg(task, manifest.toJsonString()))

        val prefixPath = task.fullPath
        TarArchiveOutputStream(fileOutputStream).use { tos ->
            val configSha = manifest.config.digest.replace(COLON.toString(), "__")
            val configFullPath = prefixPath + SLASH + configSha
            logger.info(logMsg(task, "configFullPath:$configFullPath"))

            val configNode = nodeClient.getNodeDetail(task.projectId, task.repoName, configFullPath).data ?: throw
            SystemErrorException(CommonMessageCode.SYSTEM_ERROR, "image config file $configFullPath acquire failed")
            val configInputStream =
                storageService.load(configNode.sha256!!, Range.full(configNode.size), task.storageCredentials)
            // 打包config文件
            val entry = TarArchiveEntry(configSha)
            entry.size = configNode.size
            tos.putArchiveEntry(entry)
            configInputStream?.copyTo(tos)
            tos.closeArchiveEntry()
            val layers = ArrayList<String>()
            // 打包layers文件
            manifest.layers.forEach {
                val layerSha = it.digest.replace(COLON.toString(), "__")
                layers.add(layerSha)
                val layerFullPath = prefixPath + SLASH + layerSha
                logger.info(logMsg(task, "layerFullPath:$layerFullPath"))

                val layerNode = nodeClient.getNodeDetail(task.projectId, task.repoName, layerFullPath).data ?: throw
                SystemErrorException(CommonMessageCode.SYSTEM_ERROR, "image layer $layerFullPath acquire failed")
                val layerInputStream =
                    storageService.load(layerNode.sha256!!, Range.full(layerNode.size), task.storageCredentials)
                val layerEntry = TarArchiveEntry(layerSha)
                layerEntry.size = layerNode.size
                tos.putArchiveEntry(layerEntry)
                layerInputStream?.copyTo(tos)
                tos.closeArchiveEntry()
            }
            // 打包manifest.json文件
            val repoTag = configNode.path.replace(SLASH.toString(), "")
            val manifestV1 = ManifestV1(listOf(Manifest(configSha, listOf(repoTag), layers)))
            val manifestV1Bytes = manifestV1.manifests.toJsonString().toByteArray()
            val manifestEntry = TarArchiveEntry(MANIFEST)
            manifestEntry.size = manifestV1Bytes.size.toLong()
            tos.putArchiveEntry(manifestEntry)
            ByteArrayInputStream(manifestV1Bytes).copyTo(tos)
            tos.closeArchiveEntry()
        }
    }

    override fun stop(taskId: String): Boolean {
        val containerId = taskContainerIdMap[taskId] ?: return false
        dockerClient.removeContainerCmd(containerId).withForce(true).exec()
        return true
    }

    private fun maxFileSize(fileSize: Long): Long {
        // 最大允许的单文件大小为待扫描文件大小3倍，先除以3，防止long溢出
        val maxFileSize = (Long.MAX_VALUE / 3L).coerceAtMost(fileSize) * 3L
        // 限制单文件大小，避免扫描器文件创建的文件过大
        return max(scannerExecutorProperties.fileSizeLimit.toBytes(), maxFileSize)
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
     * 创建容器执行扫描
     * @param workDir 工作目录,将挂载到容器中
     * @param task 扫描任务
     *
     * @return true 扫描成功， false 扫描失败
     */
    private fun doScan(workDir: File, task: ScanExecutorTask, scannerInputFile: File): SubScanTaskStatus {
        require(task.scanner is TrivyScanner)
        val fileSize = scannerInputFile.length()
        val maxScanDuration = task.scanner.maxScanDuration(fileSize)
        // 容器内单文件大小限制为待扫描文件大小的3倍
        val maxFilesSize = maxFileSize(fileSize)
        val containerConfig = task.scanner.container

        // 拉取镜像
        DockerUtils.pullImage(dockerClient, containerConfig.image)

        // 从默认仓库下载最新镜像漏洞数据库到cacheDir
        generateTrivyDB(task)

        // 容器内工作目录
        val bind = Bind(workDir.absolutePath, Volume(containerConfig.workDir))

        // 缓存目录映射
        val cacheBind = Bind(task.scanner.cacheDir, Volume(CACHE_DIR))

        val hostConfig = HostConfig().apply {
            withBinds(bind, cacheBind)
            withPrivileged(true)
            withUlimits(arrayOf(Ulimit("fsize", maxFilesSize, maxFilesSize)))
            configCpu(this)
        }

        val containerCmd = dockerClient.createContainerCmd(containerConfig.image)
            .withHostConfig(hostConfig)
            .withCmd(buildScanCmds(task, scannerInputFile))
            .withTty(true)
            .withStdinOpen(true)
        val cmd = containerCmd.cmd.contentToString()

        val cmdStr = StringBuilder()
        cmd.forEach { cmdStr.append(it).append(SPACING) }
        logger.info(logMsg(task, "run container cmd [$cmdStr]"))

        val containerId = containerCmd.exec().id
        taskContainerIdMap[task.taskId] = containerId

        logger.info(logMsg(task, "run container instance Id [$workDir, $containerId]"))
        try {
            val result = DockerUtils.containerRun(dockerClient, containerId, maxScanDuration * 9L)

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
            DockerUtils.ignoreExceptionExecute(logMsg(task, "stop container failed")) {
                dockerClient.stopContainerCmd(containerId)
                    .withTimeout(DEFAULT_STOP_CONTAINER_TIMEOUT_SECONDS).exec()
                dockerClient.killContainerCmd(containerId).withSignal(SIGNAL_KILL).exec()
            }
            DockerUtils.ignoreExceptionExecute(logMsg(task, "remove container failed")) {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec()
            }
        }
    }

    private fun generateTrivyDB(task: ScanExecutorTask) {
        require(task.scanner is TrivyScanner)

        val trivyDBFile = File(task.scanner.cacheDir, TRIVY_DB)
        val metedataJsonFile = File(task.scanner.cacheDir, METEDATA_JSON)
        if (!trivyDBFile.exists()) {
            logger.info(logMsg(task, "trivy.db file not exists"))
            trivyDBFile.parentFile.mkdirs()
            trivyDBFile.createNewFile()
        }
        if (!metedataJsonFile.exists()) {
            // 创建metadata.json文件，trivy扫描时必须的文件
            logger.info(logMsg(task, "metadata.json file not exists"))
            metedataJsonFile.parentFile.mkdirs()
            metedataJsonFile.createNewFile()
            val fileOutputStream = FileOutputStream(metedataJsonFile)
            fileOutputStream.use { it.write(METADATA_JSON_FILE_CONTENT.toByteArray()) }
            logger.info(logMsg(task, "create metadata.json file success"))
        }
        val md5 = trivyDBFile.md5()
        val newestNode = getNewestNode()
        if (md5 != newestNode.get("md5")) {
            logger.info(logMsg(task, "trivy.db changed"))
            // md5不相等时，更新trivy.db文件
            trivyDBFile.delete()
            trivyDBFile.createNewFile()
            val trivyDBInputStream = getTrivyDBInputStream(task)
            trivyDBInputStream.use { inputStream ->
                trivyDBFile.outputStream().use { inputStream.copyTo(it) }
            }
            logger.info(logMsg(task, "update trivy.db file success"))
        }
    }

    private fun getTrivyDBInputStream(task: ScanExecutorTask): ArtifactInputStream {
        // 获取trivy默认仓库信息
        val repoRes = repositoryClient.getRepoDetail(PUBLIC_GLOBAL_PROJECT, PUBLIC_VULDB_REPO)
        if (repoRes.isNotOk()) {
            logger.error(
                "Get repo info failed: code[${repoRes.code}], message[${repoRes.message}]," +
                    " projectId[$PUBLIC_GLOBAL_PROJECT], repoName[$PUBLIC_VULDB_REPO]"
            )
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, repoRes.message ?: "")
        }
        val repositoryDetail =
            repoRes.data ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, PUBLIC_VULDB_REPO)
        val newestNode = getNewestNode()
        logger.info(logMsg(task, "trivy.db latest metadata [${newestNode.toJsonString()}]"))

        val artifactInputStream =
            storageService.load(
                newestNode.get("sha256") as String, Range.full(newestNode.get("size").toString().toLong()),
                repositoryDetail.storageCredentials
            )
        if (artifactInputStream == null) {
            logger.error("load trivy.db file failed")
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, repoRes.message ?: "")
        }
        return artifactInputStream
    }

    private fun getNewestNode(): Map<String, Any?> {
        // 按修改时间 创建时间倒序排序，第一位则为最新的trivy.db文件
        val queryModel = NodeQueryBuilder().projectId(PUBLIC_GLOBAL_PROJECT).repoName(PUBLIC_VULDB_REPO)
            .path("/trivy/")
            .page(1, 10)
            .sort(Sort.Direction.DESC, "lastModifiedDate", "createdDate")
            .select("fullPath", "size", "sha256", "md5")
            .build()
        val nodeRes = nodeClient.search(queryModel)
        if (nodeRes.isNotOk()) {
            logger.error(
                "Get node info failed: code[${nodeRes.code}], message[${nodeRes.message}]," +
                    " projectId[$PUBLIC_GLOBAL_PROJECT], repoName[$PUBLIC_VULDB_REPO]"
            )
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, nodeRes.message ?: "")
        }
        // 获取最新的trivy.db
        val newestDB = nodeRes.data!!.records.firstOrNull()
        if (newestDB == null) {
            logger.error("Get trivy.db file failed")
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, "Get trivy.db file failed")
        }
        return newestDB
    }

    private fun buildScanCmds(task: ScanExecutorTask, scannerInputFile: File): List<String> {
        require(task.scanner is TrivyScanner)
        val cmds = ArrayList<String>()
        cmds.add(CACHE_DIR_COMMAND)
        cmds.add(CACHE_DIR)
        cmds.add("-f")
        cmds.add("json")
        cmds.add("-o")
        cmds.add(task.scanner.container.workDir + scanResultFilePath(task))
        cmds.add(SKIP_DB_UPDATE_COMMAND)
        cmds.add(SCAN_FILE_PATH_COMMAND)
        cmds.add(task.scanner.container.workDir + task.scanner.container.inputDir + SLASH + scannerInputFile.name)
        return cmds
    }

    private fun configCpu(hostConfig: HostConfig) {
        // 降低容器CPU优先级，限制可用的核心，避免调用DockerDaemon获其他系统服务时超时
        hostConfig.withCpuShares(CONTAINER_CPU_SHARES)
        val processorCount = Runtime.getRuntime().availableProcessors()
        if (processorCount > 2) {
            hostConfig.withCpusetCpus("0-${processorCount - 2}")
        } else if (processorCount == 2) {
            hostConfig.withCpusetCpus("0")
        }
    }

    /**
     * 解析arrowhead输出日志，判断扫描结果
     */
    private fun scanStatus(
        task: ScanExecutorTask,
        workDir: File,
        status: SubScanTaskStatus = SubScanTaskStatus.FAILED
    ): SubScanTaskStatus {
        val resultFile = File(workDir, scanResultFilePath(task))
        logger.info(logMsg(task, "resultFilePath:${resultFile.absolutePath}"))
        if (!resultFile.exists() || resultFile.length() < 1) {
            logger.info(logMsg(task, "scan result file not exists"))
            return status
        }
        return SubScanTaskStatus.SUCCESS
    }

    /**
     * 解析扫描结果
     */
    private fun result(outputDir: File, scanStatus: SubScanTaskStatus): TrivyScanExecutorResult {

        val scanResult = readJsonString<List<TrivyScanResult>>(File(outputDir, SCAN_RESULT_FILE_NAME))
        val vulnerabilityItems = ArrayList<VulnerabilityItem>()
        val overview = overview(vulnerabilityItems, scanResult)
        return TrivyScanExecutorResult(
            scanStatus = scanStatus.name,
            overview = overview,
            vulnerabilityItems = vulnerabilityItems
        )
    }

    private fun overview(vulnerabilityItems: ArrayList<VulnerabilityItem>, scanResult: List<TrivyScanResult>?):
        Map<String, Any?> {
        val overview = HashMap<String, Long>()
        // cve count
        scanResult?.forEach { result ->
            result.vulnerabilities?.let { vulnerabilityItems.addAll(it) }
            result.vulnerabilities?.forEach {
                val overviewKey = overviewKeyOf(it.severity.toLowerCase())
                overview[overviewKey] = overview.getOrDefault(overviewKey, 0L) + 1L
            }
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

    /**
     * 扫描结果文件相对路径
     */
    private fun scanResultFilePath(task: ScanExecutorTask): String = with(task) {
        require(task.scanner is TrivyScanner)
        return@with StringBuilder().append(task.scanner.container.outputDir).append(SLASH).append(SCAN_RESULT_FILE_NAME)
            .toString()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TrivyScanExecutor::class.java)

        /**
         * 默认为1024，降低此值可降低容器在CPU时间分配中的优先级
         */
        private const val CONTAINER_CPU_SHARES = 512

        private const val DEFAULT_STOP_CONTAINER_TIMEOUT_SECONDS = 30

        private const val SIGNAL_KILL = "KILL"

        private const val SPACING = " "

        /**
         * 指定容器内缓存目录命令
         */
        private const val CACHE_DIR_COMMAND = "--cache-dir"

        /**
         * 容器内缓存目录
         */
        private const val CACHE_DIR = "/root/.cache/trivy"

        /**
         * 扫描结果文件
         */
        private const val SCAN_RESULT_FILE_NAME = "result.json"

        /**
         * 跳过trivy.db文件更新命令
         */
        private const val SKIP_DB_UPDATE_COMMAND = "--skip-db-update"

        /**
         * 指定扫描镜像文件
         */
        private const val SCAN_FILE_PATH_COMMAND = "--input"

        /**
         * trivy镜像漏洞库trivy.db相对路径
         */
        private const val TRIVY_DB = "db/trivy.db"

        /**
         * trivy镜像漏洞库metadata.json相对路径
         */
        private const val METEDATA_JSON = "db/metadata.json"

        /**
         * metadata.json文件内容，确保trivy必须需要metadata.json文件
         */
        private const val METADATA_JSON_FILE_CONTENT =
            """{"Version":1,"Type":1,"NextUpdate":"2022-05-31T06:51:38.429826331Z","UpdatedAt":"2022-05-31T00:51:38.429826631Z","DownloadedAt":"0001-01-01T00:00:00Z"}"""

        /**
         * manifest.json文件名
         */
        private const val MANIFEST = "manifest.json"
    }
}
