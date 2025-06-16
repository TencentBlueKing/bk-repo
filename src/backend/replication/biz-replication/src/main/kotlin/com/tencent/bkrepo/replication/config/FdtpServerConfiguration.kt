/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.config

import com.tencent.bkrepo.common.security.service.ServiceAuthManager
import com.tencent.bkrepo.common.security.service.ServiceAuthProperties
import com.tencent.bkrepo.replication.controller.api.BaseCacheHandler
import com.tencent.bkrepo.replication.controller.api.ReplicationFdtpAFTRequestHandler
import com.tencent.bkrepo.replication.fdtp.DefaultFdtpAFTRequestHandler
import com.tencent.bkrepo.replication.fdtp.FdtpAFTRequestHandler
import com.tencent.bkrepo.replication.fdtp.FdtpAFTServer
import com.tencent.bkrepo.replication.fdtp.FdtpAuthManager
import com.tencent.bkrepo.replication.fdtp.FdtpServerProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@EnableConfigurationProperties(FdtpServerProperties::class)
@ConditionalOnProperty("fdtp.server.enabled", matchIfMissing = true)
class FdtpServerConfiguration {
    @Bean
    @ConditionalOnMissingBean(FdtpAFTRequestHandler::class)
    fun fdtpAFTRequestHandler() = DefaultFdtpAFTRequestHandler()


    @Bean
    @Primary
    fun replicationFdtpAFTRequestHandler(
        baseCacheHandler: BaseCacheHandler
    ) = ReplicationFdtpAFTRequestHandler(baseCacheHandler)


    @Bean
    fun fdtpAFTServer(
        fdtpServerProperties: FdtpServerProperties,
        handler: FdtpAFTRequestHandler,
        fdtpAuthManager: FdtpAuthManager,
    ): FdtpAFTServer {
        val server = FdtpAFTServer(fdtpServerProperties, handler, fdtpAuthManager)
        configSsl(server, fdtpServerProperties)
        return server
    }

    @Bean
    fun fdtpAuthManager(fdtpServerProperties: FdtpServerProperties): FdtpAuthManager {
        val secretKey = fdtpServerProperties.secretKey
        val serverProperties = ServiceAuthProperties(true, secretKey)
        val serviceAuthManager = ServiceAuthManager(serverProperties)
        return FdtpAuthManager(serviceAuthManager)
    }

    private fun configSsl(server: FdtpAFTServer, fdtpServerProperties: FdtpServerProperties) {
        with(fdtpServerProperties) {
            if (certificates != null && privateKey != null) {
                server.certificates = fdtpServerProperties.certificates!!.byteInputStream()
                server.privateKeyPassword = fdtpServerProperties.privateKeyPassword
                server.privateKey = fdtpServerProperties.privateKey!!.byteInputStream()
            }
        }
    }
}
