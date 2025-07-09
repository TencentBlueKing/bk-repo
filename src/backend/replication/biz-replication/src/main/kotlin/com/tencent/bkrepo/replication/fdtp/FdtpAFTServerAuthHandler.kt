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
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.slf4j.LoggerFactory

class FdtpAFTServerAuthHandler(val fdtpAuthManager: FdtpAuthManager) : SimpleChannelInboundHandler<ByteBuf>() {
    private var buf: ByteBuf? = null
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        buf = ctx.alloc().buffer(MAX_TOKEN_LENGTH)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        buf?.release()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.info("Failed auth ${ctx.channel()},will close connection", cause)
        ctx.writeAndFlush(AUTH_FAILED_DATA.duplicate())
        ctx.close()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        msg.markReaderIndex()
        val widx = msg.writerIndex()
        val realWidx = widx - DELIMITER_DATA_LENGTH
        msg.readerIndex(realWidx)
        val delimiterBytes = ByteArray(DELIMITER_DATA_LENGTH)
        msg.readBytes(delimiterBytes)
        if (delimiterBytes.contentEquals(DELIMITER_DATA)) {
            msg.resetReaderIndex()
            msg.writerIndex(realWidx)
            buf!!.writeBytes(msg)
            // token末尾，进行校验token
            val tokenBytes = ByteArray(buf!!.readableBytes())
            buf!!.readBytes(tokenBytes)
            val token = String(tokenBytes)
            fdtpAuthManager.verifySecurityToken(token)
            logger.info("Success auth,will remove $SERVER_AUTH_HANDLER_NAME handler.")
            ctx.writeAndFlush(AUTH_SUCCESS_DATA.duplicate())
            ctx.pipeline().remove(this)
        } else {
            buf!!.writeBytes(msg)
        }
    }

    companion object {
        const val MAX_TOKEN_LENGTH = 1000
        val DELIMITER_DATA = "\r\n".toByteArray()
        val DELIMITER_DATA_LENGTH = DELIMITER_DATA.size
        val AUTH_SUCCESS = 0.toByte()
        val AUTH_FAILED = 1.toByte()
        private val SERVER_AUTH_HANDLER_NAME = FdtpAFTServerAuthHandler::class.java.simpleName
        private fun unreleasableAndReadOnlyBuffer(buf: ByteBuf): ByteBuf {
            return Unpooled.unreleasableBuffer((buf).asReadOnly())
        }

        val AUTH_FAILED_DATA = unreleasableAndReadOnlyBuffer(Unpooled.wrappedBuffer(byteArrayOf(AUTH_FAILED)))
        val AUTH_SUCCESS_DATA = unreleasableAndReadOnlyBuffer(Unpooled.wrappedBuffer(byteArrayOf(AUTH_SUCCESS)))
        private val logger = LoggerFactory.getLogger(FdtpAFTServerAuthHandler::class.java)
    }
}
