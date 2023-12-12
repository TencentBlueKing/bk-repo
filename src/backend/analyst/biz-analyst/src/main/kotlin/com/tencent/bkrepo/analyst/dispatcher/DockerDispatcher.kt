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

package com.tencent.bkrepo.analyst.dispatcher

import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.okhttp.OkDockerHttpClient
import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.execution.DockerExecutionCluster
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.TemporaryScanTokenService
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.utils.DockerUtils.createContainer
import com.tencent.bkrepo.common.analysis.pojo.scanner.utils.DockerUtils.removeContainer
import com.tencent.bkrepo.statemachine.StateMachine
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class DockerDispatcher(
    executionCluster: DockerExecutionCluster,
    scannerProperties: ScannerProperties,
    scanService: ScanService,
    subtaskStateMachine: StateMachine,
    temporaryScanTokenService: TemporaryScanTokenService,
    executor: ThreadPoolTaskExecutor,
    private val subScanTaskDao: SubScanTaskDao,
    private val redisTemplate: ObjectProvider<RedisTemplate<String, String>>
) : SubtaskPushDispatcher<DockerExecutionCluster>(
    executionCluster,
    scannerProperties,
    scanService,
    subtaskStateMachine,
    temporaryScanTokenService,
    executor,
) {

    private val dockerClient by lazy {
        val dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(executionCluster.host)
            .withApiVersion(executionCluster.version)
            .build()

        val longHttpClient = OkDockerHttpClient.Builder()
            .dockerHost(dockerConfig.dockerHost)
            .sslConfig(dockerConfig.sslConfig)
            .connectTimeout(executionCluster.connectTimeout)
            .readTimeout(0)
            .build()

        DockerClientBuilder
            .getInstance(dockerConfig)
            .withDockerHttpClient(longHttpClient)
            .build()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun dispatch(subtask: SubScanTask): Boolean {
        val scanner = subtask.scanner
        require(scanner is StandardScanner)
        try {
            val command = buildCommand(
                cmd = scanner.cmd,
                baseUrl = scannerProperties.baseUrl,
                subtaskId = subtask.taskId,
                token = subtask.token!!,
                heartbeatTimeout = scannerProperties.heartbeatTimeout
            )
            val containerId = dockerClient.createContainer(
                image = scanner.image,
                userName = scanner.dockerRegistryUsername,
                password = scanner.dockerRegistryPassword,
                hostConfig = hostConfig(),
                cmd = command
            )
            dockerClient.startContainerCmd(containerId).exec()
            redisTemplate.ifAvailable
                ?.opsForValue()
                ?.set(containerIdKey(subtask.taskId), containerId, 1, TimeUnit.DAYS)
        } catch (e: Exception) {
            logger.error("dispatch subtask[${subtask.taskId}] failed", e)
            return false
        }
        return true
    }

    override fun clean(subtask: SubScanTask, subtaskStatus: String): Boolean {
        redisTemplate.ifAvailable
            ?.opsForValue()
            ?.get(containerIdKey(subtask.taskId))
            ?.let { dockerClient.removeContainer(it) }
        return true
    }

    override fun availableCount(): Int {
        val executingCount = subScanTaskDao
            .countTaskByStatusIn(listOf(SubScanTaskStatus.EXECUTING.name), executionCluster.name)
            .toInt()
        return executionCluster.maxTaskCount - executingCount
    }

    override fun name(): String {
        return executionCluster.name
    }

    private fun containerIdKey(subtaskId: String) = "scanner:dispatcher:sid:$subtaskId:cid"

    private fun hostConfig(): HostConfig? {
        val url = scannerProperties.baseUrl.toHttpUrl()
        val address = InetAddress.getByName(url.host)
        if (address.hostAddress == LOCALHOST) {
            return HostConfig.newHostConfig().withExtraHosts("${url.host}:host-gateway")
        }
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerDispatcher::class.java)
        private const val LOCALHOST = "127.0.0.1"
    }
}
