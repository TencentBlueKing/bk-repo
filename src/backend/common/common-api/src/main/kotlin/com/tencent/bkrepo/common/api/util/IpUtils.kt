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

object IpUtils {

    private val cidrParseRegex = Regex("(.*)/(.*)")
    private val cidrRegex = Regex(
        "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
            "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])/([0-9]|[1-2]\\d|3[0-2])?"
    )

    fun isInRange(ip: String, cidr: String): Boolean {
        val ip1 = ipv4ToLong(ip)
        val (cidrIp, mask) = parseCidr(cidr)
        val ip2 = ipv4ToLong(cidrIp)
        return (ip1 and mask) == (ip2 and mask)
    }

    fun ipv4ToLong(ip: String): Long {
        val bytes = textToNumericFormatV4(ip) ?: throw IllegalArgumentException("$ip is invalid")
        var ret = 0L
        bytes.forEach {
            ret = ret shl 8 or (it.toInt() and 0xFF).toLong()
        }
        return ret
    }

    fun parseCidr(cidr: String): Pair<String, Long> {
        if (!cidrRegex.matches(cidr)) {
            throw IllegalArgumentException("$cidr is invalid")
        }
        val (cidrIp, maskStr) = cidrParseRegex.matchEntire(cidr)?.destructured
            ?: throw IllegalArgumentException("$cidr is invalid")
        val mastInt = maskStr.toInt()
        val mask = 0xFFFFFFFF shl (32 - mastInt)
        return cidrIp to mask
    }

    fun textToNumericFormatV4(ip: String): ByteArray? {
        // 步骤 1: 正则验证 IPv4 格式（禁止主机名，避免触发 DNS）
        if (!isStrictIPv4(ip)) return null

        // 步骤 2: 手动解析为字节数组
        return parseIPv4ToBytes(ip)
    }

    /**
     * 严格匹配 IPv4 格式（纯数字，无 DNS 解析）
     * 规则：
     * - 四段数字，用点分隔
     * - 每段 0-255
     * - 禁止前导零（如 "192.016.0.1" 无效）
     */
    private fun isStrictIPv4(ip: String): Boolean {
        val ipv4Pattern =
            "^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$"
        return ip.matches(ipv4Pattern.toRegex())
    }

    /**
     * 将已验证的 IPv4 字符串转换为字节数组
     */
    private fun parseIPv4ToBytes(ip: String): ByteArray? {
        val parts = ip.split(".")
        if (parts.size != 4) return null

        return try {
            ByteArray(4).apply {
                parts.forEachIndexed { i, part ->
                    val value = part.toInt()
                    if (value !in 0..255) throw NumberFormatException()
                    this[i] = value.toByte()
                }
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

}
