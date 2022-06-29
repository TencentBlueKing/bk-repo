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
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.command.WaitContainerResultCallback
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ulimit
import com.github.dockerjava.api.model.Volume
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.ArrowheadScanner
import com.tencent.bkrepo.scanner.executor.configuration.DockerProperties.Companion.SCANNER_EXECUTOR_DOCKER_ENABLED
import com.tencent.bkrepo.scanner.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.io.File
import java.io.UncheckedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.system.measureTimeMillis

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
        val maxFilesSize = maxFileSize(fileSize)
        val containerConfig = task.scanner.container

        // 拉取镜像
        pullImage(containerConfig.image)

        // 容器内tmp目录
        val tmpDir = createTmpDir(taskWorkDir)
        val tmpBind = Bind(tmpDir.absolutePath, Volume("/tmp"))
        // 容器内工作目录
        val bind = Bind(taskWorkDir.absolutePath, Volume(containerConfig.workDir))
        val hostConfig = HostConfig().apply {
            withBinds(tmpBind, bind)
            withUlimits(arrayOf(Ulimit("fsize", maxFilesSize, maxFilesSize)))
            configCpu(this)
        }

        val containerId = dockerClient.createContainerCmd(containerConfig.image)
            .withHostConfig(hostConfig)
            .withCmd(containerConfig.args)
            .withTty(true)
            .withStdinOpen(true)
            .exec().id
        taskContainerIdMap[task.taskId] = containerId
        logger.info(logMsg(task, "run container instance Id [$taskWorkDir, $containerId]"))
        try {
            dockerClient.startContainerCmd(containerId).exec()
            val resultCallback = WaitContainerResultCallback()
            dockerClient.waitContainerCmd(containerId).exec(resultCallback)
            val result = resultCallback.awaitCompletion(maxScanDuration, TimeUnit.MILLISECONDS)
            logger.info(logMsg(task, "task docker run result[$result], [$taskWorkDir, $containerId]"))
            if (!result) {
                return scanStatus(task, taskWorkDir, SubScanTaskStatus.TIMEOUT)
            }
            return scanStatus(task, taskWorkDir)
        } catch (e: UncheckedIOException) {
            if (e.cause is SocketTimeoutException) {
                logger.error(logMsg(task, "socket timeout[${e.message}]"))
                return scanStatus(task, taskWorkDir, SubScanTaskStatus.TIMEOUT)
            }
            throw e
        } finally {
            taskContainerIdMap.remove(task.taskId)
            ignoreExceptionExecute(logMsg(task, "stop container failed")) {
                dockerClient.stopContainerCmd(containerId).withTimeout(DEFAULT_STOP_CONTAINER_TIMEOUT_SECONDS).exec()
                dockerClient.killContainerCmd(containerId).withSignal(SIGNAL_KILL).exec()
            }
            ignoreExceptionExecute(logMsg(task, "remove container failed")) {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec()
            }
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

    /**
     * 拉取镜像
     */
    private fun pullImage(tag: String) {
        val images = dockerClient.listImagesCmd().exec()
        val exists = images.any { image ->
            image.repoTags.any { it == tag }
        }
        if (exists) {
            return
        }
        logger.info("pulling image: $tag")
        val elapsedTime = measureTimeMillis {
            val result = dockerClient
                .pullImageCmd(tag)
                .exec(PullImageResultCallback())
                .awaitCompletion(DEFAULT_PULL_IMAGE_DURATION, TimeUnit.MILLISECONDS)
            if (!result) {
                throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, "image $tag pull failed")
            }
        }
        logger.info("image $tag pulled, elapse: $elapsedTime")
    }

    private fun ignoreExceptionExecute(failedMsg: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.warn("$failedMsg, ${e.message}")
        }
    }

    private fun createTmpDir(workDir: File): File {
        val tmpDir = File(workDir, TMP_DIR_NAME)
        tmpDir.mkdirs()
        return tmpDir
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

    companion object {
        private val logger = LoggerFactory.getLogger(ArrowheadScanExecutor::class.java)

        /**
         * 扫描器配置文件路径
         */
        private const val CONFIG_FILE_TEMPLATE_CLASS_PATH = "classpath:standalone.toml"

        /**
         * 拉取镜像最大时间
         */
        private const val DEFAULT_PULL_IMAGE_DURATION = 15 * 60 * 1000L

        /**
         * 默认为1024，降低此值可降低容器在CPU时间分配中的优先级
         */
        private const val CONTAINER_CPU_SHARES = 512

        const val TMP_DIR_NAME = "tmp"

        private const val DEFAULT_STOP_CONTAINER_TIMEOUT_SECONDS = 30

        private const val SIGNAL_KILL = "KILL"
    }
}
