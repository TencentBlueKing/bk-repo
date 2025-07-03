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

package com.tencent.bkrepo.replication.fdtp

import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.fdtp.SimpleChannelPoolMap
import com.tencent.bkrepo.udt.netty.transport.nio.NioUdtProvider
import io.netty.bootstrap.Bootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.DefaultThreadFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * fdtp-aft client工厂
 * */
@Component
@ConditionalOnProperty("fdtp.server.enabled")
class FdtpAFTClientFactory(fdtpAuthManager: FdtpAuthManager, clientProperties: FdtpClientProperties) :
    DisposableBean {
    private val group = NioEventLoopGroup(
        Runtime.getRuntime().availableProcessors(),
        DefaultThreadFactory("fdtp-client"),
        NioUdtProvider.BYTE_PROVIDER,
    )
    val bootstrap = Bootstrap()

    private val poolMap: SimpleChannelPoolMap

    init {
        bootstrap.group(group)
            .channelFactory(NioUdtProvider.BYTE_CONNECTOR)
        poolMap = SimpleChannelPoolMap(bootstrap, FdtpAFTClientChannelPoolHandler())
        Companion.poolMap = poolMap
        Companion.fdtpAuthManager = fdtpAuthManager
        Companion.clientProperties = clientProperties
    }

    override fun destroy() {
        poolMap.close()
        group.shutdownGracefully()
        group.terminationFuture().sync()
    }

    companion object {
        lateinit var poolMap: SimpleChannelPoolMap
        lateinit var fdtpAuthManager: FdtpAuthManager
        lateinit var clientProperties: FdtpClientProperties
        private val clientCache = ConcurrentHashMap<ClusterInfo, FdtpAFTClient>()

        /**
         * 创建一个fdtp-aft客户端
         * */
        fun createAFTClient(clusterInfo: ClusterInfo, defaultPort: Int? = null): FdtpAFTClient {
            with(clusterInfo) {
                return clientCache[clusterInfo] ?: synchronized(clusterInfo) {
                    clientCache[clusterInfo]?.let { return it }
                    val host = URL(url).host
                    val udpPort = udpPort ?: defaultPort
                    val serverAddress = InetSocketAddress(host, udpPort!!)
                    val newClient = FdtpAFTClient(
                        serverAddress,
                        poolMap,
                        certificate,
                        fdtpAuthManager,
                        clientProperties
                    )
                    clientCache.putIfAbsent(clusterInfo, newClient)
                    newClient
                }
            }
        }
    }
}
