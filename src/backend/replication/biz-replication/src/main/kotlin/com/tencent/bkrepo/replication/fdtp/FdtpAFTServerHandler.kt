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

import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.file.chunk.ChunkedArtifactFile
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.fdtp.FdtpVersion
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpHeaderFrame
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpHeaders
import com.tencent.bkrepo.fdtp.codec.FdtpDataFrame
import com.tencent.bkrepo.fdtp.codec.FdtpFrameStream
import com.tencent.bkrepo.fdtp.codec.FdtpHeaderFrame
import com.tencent.bkrepo.fdtp.codec.FdtpHeaderNames
import com.tencent.bkrepo.fdtp.codec.FdtpHeaders
import com.tencent.bkrepo.fdtp.codec.FdtpResponseStatus
import com.tencent.bkrepo.fdtp.codec.FdtpStreamFrame
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.udt.StatusUDT
import com.tencent.bkrepo.udt.netty.transport.nio.NioUdtProvider
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * fdtp-aft的服务端处理
 * */
class FdtpAFTServerHandler(private val requestHandler: FdtpAFTRequestHandler) :
    SimpleChannelInboundHandler<FdtpStreamFrame>() {

    /**
     * 请求map
     * 多路复用时，单个channel上会有多个请求
     * */
    private val requestMap = ConcurrentHashMap<Int, FullFdtpAFTRequest>()

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val socket = NioUdtProvider.socketUDT(ctx.channel())
        if (socket?.status() == StatusUDT.NONEXIST) {
            logger.info("Peer close connection $socket")
        } else {
            logger.error("exceptionCaught $socket", cause)
        }
        close(ctx)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            if (evt.state() == IdleState.ALL_IDLE) {
                logger.info("Close idle connection ${ctx.channel()}")
                close(ctx)
            }
        }
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FdtpStreamFrame) {
        try {
            if (msg is FdtpHeaderFrame) {
                onHeadersRead(ctx, msg)
            }
            if (msg is FdtpDataFrame) {
                onDataRead(ctx, msg)
            }
        } catch (e: Exception) {
            logger.error("Handle fdtp stream frame error", e)
            val stream = msg.stream()!!
            val errorResponse = FdtpResponseBuilder.error(e.message.orEmpty())
            sendResponse(ctx, stream, errorResponse, null)
            requestMap[stream.id()]?.let {
                logAccess(it, errorResponse)
                removeRequestContext(it)
            }
        }
    }

    private fun onHeadersRead(ctx: ChannelHandlerContext, headers: FdtpHeaderFrame) {
        val stream = headers.stream()
        val streamId = stream!!.id()
        val fileSha256 = headers.headers().get(SHA256)
        checkNotNull(fileSha256)
        val request = FullFdtpAFTRequest(
            stream = stream,
            artifactFile = ArtifactFileFactory.buildChunked(),
            headers = headers.headers(),
            channel = ctx.channel(),
            startTime = System.currentTimeMillis(),
        )
        requestMap[streamId] = request
    }

    private fun onDataRead(ctx: ChannelHandlerContext, data: FdtpDataFrame) {
        val streamId = data.stream()!!.id()
        val requestContext = requestMap[streamId]
        checkNotNull(requestContext) { "Unexpected message received:$data" }
        with(requestContext) {
            val bytes = readBytes(data)
            (artifactFile as ChunkedArtifactFile).write(bytes)
            if (data.isEndStream()) {
                requestMap.remove(streamId)
                artifactFile.finish()
                var response = FdtpResponseStatus.OK
                try {
                    response = requestHandler.handler(this)
                    sendResponse(ctx, data.stream()!!, response, headers)
                } finally {
                    logAccess(this, response)
                    removeRequestContext(requestContext)
                }
            }
        }
    }

    private fun sendResponse(
        ctx: ChannelHandlerContext,
        stream: FdtpFrameStream,
        status: FdtpResponseStatus,
        headers: FdtpHeaders?,
    ) {
        val streamId = stream.id()
        val responseHeaders = DefaultFdtpHeaders()
        responseHeaders.add(FdtpHeaderNames.FDTP_VERSION, FdtpVersion.FDTP_1_0.text())
        responseHeaders.add(FdtpHeaderNames.STATUS, status.code.toString())
        responseHeaders.add(FdtpHeaderNames.REASON_PHRASE, status.reasonPhrase)
        responseHeaders.add(FdtpHeaderNames.STREAM_ID, streamId.toString())
        headers?.iteratorCharSequence()?.forEach {
            responseHeaders.add(it.key.toString(), it.value.toString())
        }
        val headerFrame = DefaultFdtpHeaderFrame(responseHeaders, true).stream(stream)
        ctx.writeAndFlush(headerFrame)
    }

    private fun readBytes(msg: FdtpDataFrame): ByteArray {
        val content = msg.content()
        val data = ByteArray(content.readableBytes())
        content.readBytes(data)
        return data
    }

    private fun removeRequestContext(request: FullFdtpAFTRequest) {
        requestMap.remove(request.stream.id())
        val tempFile = request.artifactFile
        if (!tempFile.isInMemory()) {
            val absolutePath = tempFile.getFile()!!.absolutePath
            measureTimeMillis { tempFile.delete() }.apply {
                logger.info("Delete temp artifact file [$absolutePath] success, elapse $this ms")
            }
        }
    }

    private fun close(ctx: ChannelHandlerContext) {
        ctx.close()
        requestMap.values.forEach { removeRequestContext(it) }
    }

    private fun logAccess(request: FullFdtpAFTRequest, responseStatus: FdtpResponseStatus) {
        val remoteAddress = request.channel.remoteAddress() as InetSocketAddress
        val remoteIp = remoteAddress.address.hostAddress
        val accessTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val bodySize = request.artifactFile.getSize()
        val elapse = System.currentTimeMillis() - request.startTime
        LoggerHolder.accessLogger.info(
            "$remoteIp - - [$accessTime]" +
                " FDTP-AFT ${responseStatus.code} $bodySize $elapse ms",
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FdtpAFTServerHandler::class.java)
    }
}
