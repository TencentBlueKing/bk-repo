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

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.Volume
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.ArrowheadScanner
import com.tencent.bkrepo.scanner.executor.configuration.DockerProperties.Companion.SCANNER_EXECUTOR_DOCKER_ENABLED
import com.tencent.bkrepo.scanner.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.scanner.executor.util.CommonUtils.logMsg
import com.tencent.bkrepo.scanner.executor.util.DockerUtils
import com.tencent.bkrepo.scanner.executor.util.DockerUtils.createContainer
import com.tencent.bkrepo.scanner.executor.util.DockerUtils.pullImage
import com.tencent.bkrepo.scanner.executor.util.DockerUtils.removeContainer
import com.tencent.bkrepo.scanner.executor.util.DockerUtils.startContainer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

@Component(ArrowheadScanner.TYPE)
@ConditionalOnProperty(SCANNER_EXECUTOR_DOCKER_ENABLED, matchIfMissing = true)
class ArrowheadScanExecutor @Autowired constructor(
    private val dockerClient: DockerClient,
    private val scannerExecutorProperties: ScannerExecutorProperties
) : AbsArrowheadScanExecutor() {

    private val taskContainerIdMap = ConcurrentHashMap<String, String>()

    @Value(CONFIG_FILE_TEMPLATE_CLASS_PATH)
    private lateinit var arrowheadConfigTemplate: Resource
    private val configTemplate by lazy { arrowheadConfigTemplate.inputStream.use { it.reader().readText() } }

    private val workDir by lazy { File(scannerExecutorProperties.workDir) }

    override fun doScan(
        taskWorkDir: File,
        configFile: File,
        task: ScanExecutorTask,
        fileSize: Long
    ): SubScanTaskStatus {
        require(task.scanner is ArrowheadScanner)

        val maxScanDuration = task.scanner.maxScanDuration(fileSize)
        // 容器内单文件大小限制为待扫描文件大小的3倍
        val maxFileSize = maxFileSize(fileSize)
        val containerConfig = task.scanner.container

        // 拉取镜像
        dockerClient.pullImage(containerConfig.image)

        // 容器内tmp目录
        val tmpDir = createTmpDir(taskWorkDir)
        val tmpBind = Bind(tmpDir.absolutePath, Volume("/tmp"))
        // 容器内工作目录
        val bind = Bind(taskWorkDir.absolutePath, Volume(containerConfig.workDir))
        val hostConfig = DockerUtils.dockerHostConfig(Binds(tmpBind, bind), maxFileSize)

        // 创建容器
        val containerId = dockerClient.createContainer(containerConfig.image, hostConfig, listOf(containerConfig.args))

        taskContainerIdMap[task.taskId] = containerId
        logger.info(logMsg(task, "run container instance Id [$taskWorkDir, $containerId]"))
        try {
            val result = dockerClient.startContainer(containerId, maxScanDuration)
            logger.info(logMsg(task, "task docker run result[$result], [$taskWorkDir, $containerId]"))
            if (!result) {
                return scanStatus(task, taskWorkDir, SubScanTaskStatus.TIMEOUT)
            }
            return scanStatus(task, taskWorkDir)
        } finally {
            taskContainerIdMap.remove(task.taskId)
            dockerClient.removeContainer(containerId, logMsg(task, "remove container failed"))
        }
    }

    override fun stop(taskId: String): Boolean {
        val containerId = taskContainerIdMap[taskId] ?: return false
        dockerClient.removeContainerCmd(containerId).withForce(true).exec()
        return true
    }

    override fun workDir() = workDir

    override fun configTemplate() = configTemplate

    private fun maxFileSize(fileSize: Long): Long {
        // 最大允许的单文件大小为待扫描文件大小3倍，先除以3，防止long溢出
        val maxFileSize = (Long.MAX_VALUE / 3L).coerceAtMost(fileSize) * 3L
        // 限制单文件大小，避免扫描器文件创建的文件过大
        return max(scannerExecutorProperties.fileSizeLimit.toBytes(), maxFileSize)
    }

    private fun createTmpDir(workDir: File): File {
        val tmpDir = File(workDir, TMP_DIR_NAME)
        tmpDir.mkdirs()
        return tmpDir
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArrowheadScanExecutor::class.java)

        /**
         * 扫描器配置文件路径
         */
        private const val CONFIG_FILE_TEMPLATE_CLASS_PATH = "classpath:standalone.toml"

        const val TMP_DIR_NAME = "tmp"
    }
}
