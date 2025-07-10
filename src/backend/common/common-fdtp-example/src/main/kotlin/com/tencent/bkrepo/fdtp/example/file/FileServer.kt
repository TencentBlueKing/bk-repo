/* Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
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

package com.tencent.bkrepo.fdtp.example.file

import com.tencent.bkrepo.udt.netty.transport.nio.NioUdtProvider
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.util.concurrent.DefaultThreadFactory
import java.net.InetSocketAddress

fun main() {
    val bindAddress = InetSocketAddress(9000)
    val ssl = System.getProperty("ssl") != null
    val sslCtx: SslContext? = if (ssl) {
        val ssc = SelfSignedCertificate()
        SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build()
    } else {
        null
    }
    val group = NioEventLoopGroup(1, DefaultThreadFactory("server"), NioUdtProvider.BYTE_PROVIDER)
    try {
        val boot = ServerBootstrap()
        boot.group(group)
            .channelFactory(NioUdtProvider.BYTE_ACCEPTOR)
            .childHandler(FileServerInitializer(sslCtx))
        val channel = boot.bind(bindAddress).syncUninterruptibly().channel()
        println("Fdtp server listen on $bindAddress")
        channel.closeFuture().sync()
    } finally {
        group.shutdownGracefully()
    }
}
