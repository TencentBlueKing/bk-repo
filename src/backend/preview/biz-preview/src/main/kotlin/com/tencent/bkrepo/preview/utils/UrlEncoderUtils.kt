/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.preview.utils

import java.util.BitSet

/**
 * url编码工具
 */
object UrlEncoderUtils {

    // BitSet is initialized with 256 size to track characters that do not need encoding
    private val dontNeedEncoding = BitSet(256)

    init {
        // Define the characters that do not need encoding
        for (i in 'a'..'z') dontNeedEncoding.set(i.code)
        for (i in 'A'..'Z') dontNeedEncoding.set(i.code)
        for (i in '0'..'9') dontNeedEncoding.set(i.code)
        dontNeedEncoding.set('+'.code) // '+' is considered safe
        dontNeedEncoding.set('-'.code)
        dontNeedEncoding.set('_'.code)
        dontNeedEncoding.set('.'.code)
        dontNeedEncoding.set('*'.code)
    }

    /**
     * 判断str是否urlEncoder.encode过
     * 经常遇到这样的情况，拿到一个URL,但是搞不清楚到底要不要encode.
     * 不做encode吧，担心出错，做encode吧，又怕重复了
     *
     * @param str 输入的字符串
     * @return 是否已经进行过URL编码
     */
    fun hasUrlEncoded(str: String): Boolean {
        var needEncode = false

        for (i in str.indices) {
            val c = str[i]
            if (dontNeedEncoding[c.toInt()]) {
                continue
            }
            if (c == '%' && i + 2 < str.length) {
                // 判断是否符合urlEncode规范
                val c1 = str[i + 1]
                val c2 = str[i + 2]
                if (isDigit16Char(c1) && isDigit16Char(c2)) {
                    continue
                }
            }
            // 其他字符，肯定需要urlEncode
            needEncode = true
            break
        }

        return !needEncode
    }

    /**
     * 判断字符是否是16进制的字符
     *
     * @param c 字符
     * @return 是否是16进制字符
     */
    private fun isDigit16Char(c: Char): Boolean {
        return c in '0'..'9' || c in 'A'..'F'
    }
}