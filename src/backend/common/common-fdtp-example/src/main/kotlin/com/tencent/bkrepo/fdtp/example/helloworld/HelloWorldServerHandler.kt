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

package com.tencent.bkrepo.fdtp.example.helloworld

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
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent

class HelloWorldServerHandler : SimpleChannelInboundHandler<FdtpStreamFrame>() {

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val socket = NioUdtProvider.socketUDT(ctx.channel())
        if (socket?.status() == StatusUDT.NONEXIST) {
            println("Peer close connection $socket")
        } else {
            cause.printStackTrace()
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            if (evt.state() == IdleState.ALL_IDLE) {
                ctx.close()
            }
        }
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FdtpStreamFrame) {
        if (msg is FdtpHeaderFrame && msg.isEndStream()) {
            val stream = msg.stream()!!
            sendResponse(ctx, stream, RESPONSE_DATA.duplicate())
        }
        if (msg is FdtpDataFrame && msg.isEndStream()) {
            val stream = msg.stream()!!
            sendResponse(ctx, stream, RESPONSE_DATA.duplicate())
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
    }

    companion object {
        val RESPONSE_DATA =
            Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer("Hello World!".toByteArray()).asReadOnly())
    }
}
