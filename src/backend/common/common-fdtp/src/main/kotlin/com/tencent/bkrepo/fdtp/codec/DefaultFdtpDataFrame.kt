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
import io.netty.buffer.Unpooled

/**
 * 默认的数据帧实现
 * */
class DefaultFdtpDataFrame(val content: ByteBuf, val endStream: Boolean) : AbstractFdtpStreamFrame(), FdtpDataFrame {

    constructor(content: ByteBuf) : this(content, false)
    constructor(endStream: Boolean) : this(Unpooled.EMPTY_BUFFER, endStream)

    override fun content(): ByteBuf {
        return content
    }

    override fun isEndStream(): Boolean {
        return endStream
    }

    override fun stream(stream: FdtpFrameStream): DefaultFdtpDataFrame {
        super.stream(stream)
        return this
    }

    override fun name(): String {
        return "DATA"
    }

    override fun refCnt(): Int {
        return content.refCnt()
    }

    override fun retain(): DefaultFdtpDataFrame {
        content.retain()
        return this
    }

    override fun retain(increment: Int): DefaultFdtpDataFrame {
        content.retain(increment)
        return this
    }

    override fun touch(): DefaultFdtpDataFrame {
        content.touch()
        return this
    }

    override fun touch(hint: Any): DefaultFdtpDataFrame {
        content.touch(hint)
        return this
    }

    override fun copy(): DefaultFdtpDataFrame {
        return replace(content.copy())
    }

    override fun duplicate(): DefaultFdtpDataFrame {
        return replace(content.duplicate())
    }

    override fun retainedDuplicate(): DefaultFdtpDataFrame {
        return replace(content.retainedDuplicate())
    }

    override fun replace(content: ByteBuf): DefaultFdtpDataFrame {
        return DefaultFdtpDataFrame(content)
    }

    override fun release(): Boolean {
        return content.release()
    }

    override fun release(decrement: Int): Boolean {
        return content.release(decrement)
    }

    override fun toString(): String {
        return "${this.javaClass.simpleName} (stream=${stream()}, " +
            "content=$content, endStream=$endStream)"
    }
}
