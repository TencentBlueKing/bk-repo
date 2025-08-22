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

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * 处理fdtp-aft协议的client认证
 * */
class FdtpAFTClientAuthHandler(val fdtpAuthManager: FdtpAuthManager) : SimpleChannelInboundHandler<ByteBuf>() {

    val latch = CountDownLatch(1)

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        // 发起认证
        val token = fdtpAuthManager.getSecurityToken()
        val tokenBytes = token.toByteArray() + FdtpAFTServerAuthHandler.DELIMITER_DATA
        val tokenBuf = ctx.alloc().buffer(tokenBytes.size)
        tokenBuf.writeBytes(tokenBytes)
        ctx.writeAndFlush(tokenBuf)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        // 认证结果处理
        if (msg.readableBytes() > 1) {
            logger.error("Unexpected response size ${msg.readableBytes()}")
            ctx.close()
            return
        }
        when (val res = msg.readByte()) {
            FdtpAFTServerAuthHandler.AUTH_SUCCESS -> {
                logger.info("Success auth,will remove ${this.javaClass.simpleName} handler")
                ctx.pipeline().remove(this)
                latch.countDown()
            }

            FdtpAFTServerAuthHandler.AUTH_FAILED -> {
                logger.error("Failed auth,will close connection.")
                ctx.close()
            }

            else -> {
                logger.error("Unexpected response $res")
                ctx.close()
            }
        }
    }

    fun responseSuccessfullyCompleted(): Boolean {
        return try {
            latch.await(MAX_RESPONSE_WAIT_TIME_IN_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            println("Latch exception: $e.message")
            false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FdtpAFTClientAuthHandler::class.java)
        private const val MAX_RESPONSE_WAIT_TIME_IN_SECONDS = 5L
    }
}
