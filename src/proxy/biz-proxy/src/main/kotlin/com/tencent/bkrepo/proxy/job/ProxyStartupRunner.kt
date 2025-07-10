/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.proxy.job

import com.tencent.bkrepo.auth.api.proxy.ProxyAuthClient
import com.tencent.bkrepo.auth.pojo.proxy.ProxyStatusRequest
import com.tencent.bkrepo.common.api.constant.retry
import com.tencent.bkrepo.common.security.util.AESUtils
import com.tencent.bkrepo.common.service.proxy.ProxyEnv
import com.tencent.bkrepo.common.service.proxy.ProxyFeignClientFactory
import com.tencent.bkrepo.common.service.proxy.SessionKeyHolder
import com.tencent.bkrepo.proxy.constant.PID_FILE_PATH
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.exitProcess

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProxyStartupRunner : ApplicationRunner {

    private val proxyAuthClient: ProxyAuthClient by lazy { ProxyFeignClientFactory.create("auth") }

    override fun run(args: ApplicationArguments?) {
        try {
            retry(RETRY_TIME, block = {
                startup()
                writePidFile()
            })
        } catch (e: Exception) {
            logger.error("startup failed: ", e)
            exitProcess(1)
        }
    }

    @Retryable(Exception::class)
    private fun startup() {
        logger.info("startup")
        val projectId = ProxyEnv.getProjectId()
        val name = ProxyEnv.getName()
        val secretKey = ProxyEnv.getSecretKey()
        val ticket = proxyAuthClient.ticket(projectId, name).data!!
        val startupRequest = ProxyStatusRequest(
            projectId = projectId,
            name = name,
            message = AESUtils.encrypt("$name:$STARTUP_OPERATION:$ticket", secretKey)
        )
        val encSessionKey = proxyAuthClient.startup(startupRequest).data!!
        val sessionKey = AESUtils.decrypt(encSessionKey, secretKey)
        SessionKeyHolder.setSessionKey(sessionKey)
        logger.info("startup success")
    }

    private fun writePidFile() {
        val file = File(PID_FILE_PATH)
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }
        val pid = getProcessId()
        file.writeText(pid.toString())
    }

    private fun getProcessId(): Long {
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        val name = runtimeMXBean.name
        return name.split("@")[0].toLong()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProxyStartupRunner::class.java)
        private const val RETRY_TIME = 3
        private const val STARTUP_OPERATION = "startup"
    }
}
