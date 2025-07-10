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

package com.tencent.bkrepo.replication.fdtp

import com.tencent.bkrepo.udt.StatusUDT
import com.tencent.bkrepo.fdtp.codec.FdtpHeaderFrame
import com.tencent.bkrepo.fdtp.codec.FdtpHeaderNames
import com.tencent.bkrepo.fdtp.codec.FdtpResponseStatus
import com.tencent.bkrepo.fdtp.codec.FdtpStreamFrame
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import com.tencent.bkrepo.udt.netty.transport.nio.NioUdtProvider
import io.netty.util.concurrent.DefaultPromise
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class FdtpAFTClientHandler : SimpleChannelInboundHandler<FdtpStreamFrame>() {
    val streamIdPromiseMap = ConcurrentHashMap<Int, DefaultPromise<FullFdtpAFTResponse>>()

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val socket = NioUdtProvider.socketUDT(ctx.channel())
        if (socket?.status() == StatusUDT.NONEXIST) {
            logger.info("Peer close connection $socket")
        } else {
            logger.error("exceptionCaught $socket", cause)
        }
        close(ctx)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FdtpStreamFrame) {
        if (msg is FdtpHeaderFrame && msg.isEndStream()) {
            val stream = msg.stream()
            val streamId = stream!!.id()
            val promise = streamIdPromiseMap[streamId]
            streamIdPromiseMap.remove(streamId)
            checkNotNull(promise) { "Unexpected message received: $msg" }
            val headers = msg.headers()
            val code = headers.get(FdtpHeaderNames.STATUS)?.toInt()
            checkNotNull(code) { "Response missing code field" }
            val reasonPhrase = headers.get(FdtpHeaderNames.REASON_PHRASE).orEmpty()
            val status = FdtpResponseStatus(code, reasonPhrase)
            if (status == FdtpResponseStatus.OK) {
                val response = FullFdtpAFTResponse(stream, headers, status)
                promise.trySuccess(response)
            } else {
                promise.tryFailure(IllegalStateException("Failed to send file: ${status.reasonPhrase}"))
            }
        }
    }

    private fun close(ctx: ChannelHandlerContext) {
        ctx.close()
        streamIdPromiseMap.values.forEach { it.tryFailure(IllegalStateException("Closed connection.")) }
    }

    fun put(streamId: Int, promise: DefaultPromise<FullFdtpAFTResponse>) {
        streamIdPromiseMap[streamId] = promise
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FdtpAFTClientHandler::class.java)
    }
}
