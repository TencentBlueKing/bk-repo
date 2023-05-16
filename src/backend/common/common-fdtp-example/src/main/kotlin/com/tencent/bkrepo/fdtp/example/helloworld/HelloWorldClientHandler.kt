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

import com.tencent.bkrepo.fdtp.codec.FdtpDataFrame
import com.tencent.bkrepo.fdtp.codec.FdtpHeaderFrame
import com.tencent.bkrepo.fdtp.codec.FdtpStreamFrame
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HelloWorldClientHandler : SimpleChannelInboundHandler<FdtpStreamFrame>() {
    val latch = CountDownLatch(1)
    override fun channelRead0(ctx: ChannelHandlerContext, msg: FdtpStreamFrame) {
        println(msg)
        if (msg is FdtpHeaderFrame && msg.isEndStream()) {
            latch.countDown()
        }
        if (msg is FdtpDataFrame && msg.isEndStream()) {
            val content = msg.content()
            val bytes = ByteArray(content.readableBytes())
            content.readBytes(bytes)
            println(String(bytes))
            latch.countDown()
        }
    }

    fun responseSuccessfullyCompleted(): Boolean {
        return try {
            latch.await(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            println("Latch exception: $e.message")
            false
        }
    }
}
