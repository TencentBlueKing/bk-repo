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

package com.tencent.bkrepo.analysis.executor.util

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback.Adapter
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.Frame
import com.tencent.bkrepo.analysis.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.analysis.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.common.analysis.pojo.scanner.utils.DockerUtils
import com.tencent.bkrepo.common.analysis.pojo.scanner.utils.DockerUtils.createContainer
import com.tencent.bkrepo.common.analysis.pojo.scanner.utils.DockerUtils.removeContainer
import com.tencent.bkrepo.common.analysis.pojo.scanner.utils.DockerUtils.startContainer
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

class DockerScanHelper(
    private val scannerExecutorProperties: ScannerExecutorProperties,
    private val dockerClient: DockerClient
) {
    private val taskContainerIdMap = ConcurrentHashMap<String, String>()

    fun scan(
        image: String,
        userName: String?,
        password: String?,
        binds: Binds,
        args: List<String>,
        scannerInputFile: File,
        task: ScanExecutorTask
    ): Boolean {
        val maxScanDuration = task.scanner.maxScanDuration(scannerInputFile.length())
        // 创建容器
        val maxFileSize = maxFileSize(scannerInputFile.length())
        val hostConfig = DockerUtils.dockerHostConfig(binds, maxFileSize, task.scanner.memory)
        val containerId = dockerClient.createContainer(
            image = image,
            hostConfig = hostConfig,
            cmd = args,
            userName = userName,
            password = password
        )
        taskContainerIdMap[task.taskId] = containerId
        logger.info(CommonUtils.buildLogMsg(task, "run container instance Id [$containerId]"))
        try {
            // 启动容器
            val result = dockerClient.startContainer(containerId, maxScanDuration)
            val containerLogs = getContainerLogs(containerId)
            logger.info(
                CommonUtils.buildLogMsg(
                    task,
                    "task docker run result[$result], [$containerId], logs:\n $containerLogs"
                )
            )
            return result
        } finally {
            taskContainerIdMap.remove(task.taskId)
            dockerClient.removeContainer(containerId, CommonUtils.buildLogMsg(task, "remove container failed"))
        }
    }

    fun stop(taskId: String): Boolean {
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

    fun getContainerLogs(containerId: String): String {
        if (!scannerExecutorProperties.showContainerLogs) {
            return ""
        }
        val logCallback = LogCallback()
        val result = dockerClient.logContainerCmd(containerId)
            .withStdOut(true)
            .withStdErr(true)
            .withTail(CONTAINER_LOG_LINES)
            .exec(logCallback)
            .awaitCompletion(CONTAINER_LOG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!result) {
            logger.error("get result failed: $containerId")
        }
        return logCallback.content()
    }

    private class LogCallback : Adapter<Frame>() {
        private val logs = StringBuilder()
        override fun onNext(frame: Frame) {
            logs.appendLine(String(frame.payload).trim())
        }

        fun content(): String = logs.toString()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerScanHelper::class.java)
        private const val CONTAINER_LOG_LINES = 50
        private const val CONTAINER_LOG_TIMEOUT_SECONDS = 5L
    }
}
