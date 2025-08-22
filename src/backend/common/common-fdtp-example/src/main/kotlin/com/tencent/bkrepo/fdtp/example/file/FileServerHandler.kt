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

package com.tencent.bkrepo.fdtp.example.file

import com.tencent.bkrepo.fdtp.FdtpCodecUtil
import com.tencent.bkrepo.fdtp.FdtpError
import com.tencent.bkrepo.fdtp.FdtpVersion
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpDataFrame
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpHeaderFrame
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpHeaders
import com.tencent.bkrepo.fdtp.codec.FdtpDataFrame
import com.tencent.bkrepo.fdtp.codec.FdtpFrameStream
import com.tencent.bkrepo.fdtp.codec.FdtpHeaderFrame
import com.tencent.bkrepo.fdtp.codec.FdtpHeaderNames
import com.tencent.bkrepo.fdtp.codec.FdtpResponseStatus
import com.tencent.bkrepo.fdtp.codec.FdtpStreamFrame
import com.tencent.bkrepo.udt.StatusUDT
import com.tencent.bkrepo.udt.netty.transport.nio.NioUdtProvider
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class FileServerHandler : SimpleChannelInboundHandler<FdtpStreamFrame>() {
    private var dstFilePath: Path? = null
    private var outputStream: OutputStream? = null
    override fun channelActive(ctx: ChannelHandlerContext) {
        logger.info("new connection ${NioUdtProvider.socketUDT(ctx.channel())}")
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val socket = NioUdtProvider.socketUDT(ctx.channel())
        if (socket?.status() == StatusUDT.NONEXIST) {
            logger.info("Peer close connection $socket")
        } else {
            logger.error("exceptionCaught $socket", cause)
        }
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FdtpStreamFrame) {
        if (msg is FdtpHeaderFrame) {
            onHeadersRead(ctx, msg)
        }
        if (msg is FdtpDataFrame) {
            onDataRead(ctx, msg)
        }
    }

    private fun onHeadersRead(ctx: ChannelHandlerContext, headers: FdtpHeaderFrame) {
        val dstPath = headers.headers().get("dst-path") ?: throw FdtpError("Missing dst path param")
        dstFilePath = Paths.get(dstPath)
        if (Files.exists(dstFilePath!!)) {
            Files.deleteIfExists(dstFilePath!!)
        }
        outputStream = Files.newOutputStream(dstFilePath!!)
    }

    private fun onDataRead(ctx: ChannelHandlerContext, data: FdtpDataFrame) {
        val bytes = readBytes(data)
        outputStream!!.write(bytes)
        if (data.isEndStream()) {
            val size = Files.size(dstFilePath!!)
            val payload = Unpooled.wrappedBuffer(FdtpCodecUtil.longToByteArray(size))
            sendResponse(ctx, data.stream()!!, payload)
        }
    }

    private fun sendResponse(
        ctx: ChannelHandlerContext,
        stream: FdtpFrameStream,
        payload: ByteBuf,
    ) {
        val streamId = stream.id()
        val headers = DefaultFdtpHeaders()
        headers.add(FdtpHeaderNames.FDTP_VERSION, FdtpVersion.FDTP_1_0.text())
        headers.add(FdtpHeaderNames.STATUS, FdtpResponseStatus.OK.code.toString())
        headers.add(FdtpHeaderNames.STREAM_ID, streamId.toString())
        val headerFrame = DefaultFdtpHeaderFrame(headers).stream(stream)
        ctx.writeAndFlush(headerFrame)
        val dataFrame = DefaultFdtpDataFrame(payload, true).stream(stream)
        ctx.writeAndFlush(dataFrame)
        logger.info("[$streamId] Success receive file $dstFilePath")
    }

    private fun readBytes(msg: FdtpDataFrame): ByteArray {
        val content = msg.content()
        val data = ByteArray(content.readableBytes())
        content.readBytes(data)
        return data
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(FileServerHandler::class.java)
    }
}
