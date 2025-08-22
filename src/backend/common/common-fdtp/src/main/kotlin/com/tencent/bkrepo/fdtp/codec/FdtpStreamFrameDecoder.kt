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

import com.tencent.bkrepo.fdtp.FdtpError
import com.tencent.bkrepo.fdtp.FdtpCodecUtil
import com.tencent.bkrepo.fdtp.FdtpCodecUtil.MAX_FRAME_SIZE
import com.tencent.bkrepo.fdtp.codec.FdtpFrameType.DATA
import com.tencent.bkrepo.fdtp.codec.FdtpFrameType.HEADER
import com.tencent.bkrepo.fdtp.codec.FdtpStreamFrameDecoder.State
import com.tencent.bkrepo.fdtp.codec.FdtpStreamFrameDecoder.State.READ_HEADER_FRAME
import com.tencent.bkrepo.fdtp.codec.FdtpStreamFrameDecoder.State.READ_PAYLOAD_FRAME
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ReplayingDecoder
import java.util.concurrent.ConcurrentHashMap

/**
 * fdtp framde解码器
 * */
abstract class FdtpStreamFrameDecoder : ReplayingDecoder<State>(READ_HEADER_FRAME) {

    private var payloadLength: Int? = null
    private var frameType: Byte? = null
    private var flags: FdtpFlags? = null
    private var streamId: Int? = null

    // todo 更好的实现方式，减少内存或者内存安全
    private var streamMap = ConcurrentHashMap<Int, FdtpFrameStream>()

    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        when (state()) {
            READ_HEADER_FRAME -> {
                if (buf.readableBytes() < FdtpCodecUtil.FRAME_HEADER_LENGTH) {
                    return
                }
                payloadLength = buf.readUnsignedMedium()
                if (payloadLength!! > MAX_FRAME_SIZE) {
                    throw FdtpError("Payload length exceed max size $MAX_FRAME_SIZE")
                }
                frameType = buf.readByte()
                flags = FdtpFlags(buf.readUnsignedByte())
                streamId = FdtpCodecUtil.readUnsignedInt(buf)
                streamMap.getOrPut(streamId) { DefaultFdtpFrameStream(streamId!!) }
                // empty data frame,no more data can read at next time
                if (payloadLength == 0 && frameType == DATA) {
                    val dataFrame = DefaultFdtpDataFrame(Unpooled.EMPTY_BUFFER, flags!!.endOfStream())
                    val stream = streamMap[streamId]!!
                    dataFrame.stream(stream)
                    out.add(dataFrame)
                    resetNow()
                } else {
                    checkpoint(READ_PAYLOAD_FRAME)
                }
            }

            READ_PAYLOAD_FRAME -> {
                val payload = buf.readRetainedSlice(payloadLength!!)
                val stream = streamMap[streamId] ?: throw FdtpError("Unexpected fdtp frame data")
                when (frameType) {
                    DATA -> {
                        val dataFrame = DefaultFdtpDataFrame(payload, flags!!.endOfStream())
                        dataFrame.stream(stream)
                        out.add(dataFrame)
                    }

                    HEADER -> {
                        val headersBytes = ByteArray(payload.readableBytes())
                        payload.readBytes(headersBytes)
                        val headersMap = FdtpCodecUtil.unMarshalHeaders(headersBytes)
                        val headers = DefaultFdtpHeaders()
                        headersMap.forEach { (key, value) ->
                            headers.add(key, value)
                        }
                        val headerFrame = DefaultFdtpHeaderFrame(headers, flags!!.endOfStream())
                        headerFrame.stream(stream)
                        out.add(headerFrame)
                    }
                }
                resetNow()
            }

            else -> {
                throw FdtpError("Decode header frame error")
            }
        }
    }

    /**
     * 解码器状态
     * */
    enum class State {
        READ_HEADER_FRAME,
        READ_PAYLOAD_FRAME,
    }

    private fun resetNow() {
        payloadLength = null
        frameType = null
        flags = null
        streamMap.remove(streamId)
        streamId = null
        checkpoint(READ_HEADER_FRAME)
    }
}
