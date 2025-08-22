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

import com.tencent.bkrepo.fdtp.FdtpVersion
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpFrameStream
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpHeaderFrame
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpHeaders
import com.tencent.bkrepo.fdtp.codec.FdtpChunkStream
import com.tencent.bkrepo.fdtp.codec.FdtpHeaderNames
import com.tencent.bkrepo.udt.netty.transport.nio.NioUdtProvider
import io.netty.bootstrap.Bootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.stream.ChunkedFile
import io.netty.util.concurrent.DefaultThreadFactory
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.random.Random

fun main() {
    val ssl = System.getProperty("ssl") != null
    val group = NioEventLoopGroup(
        Runtime.getRuntime().availableProcessors(),
        DefaultThreadFactory("client"),
        NioUdtProvider.BYTE_PROVIDER,
    )
    val remoteAddress = InetSocketAddress("127.0.0.1", 9000)
    val bootstrap = Bootstrap()
    bootstrap.group(group)
        .channelFactory(NioUdtProvider.BYTE_CONNECTOR)
        .handler(FileClientInitializer(ssl))
        .remoteAddress(remoteAddress)
    val channel = bootstrap.connect().syncUninterruptibly().channel()
    val fileClientHandler = FileClientHandler()
    channel.pipeline().addLast(fileClientHandler)
    val file = createTempFile()
    val path = createTempFile().path
    val fileSize = 1024 * 1024 * 10
    val data = Random.nextBytes(fileSize)
    file.writeBytes(data)
    try {
        val streamId = 3
        val stream = DefaultFdtpFrameStream(streamId)
        val headers = DefaultFdtpHeaders()
        headers.add(FdtpHeaderNames.FDTP_VERSION, FdtpVersion.FDTP_1_0.text())
        headers.add(FdtpHeaderNames.STREAM_ID, streamId.toString())
        headers.add("dst-path", path)
        // header frame
        val headerFrame = DefaultFdtpHeaderFrame(headers, false)
        headerFrame.stream(stream)
        channel.writeAndFlush(headerFrame)
        // data frame
        channel.writeAndFlush(FdtpChunkStream(ChunkedFile(file), stream))

        if (!fileClientHandler.responseSuccessfullyCompleted()) {
            println("Did not get fdtp response in expected time.")
        }
        println("Finished fdtp request,will close the connection.")
        channel.close().syncUninterruptibly()
    } finally {
        Files.deleteIfExists(file.toPath())
        Files.deleteIfExists(Paths.get(path))
        group.shutdownGracefully()
    }
}
