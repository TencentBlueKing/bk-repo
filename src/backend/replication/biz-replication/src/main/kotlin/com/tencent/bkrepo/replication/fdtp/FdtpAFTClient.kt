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

import com.tencent.bkrepo.fdtp.FdtpVersion
import com.tencent.bkrepo.fdtp.SimpleChannelPoolMap
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpFrameStream
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpHeaderFrame
import com.tencent.bkrepo.fdtp.codec.FdtpChunkStream
import com.tencent.bkrepo.fdtp.codec.FdtpHeaderNames
import com.tencent.bkrepo.fdtp.codec.FdtpHeaders
import io.netty.handler.stream.ChunkedFile
import io.netty.handler.stream.ChunkedStream
import io.netty.util.concurrent.DefaultPromise
import io.netty.util.concurrent.Promise
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress

/**
 * 使用fdtp协议传输artifact file
 * 底层使用fdtp的多路复用传输，即多个stream复用一个fdtp连接
 * */
class FdtpAFTClient(
    remoteAddress: InetSocketAddress,
    poolMap: SimpleChannelPoolMap,
    val certificate: String?,
    val authManager: FdtpAuthManager,
    private val properties: FdtpClientProperties,
) {

    private val channelPool = poolMap.get(remoteAddress)

    fun sendFile(
        file: File,
        headers: FdtpHeaders,
    ): Promise<FullFdtpAFTResponse> {
        val fdtStream = FdtpAFTHelper.createStream(channelPool, certificate, authManager)
        val streamId = fdtStream.id
        val stream = DefaultFdtpFrameStream(streamId)
        val chunkStream = FdtpChunkStream(ChunkedFile(file, properties.chunkSize), stream)
        return send0(fdtStream, streamId, headers, stream, chunkStream)
    }

    fun sendStream(inputStream: InputStream, headers: FdtpHeaders): Promise<FullFdtpAFTResponse> {
        val fdtStream = FdtpAFTHelper.createStream(channelPool, certificate, authManager)
        val streamId = fdtStream.id
        val stream = DefaultFdtpFrameStream(streamId)
        val chunkStream = FdtpChunkStream(ChunkedStream(inputStream, properties.chunkSize), stream)
        return send0(fdtStream, streamId, headers, stream, chunkStream)
    }

    private fun send0(
        fdtStream: FdtpStream,
        streamId: Int,
        headers: FdtpHeaders,
        stream: DefaultFdtpFrameStream,
        chunkStream: FdtpChunkStream,
    ): Promise<FullFdtpAFTResponse> {
        val channel = fdtStream.channel
        val promise = DefaultPromise<FullFdtpAFTResponse>(channel.eventLoop())
        val clientHandler = channel.pipeline().get(FdtpAFTClientHandler::class.java)
        clientHandler.put(streamId, promise)
        headers.add(FdtpHeaderNames.FDTP_VERSION, FdtpVersion.FDTP_1_0.text())
        headers.add(FdtpHeaderNames.STREAM_ID, stream.id().toString())
        val headerFrame = DefaultFdtpHeaderFrame(headers, false).stream(stream)
        channel.writeAndFlush(headerFrame)
        channel.writeAndFlush(chunkStream)
        return promise
    }
}
