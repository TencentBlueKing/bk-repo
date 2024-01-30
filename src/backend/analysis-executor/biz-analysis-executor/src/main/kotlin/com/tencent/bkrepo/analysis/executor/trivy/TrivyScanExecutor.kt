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

package com.tencent.bkrepo.analysis.executor.trivy

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.Volume
import com.tencent.bkrepo.analysis.executor.CommonScanExecutor
import com.tencent.bkrepo.analysis.executor.configuration.DockerProperties.Companion.SCANNER_EXECUTOR_DOCKER_ENABLED
import com.tencent.bkrepo.analysis.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.analysis.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.analysis.executor.util.CommonUtils.buildLogMsg
import com.tencent.bkrepo.analysis.executor.util.CommonUtils.readJsonString
import com.tencent.bkrepo.analysis.executor.util.DockerScanHelper
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.DbSource
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.TrivyScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.TrivyScanResults
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.TrivyScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.VulnerabilityItem
import com.tencent.bkrepo.common.api.constant.CharPool.SLASH
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode.RESOURCE_NOT_FOUND
import com.tencent.bkrepo.common.api.message.CommonMessageCode.SYSTEM_ERROR
import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileOutputStream

@Component("${TrivyScanner.TYPE}Executor")
@ConditionalOnProperty(SCANNER_EXECUTOR_DOCKER_ENABLED, matchIfMissing = true)
class TrivyScanExecutor @Autowired constructor(
    dockerClient: DockerClient,
    private val scannerExecutorProperties: ScannerExecutorProperties,
    private val repositoryClient: RepositoryClient,
    private val storageService: StorageService,
    private val nodeClient: NodeClient
) : CommonScanExecutor() {

    private val dockerScanHelper = DockerScanHelper(scannerExecutorProperties, dockerClient)

    override fun doScan(
        taskWorkDir: File,
        scannerInputFile: File,
        sha256: String,
        task: ScanExecutorTask
    ): SubScanTaskStatus {
        require(task.scanner is TrivyScanner)
        val containerConfig = task.scanner.container

        // 从默认仓库下载最新镜像漏洞数据库到cacheDir
        val cacheDir = File(
            "${scannerExecutorProperties.workDir}/${task.scanner.rootPath}/${task.scanner.cacheDir}"
        )
        if (task.scanner.vulDbConfig.dbSource != DbSource.TRIVY.code) {
            generateTrivyDB(task, cacheDir)
        }

        // 需要提前创建输出目录，否则trivy报错
        File(taskWorkDir, containerConfig.outputDir).mkdirs()

        // 容器内工作目录
        val bind = Bind(taskWorkDir.absolutePath, Volume(containerConfig.workDir))
        // 缓存目录映射
        val cacheBind = Bind(cacheDir.absolutePath, Volume(CACHE_DIR))
        val cmd = buildScanCmds(task, scannerInputFile)
        val result = dockerScanHelper.scan(
            image = containerConfig.image,
            binds = Binds(bind, cacheBind),
            args = cmd,
            scannerInputFile = scannerInputFile,
            task = task,
            userName = containerConfig.dockerRegistryUsername,
            password = containerConfig.dockerRegistryPassword
        )
        if (!result) {
            return scanStatus(task, taskWorkDir, SubScanTaskStatus.TIMEOUT)
        }
        return scanStatus(task, taskWorkDir)
    }

    override fun scannerInputFile(taskWorkDir: File, task: ScanExecutorTask): File {
        val scanner = task.scanner
        require(scanner is TrivyScanner)
        return File(File(taskWorkDir, scanner.container.inputDir), task.file.name)
    }

    override fun workDir() = File(scannerExecutorProperties.workDir)

    override fun result(taskWorkDir: File, task: ScanExecutorTask, scanStatus: SubScanTaskStatus): ScanExecutorResult {
        val scanner = task.scanner
        require(scanner is TrivyScanner)
        return result(File(taskWorkDir, scanner.container.outputDir), scanStatus)
    }

    override fun stop(taskId: String): Boolean {
        return dockerScanHelper.stop(taskId)
    }

    private fun generateTrivyDB(task: ScanExecutorTask, cacheDir: File) {
        require(task.scanner is TrivyScanner)
        // 创建metadata.json文件，trivy扫描时必须的文件
        val metadataFile = File(cacheDir, METEDATA_JSON)
        if (!metadataFile.exists()) {
            logger.info(buildLogMsg(task, "metadata.json file not exists"))
            metadataFile.parentFile.mkdirs()
            metadataFile.createNewFile()
            FileOutputStream(metadataFile).use { it.write(METADATA_JSON_FILE_CONTENT.toByteArray()) }
            logger.info(buildLogMsg(task, "create metadata.json file success"))
        }

        // 加载最新漏洞库
        val newestNode = getNewestNode(task.scanner.vulDbConfig.projectId, task.scanner.vulDbConfig.repo)
        val dbFile = File(cacheDir, TRIVY_DB)
        if (!dbFile.parentFile.exists()) {
            dbFile.parentFile.mkdirs()
        }
        if (!dbFile.exists() || dbFile.md5() != newestNode["md5"]) {
            logger.info(buildLogMsg(task, "updating trivy.db"))
            dbFile.delete()
            dbFile.createNewFile()
            getTrivyDBInputStream(newestNode, task).use { inputStream ->
                dbFile.outputStream().use { inputStream.copyTo(it) }
            }
            logger.info(buildLogMsg(task, "update trivy.db file success"))
        }
    }

    private fun getTrivyDBInputStream(dbNode: Map<String, Any?>, task: ScanExecutorTask): ArtifactInputStream {
        val scanner = task.scanner
        require(scanner is TrivyScanner)
        // 获取trivy默认仓库信息
        val repoRes = repositoryClient.getRepoDetail(scanner.vulDbConfig.projectId, scanner.vulDbConfig.repo)
        if (repoRes.isNotOk()) {
            logger.error(
                "Get repo info failed: code[${repoRes.code}], message[${repoRes.message}]," +
                    " projectId[${scanner.vulDbConfig.projectId}], repoName[${scanner.vulDbConfig.repo}]"
            )
            throw SystemErrorException(SYSTEM_ERROR, repoRes.message ?: "")
        }
        val repositoryDetail = repoRes.data
            ?: throw NotFoundException(RESOURCE_NOT_FOUND, scanner.vulDbConfig.repo)

        val sha256 = dbNode["sha256"] as String
        val size = dbNode["size"].toString().toLong()
        return storageService.load(sha256, Range.full(size), repositoryDetail.storageCredentials)
            ?: throw SystemErrorException(SYSTEM_ERROR, "load trivy.db file failed: res: ${repoRes.message}")
    }

    private fun getNewestNode(projectId: String, repo: String): Map<String, Any?> {
        // 按修改时间 创建时间倒序排序，第一位则为最新的trivy.db文件
        val queryModel = NodeQueryBuilder().projectId(projectId).repoName(repo)
            .path("/trivy/")
            .page(1, 1)
            .sort(Sort.Direction.DESC, "lastModifiedDate", "createdDate")
            .select("fullPath", "size", "sha256", "md5")
            .build()
        val nodeRes = nodeClient.queryWithoutCount(queryModel)
        if (nodeRes.isNotOk()) {
            logger.error(
                "Get node info failed: code[${nodeRes.code}], message[${nodeRes.message}]," +
                    " projectId[$projectId], repoName[$repo]"
            )
            throw SystemErrorException(SYSTEM_ERROR, nodeRes.message ?: "")
        }
        // 获取最新的trivy.db
        val newestDB = nodeRes.data!!.records.firstOrNull()
        if (newestDB == null) {
            logger.error("Get trivy.db file failed")
            throw SystemErrorException(SYSTEM_ERROR, "Get trivy.db file failed")
        }
        return newestDB
    }

    private fun buildScanCmds(task: ScanExecutorTask, scannerInputFile: File): List<String> {
        val scanner = task.scanner
        require(scanner is TrivyScanner)
        val maxScanDuration = task.scanner.maxScanDuration(scannerInputFile.length())
        val cmds = ArrayList<String>()
        cmds.add(CACHE_DIR_COMMAND)
        cmds.add(CACHE_DIR)
        cmds.add("image")
        cmds.add("--timeout")
        cmds.add("${maxScanDuration / 1000L}s")
        cmds.add("--security-checks")
        cmds.add("vuln")
        cmds.add("-f")
        cmds.add("json")
        cmds.add("-o")
        cmds.add(scanner.container.workDir + scanResultFilePath(task))
        if (scanner.vulDbConfig.dbSource != DbSource.TRIVY.code) {
            cmds.add(SKIP_DB_UPDATE_COMMAND)
            cmds.add(OFFLINE_SCAN_COMMAND)
        }
        cmds.add(SCAN_FILE_PATH_COMMAND)
        cmds.add(scanner.container.workDir + scanner.container.inputDir + SLASH + scannerInputFile.name)
        return cmds
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
        logger.info(buildLogMsg(task, "resultFilePath:${resultFile.absolutePath}"))
        if (!resultFile.exists() || resultFile.length() < 1) {
            logger.info(buildLogMsg(task, "scan result file not exists"))
            return status
        }
        return SubScanTaskStatus.SUCCESS
    }

    /**
     * 解析扫描结果
     */
    private fun result(outputDir: File, scanStatus: SubScanTaskStatus): TrivyScanExecutorResult {
        val scanResult = readJsonString<TrivyScanResults>(File(outputDir, SCAN_RESULT_FILE_NAME))
        val vulnerabilityItems = ArrayList<VulnerabilityItem>()
        // cve count
        scanResult?.results?.forEach { result ->
            result.vulnerabilities?.let { vulnerabilityItems.addAll(it) }
        }

        return TrivyScanExecutorResult(
            scanStatus = scanStatus.name,
            vulnerabilityItems = vulnerabilityItems
        )
    }

    /**
     * 扫描结果文件相对路径
     */
    private fun scanResultFilePath(task: ScanExecutorTask): String = with(task) {
        require(scanner is TrivyScanner)
        return "${scanner.container.outputDir}/$SCAN_RESULT_FILE_NAME"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TrivyScanExecutor::class.java)

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
         * 离线扫描，trivy扫描jar时默认会发送网络请求获取jar相关信息，需要开启离线模式
         */
        private const val OFFLINE_SCAN_COMMAND = "--offline-scan"

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
            "{\"Version\":2,\"NextUpdate\":\"2022-07-15T12:06:50.078024068Z\"," +
                "\"UpdatedAt\":\"2022-07-15T06:06:50.078024668Z\",\"DownloadedAt\":\"0001-01-01T00:00:00Z\"}"
    }
}
