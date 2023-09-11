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

package com.tencent.bkrepo.common.analysis.pojo.scanner.utils

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.PullImageCmd
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.command.WaitContainerResultCallback
import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ulimit
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

object DockerUtils {
    private val logger = LoggerFactory.getLogger(DockerUtils::class.java)

    /**
     * 拉取镜像最大时间
     */
    private const val DEFAULT_PULL_IMAGE_DURATION = 15 * 60 * 1000L

    /**
     * 默认为1024，降低此值可降低容器在CPU时间分配中的优先级
     */
    private const val CONTAINER_CPU_SHARES = 512

    const val DEFAULT_DOCKER_SERVER = "https://index.docker.io/v1/"

    /**
     * 拉取镜像
     */
    fun DockerClient.pullImage(
        tag: String,
        userName: String?,
        password: String?,
    ) {
        val images = listImagesCmd().exec()
        val exists = images.any { image ->
            image.repoTags.any { it == tag }
        }
        if (exists) {
            return
        }
        logger.info("pulling image: $tag")
        val elapsedTime = measureTimeMillis {
            val result = pullImageCmd(tag)
                .withAuthConfigIfNeed(userName, password)
                .exec(PullImageResultCallback())
                .awaitCompletion(DEFAULT_PULL_IMAGE_DURATION, TimeUnit.MILLISECONDS)
            if (!result) {
                throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, "image $tag pull failed")
            }
        }
        logger.info("image $tag pulled, elapse: $elapsedTime")
    }

    fun DockerClient.createContainer(
        image: String,
        userName: String?,
        password: String?,
        hostConfig: HostConfig? = null,
        cmd: List<String>? = null,
    ): String {
        // 拉取镜像
        pullImage(image, userName, password)
        // 创建容器
        val createCmd = createContainerCmd(image)
        hostConfig?.let { createCmd.withHostConfig(it) }
        cmd?.let { createCmd.withCmd(it) }
        return createCmd.exec().id
    }

    fun DockerClient.startContainer(containerId: String, timeout: Long): Boolean {
        startContainerCmd(containerId).exec()
        val resultCallback = WaitContainerResultCallback()
        waitContainerCmd(containerId).exec(resultCallback)
        return resultCallback.awaitCompletion(timeout, TimeUnit.MILLISECONDS)
    }

    fun DockerClient.removeContainer(containerId: String, msg: String = "", force: Boolean = true) {
        try {
            removeContainerCmd(containerId).withForce(force).exec()
        } catch (e: Exception) {
            logger.warn("$msg, ${e.message}")
        }
    }

    fun dockerHostConfig(
        binds: Binds,
        maxSize: Long,
        mem: Long,
        withPrivileged: Boolean = false,
    ): HostConfig {
        return HostConfig().apply {
            withBinds(binds)
            withUlimits(arrayOf(Ulimit("fsize", maxSize, maxSize)))
            // 降低容器CPU优先级，限制可用的核心，避免调用DockerDaemon获其他系统服务时超时
            withCpuShares(CONTAINER_CPU_SHARES)
            withPrivileged(withPrivileged)
            withMemory(mem)
            val processorCount = Runtime.getRuntime().availableProcessors()
            if (processorCount > 2) {
                withCpusetCpus("0-${processorCount - 2}")
            } else if (processorCount == 2) {
                withCpusetCpus("0")
            }
        }
    }

    fun determineDockerServer(image: String): String {
        image.split("/").apply {
            return if (size > 2) {
                first()
            } else {
                DEFAULT_DOCKER_SERVER
            }
        }
    }

    private fun PullImageCmd.withAuthConfigIfNeed(userName: String?, password: String?): PullImageCmd {
        if (userName != null && password != null) {
            withAuthConfig(
                AuthConfig()
                    .withUsername(userName)
                    .withPassword(password),
            )
        }
        return this
    }
}
