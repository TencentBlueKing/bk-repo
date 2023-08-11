/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.util

import java.security.MessageDigest
import com.tencent.bk.sdk.crypto.util.SM3Util

object DataDigestUtils {

    /**
     * md5加密字符串
     * md5使用后转成16进制变成32个字节
     */
    private const val HEXNUM = 0xFF
    fun md5FromStr(str: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val result = digest.digest(str.toByteArray())
        return toHex(result)
    }

    fun sm3FromStr(str: String): String {
        val digest = SM3Util.digest(str.toByteArray())
        return toHex(digest)
    }

    fun md5FromByteArray(byteArr: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        val result = digest.digest(byteArr)
        return toHex(result)
    }

    private fun toHex(byteArray: ByteArray): String {
        // 转成16进制后是32字节
        return with(StringBuilder()) {
            byteArray.forEach {
                val hex = it.toInt() and (HEXNUM)
                val hexStr = Integer.toHexString(hex)
                if (hexStr.length == 1) {
                    append("0").append(hexStr)
                } else {
                    append(hexStr)
                }
            }
            toString()
        }
    }

    fun sha1FromStr(str: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val result = digest.digest(str.toByteArray())
        return toHex(result)
    }

    fun sha1FromByteArray(byteArr: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val result = digest.digest(byteArr)
        return toHex(result)
    }

    fun sha256FromByteArray(byteArr: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val result = digest.digest(byteArr)
        return toHex(result)
    }
}
