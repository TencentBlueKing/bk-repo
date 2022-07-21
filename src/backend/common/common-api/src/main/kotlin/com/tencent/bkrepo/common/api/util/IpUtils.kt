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

package com.tencent.bkrepo.common.api.util

import sun.net.util.IPAddressUtil

object IpUtils {

    private val cidrIpRegex = Regex(".*/")
    private val cidrMaskRegex = Regex("/.*")

    fun isInRange(ip: String, cidr: String): Boolean {
        val ip1 = ipv4ToLong(ip)
        val cidrIp = cidr.replace(cidrMaskRegex, "")
        val ip2 = ipv4ToLong(cidrIp)
        val maskStr = cidr.replace(cidrIpRegex, "")
        val mask = 0xFFFFFFFF shl (32 - maskStr.toInt())
        return (ip1 and mask) == (ip2 and mask)
    }

    fun ipv4ToLong(ip: String): Long {
        val bytes = IPAddressUtil.textToNumericFormatV4(ip) ?: throw IllegalArgumentException("$ip is invalid")
        var ret = 0L
        bytes.forEach {
            ret = ret shl 8 or (it.toInt() and 0xFF).toLong()
        }
        return ret
    }
}
