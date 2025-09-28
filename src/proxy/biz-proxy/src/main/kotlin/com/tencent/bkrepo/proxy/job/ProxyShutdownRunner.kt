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
import com.tencent.bkrepo.common.security.util.AESUtils
import com.tencent.bkrepo.common.service.proxy.ProxyEnv
import com.tencent.bkrepo.common.service.proxy.ProxyFeignClientFactory
import com.tencent.bkrepo.proxy.constant.PID_FILE_PATH
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

@Component
class ProxyShutdownRunner {

    private val proxyAuthClient: ProxyAuthClient by lazy { ProxyFeignClientFactory.create("auth") }

    @PreDestroy
    fun shutdown() {
        val projectId = ProxyEnv.getProjectId()
        val name = ProxyEnv.getName()
        val secretKey = ProxyEnv.getSecretKey()
        val ticket = proxyAuthClient.ticket(projectId, name).data!!
        val shutdownRequest = ProxyStatusRequest(
            projectId = projectId,
            name = name,
            message = AESUtils.encrypt("$name:shutdown:$ticket", secretKey)
        )
        proxyAuthClient.shutdown(shutdownRequest)
        deletePidFile()
        logger.info("shutdown")
    }

    private fun deletePidFile() {
        val file = File(PID_FILE_PATH)
        file.deleteOnExit()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProxyShutdownRunner::class.java)
    }
}
