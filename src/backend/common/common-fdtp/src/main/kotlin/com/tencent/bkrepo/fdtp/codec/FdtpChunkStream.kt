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

package com.tencent.bkrepo.fdtp.codec

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.stream.ChunkedInput

/**
 * fdtp分块流
 * */
class FdtpChunkStream(val input: ChunkedInput<ByteBuf>, val stream: FdtpFrameStream) : ChunkedInput<FdtpDataFrame> {
    private var endStreamSent: Boolean = false
    override fun isEndOfInput(): Boolean {
        return input.isEndOfInput
    }

    override fun close() {
        input.close()
    }

    override fun readChunk(ctx: ChannelHandlerContext): FdtpDataFrame? {
        return readChunk(ctx.alloc())
    }

    override fun readChunk(allocator: ByteBufAllocator): FdtpDataFrame? {
        if (endStreamSent) {
            return null
        }
        if (input.isEndOfInput) {
            endStreamSent = true
            return DefaultFdtpDataFrame(true).stream(stream)
        }
        val buf = input.readChunk(allocator) ?: return null
        val dataFrame = DefaultFdtpDataFrame(buf, input.isEndOfInput).stream(stream)
        if (dataFrame.isEndStream()) {
            endStreamSent = true
        }
        return dataFrame
    }

    override fun length(): Long {
        return input.length()
    }

    override fun progress(): Long {
        return input.progress()
    }
}
