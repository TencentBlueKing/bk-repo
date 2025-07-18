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

package com.tencent.bkrepo.common.api.util

import com.tencent.bkrepo.common.api.constant.BASIC_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.StringPool
import java.util.Base64

/**
 * Basic Auth 工具类
 */
object BasicAuthUtils {

    /**
     * basic auth编码
     * @return Basic base64(username:password)
     */
    fun encode(username: String, password: String): String {
        val byteArray = ("$username${StringPool.COLON}$password").toByteArray(Charsets.UTF_8)
        val encodedValue = Base64.getEncoder().encodeToString(byteArray)
        return "$BASIC_AUTH_PREFIX$encodedValue"
    }

    /**
     * basic auth解码
     */
    fun decode(content: String): Pair<String, String> {
        val normalized = content.removePrefix(BASIC_AUTH_PREFIX)
        val decodedHeader = String(Base64.getDecoder().decode(normalized))
        val parts = decodedHeader.split(StringPool.COLON)
        require(parts.size >= 2)
        return Pair(parts[0], parts[1])
    }
}
