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

import com.tencent.bkrepo.fdtp.FdtpCodecUtil
import com.tencent.bkrepo.fdtp.FdtpCodecUtil.FRAME_HEADER_LENGTH
import com.tencent.bkrepo.fdtp.codec.FdtpFrameType.DATA
import com.tencent.bkrepo.fdtp.codec.FdtpFrameType.HEADER
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

/**
 * fdtp编码器
 * */
open class FdtpStreamFrameEncoder : MessageToByteEncoder<FdtpStreamFrame>() {

    override fun encode(ctx: ChannelHandlerContext, msg: FdtpStreamFrame, out: ByteBuf) {
        val streamId = msg.stream()?.id() ?: -1
        val frameHeader = ctx.alloc().buffer(FRAME_HEADER_LENGTH)
        // todo exceed max frame size
        try {
            if (msg is FdtpHeaderFrame) {
                val headers = mutableMapOf<String, String>()
                val it = msg.headers().iteratorCharSequence()
                while (it.hasNext()) {
                    val next = it.next()
                    headers[next.key.toString()] = next.value.toString()
                }
                val payload = FdtpCodecUtil.marshalHeaders(headers)
                val flags = FdtpFlags()
                flags.endOfStream(msg.isEndStream())
                writeFrameHeaderInternal(frameHeader, payload.size, HEADER, flags, streamId)
                out.writeBytes(frameHeader)
                out.writeBytes(payload)
            } else if (msg is FdtpDataFrame) {
                val payload = msg.content()
                val flags = FdtpFlags()
                flags.endOfStream(msg.isEndStream())
                writeFrameHeaderInternal(frameHeader, payload.readableBytes(), DATA, flags, streamId)
                out.writeBytes(frameHeader)
                out.writeBytes(payload)
            }
        } finally {
            frameHeader.release()
        }
    }

    private fun writeFrameHeaderInternal(
        out: ByteBuf,
        payloadLength: Int,
        type: Byte,
        flags: FdtpFlags,
        streamId: Int,
    ) {
        // payloadLength(3B)+type(1B)+flag(1B)+streamId(4B)
        out.writeMedium(payloadLength)
        out.writeByte(type.toInt())
        out.writeByte(flags.value().toInt())
        out.writeInt(streamId)
    }
}
