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

import com.tencent.bkrepo.fdtp.codec.FdtpDataFrame
import com.tencent.bkrepo.fdtp.codec.FdtpHeaderFrame
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.MDC

/**
 * 支持链路追踪
 * */
class FdtpTracingHandler : ChannelInboundHandlerAdapter() {
    private val traceIdMap = mutableMapOf<Int, String>()
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        var traceId: String? = null
        if (msg is FdtpHeaderFrame) {
            val streamId = msg.stream()!!.id()
            traceId = msg.headers().get(TRACE_ID).orEmpty()
            traceIdMap[streamId] = traceId
        }
        if (msg is FdtpDataFrame) {
            val streamId = msg.stream()!!.id()
            traceId = traceIdMap[streamId].orEmpty()
        }
        MDC.put(TRACE_ID, traceId)
        super.channelRead(ctx, msg)
        MDC.remove(TRACE_ID)
        if (msg is FdtpHeaderFrame && msg.isEndStream()) {
            val streamId = msg.stream()!!.id()
            traceIdMap.remove(streamId)
        }
        if (msg is FdtpDataFrame && msg.isEndStream()) {
            val streamId = msg.stream()!!.id()
            traceIdMap.remove(streamId)
        }
    }
}
