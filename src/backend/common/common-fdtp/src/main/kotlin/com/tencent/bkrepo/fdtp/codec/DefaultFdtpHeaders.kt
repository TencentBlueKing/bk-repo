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

import io.netty.handler.codec.CharSequenceValueConverter
import io.netty.handler.codec.DefaultHeaders
import io.netty.handler.codec.DefaultHeadersImpl
import io.netty.util.AsciiString.CASE_INSENSITIVE_HASHER

/**
 * 默认的fdtp头实现
 * */
class DefaultFdtpHeaders : FdtpHeaders {

    private val headers = DefaultHeadersImpl(
        CASE_INSENSITIVE_HASHER,
        CharSequenceValueConverter(),
        NOT_NULL,
    )

    override fun get(key: String): String? {
        return headers.get(key)?.toString()
    }

    override fun add(key: String, value: String) {
        headers.add(key, value)
    }

    override fun iteratorCharSequence(): Iterator<Map.Entry<CharSequence, CharSequence>> {
        return headers.iterator()
    }

    override fun toString(): String {
        return headers.toString()
    }

    companion object {
        private val NOT_NULL = DefaultHeaders.NameValidator<CharSequence> {
            check(it != null) { "key can't be null" }
        }
    }
}
