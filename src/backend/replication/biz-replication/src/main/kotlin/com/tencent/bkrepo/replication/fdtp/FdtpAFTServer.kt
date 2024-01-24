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

import com.tencent.bkrepo.udt.netty.transport.nio.NioUdtProvider
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.util.concurrent.DefaultThreadFactory
import java.io.InputStream
import java.net.InetSocketAddress

class FdtpAFTServer(
    private val fdtpServerProperties: FdtpServerProperties,
    private val requestHandler: FdtpAFTRequestHandler,
    private val fdtpAuthManager: FdtpAuthManager,
) {

    private val bootstrap = ServerBootstrap()
    private val bindAddress: InetSocketAddress
    private val acceptGroup: NioEventLoopGroup
    private val workerGroup: NioEventLoopGroup
    var privateKey: InputStream? = null
    var certificates: InputStream? = null
    var privateKeyPassword: String? = null
    private var sslContext: SslContext? = null
    private val backLog: Int = DEFAULT_BACK_LOG

    init {
        with(fdtpServerProperties) {
            bindAddress = InetSocketAddress(port)
            acceptGroup = NioEventLoopGroup(accepts, DefaultThreadFactory("fdtp-accept"), NioUdtProvider.BYTE_PROVIDER)
            workerGroup = NioEventLoopGroup(workers, DefaultThreadFactory("fdtp-worker"), NioUdtProvider.BYTE_PROVIDER)
            backLog = fdtpServerProperties.backLog
        }
    }

    fun start() {
        if (privateKey != null && certificates != null) {
            sslContext = SslContextBuilder.forServer(certificates, privateKey, privateKeyPassword).build()
        }
        bootstrap.group(acceptGroup, workerGroup)
            .channelFactory(NioUdtProvider.BYTE_ACCEPTOR)
            .option(ChannelOption.SO_BACKLOG, backLog)
            .childHandler(FdtpAFTServerInitializer(sslContext, requestHandler, fdtpAuthManager))
        bootstrap.bind(bindAddress).sync()
    }

    fun stop() {
        acceptGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
        acceptGroup.terminationFuture().sync()
        workerGroup.terminationFuture().sync()
    }

    fun getPort(): Int {
        return bindAddress.port
    }

    companion object {
        private const val DEFAULT_BACK_LOG = 128
    }
}
