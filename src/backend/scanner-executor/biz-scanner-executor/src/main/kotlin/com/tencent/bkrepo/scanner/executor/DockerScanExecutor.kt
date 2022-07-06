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

package com.tencent.bkrepo.scanner.executor

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Binds
import com.tencent.bkrepo.scanner.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.scanner.executor.util.CommonUtils
import com.tencent.bkrepo.scanner.executor.util.DockerUtils
import com.tencent.bkrepo.scanner.executor.util.DockerUtils.createContainer
import com.tencent.bkrepo.scanner.executor.util.DockerUtils.removeContainer
import com.tencent.bkrepo.scanner.executor.util.DockerUtils.startContainer
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class DockerScanExecutor(
    private val scannerExecutorProperties: ScannerExecutorProperties,
    private val dockerClient: DockerClient
) {
    private val taskContainerIdMap = ConcurrentHashMap<String, String>()

    fun scan(
        image: String,
        binds: Binds,
        args: List<String>,
        taskWorkDir: File,
        scannerInputFile: File,
        task: ScanExecutorTask
    ): Boolean {
        val maxScanDuration = task.scanner.maxScanDuration(scannerInputFile.length())
        // 创建容器
        val maxFileSize = maxFileSize(scannerInputFile.length())
        val hostConfig = DockerUtils.dockerHostConfig(binds, maxFileSize)
        val containerId = dockerClient.createContainer(image, hostConfig, args)

        taskContainerIdMap[task.taskId] = containerId
        logger.info(CommonUtils.logMsg(task, "run container instance Id [$taskWorkDir, $containerId]"))
        try {
            // 启动容器
            val result = dockerClient.startContainer(containerId, maxScanDuration)
            logger.info(CommonUtils.logMsg(task, "task docker run result[$result], [$taskWorkDir, $containerId]"))
            return result
        } finally {
            taskContainerIdMap.remove(task.taskId)
            dockerClient.removeContainer(containerId, CommonUtils.logMsg(task, "remove container failed"))
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

    companion object {
        private val logger = LoggerFactory.getLogger(DockerScanExecutor::class.java)
    }
}
