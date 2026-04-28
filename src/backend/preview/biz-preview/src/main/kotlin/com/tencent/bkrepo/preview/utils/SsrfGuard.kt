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

import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewInvalidException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.UnknownHostException

/**
 * 远程预览 SSRF 防护工具
 *
 * 在对外部 URL 发起 HTTP 请求前，对 URL 进行安全校验：
 * - 协议白名单（默认 https）
 * - 端口白名单（默认 80/443）
 * - 域名/IP 白名单（可选，空表示不限制）
 * - 内网 / 环回 / 链路本地 / 多播 / 云元数据 / 保留网段黑名单
 *
 * 同时返回已解析的 IP 列表，调用方在实际发起请求时应传入这些 IP 以抵御 DNS Rebinding。
 */
@Component
class SsrfGuard(private val config: PreviewConfig) {

    companion object {
        private val logger = LoggerFactory.getLogger(SsrfGuard::class.java)

        /**
         * 始终禁止访问的 IPv4 网段（即使配置关闭 blockInternalAddress 也会命中云元数据等）
         */
        private val ALWAYS_BLOCKED_V4_RANGES = listOf(
            // 云元数据 / 链路本地
            "169.254.0.0" to 16,
            // 多播
            "224.0.0.0" to 4,
            // 保留
            "240.0.0.0" to 4,
            // 0.0.0.0/8
            "0.0.0.0" to 8
        )

        /**
         * 当 blockInternalAddress=true 时额外禁止的 IPv4 网段
         */
        private val INTERNAL_V4_RANGES = listOf(
            // 环回
            "127.0.0.0" to 8,
            // RFC1918 私网
            "10.0.0.0" to 8,
            "172.16.0.0" to 12,
            "192.168.0.0" to 16,
            // Carrier-Grade NAT
            "100.64.0.0" to 10,
            // 网络基准 / 测试
            "192.0.0.0" to 24,
            "192.0.2.0" to 24,
            "198.18.0.0" to 15,
            "198.51.100.0" to 24,
            "203.0.113.0" to 24
        )
    }

    /**
     * 校验远程 URL。校验失败会抛出 [PreviewInvalidException]。
     * 校验成功时返回该 URL，供调用方继续使用。
     */
    fun validate(urlStr: String): URL {
        if (!config.isRemotePreviewEnabled) {
            logger.warn("Remote preview is disabled by configuration, reject url: {}", urlStr)
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "url")
        }

        val url = try {
            URI(urlStr).toURL()
        } catch (e: Exception) {
            logger.warn("Invalid remote preview url: {}, err: {}", urlStr, e.message)
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "url")
        }

        // 1. 协议白名单
        val scheme = url.protocol?.lowercase().orEmpty()
        val allowedSchemes = config.remoteAllowedSchemes
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        if (scheme !in allowedSchemes) {
            logger.warn("Remote preview scheme [{}] not in whitelist {}", scheme, allowedSchemes)
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "url")
        }

        // 2. 端口白名单
        val effectivePort = if (url.port == -1) url.defaultPort else url.port
        val allowedPorts = config.remoteAllowedPorts
            .split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        if (effectivePort <= 0 || effectivePort !in allowedPorts) {
            logger.warn("Remote preview port [{}] not in whitelist {}", effectivePort, allowedPorts)
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "url")
        }

        val host = url.host
        if (host.isNullOrBlank()) {
            logger.warn("Remote preview url has no host: {}", urlStr)
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "url")
        }

        // 3. 域名白名单（可选）
        val allowedHosts = config.remoteAllowedHosts
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (allowedHosts.isNotEmpty() && !hostMatches(host.lowercase(), allowedHosts)) {
            logger.warn("Remote preview host [{}] not in whitelist {}", host, allowedHosts)
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "url")
        }

        // 4. 内网 / 保留地址黑名单（基于 DNS 解析后的所有 IP）
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: UnknownHostException) {
            logger.warn("Remote preview host [{}] cannot be resolved", host)
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "url")
        }
        addresses.forEach { addr ->
            if (isForbiddenAddress(addr)) {
                logger.warn("Remote preview host [{}] resolved to forbidden address [{}]", host, addr.hostAddress)
                throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "url")
            }
        }

        return url
    }

    /**
     * 按白名单匹配域名：
     * - 条目以 "." 开头：后缀匹配（如 `.example.com` 匹配 `a.example.com` 与 `example.com`）
     * - 否则：精确匹配
     */
    private fun hostMatches(host: String, allowed: List<String>): Boolean {
        return allowed.any { pattern ->
            if (pattern.startsWith(".")) {
                val suffix = pattern.substring(1)
                host == suffix || host.endsWith(pattern)
            } else {
                host == pattern
            }
        }
    }

    /**
     * 判断 IP 是否属于禁止访问的保留/内网/云元数据等地址。
     */
    private fun isForbiddenAddress(addr: InetAddress): Boolean {
        // 通用判断：回环、链路本地、多播、任意地址
        if (addr.isLoopbackAddress || addr.isLinkLocalAddress
            || addr.isMulticastAddress || addr.isAnyLocalAddress) {
            return true
        }
        if (config.isBlockInternalAddress && addr.isSiteLocalAddress) {
            return true
        }
        return when (addr) {
            is Inet4Address -> isForbiddenV4(addr)
            is Inet6Address -> isForbiddenV6(addr)
            else -> false
        }
    }

    private fun isForbiddenV4(addr: Inet4Address): Boolean {
        if (ALWAYS_BLOCKED_V4_RANGES.any { (cidrAddr, prefix) -> inCidr(addr, cidrAddr, prefix) }) {
            return true
        }
        if (config.isBlockInternalAddress
            && INTERNAL_V4_RANGES.any { (cidrAddr, prefix) -> inCidr(addr, cidrAddr, prefix) }) {
            return true
        }
        return false
    }

    private fun isForbiddenV6(addr: Inet6Address): Boolean {
        if (!config.isBlockInternalAddress) {
            return false
        }
        // ::1 已被 isLoopbackAddress 拦截；fe80::/10 已被 isLinkLocalAddress 拦截
        // 这里补充 ULA（fc00::/7） 与 IPv4-mapped v6（::ffff:0:0/96）
        val bytes = addr.address
        // fc00::/7 —— 首字节高 7 位为 1111 110
        if ((bytes[0].toInt() and 0xFE) == 0xFC) {
            return true
        }
        // IPv4-mapped：前 80 位 0、接下来 16 位 1，再映射 IPv4
        val isV4Mapped = (0..9).all { bytes[it].toInt() == 0 }
            && bytes[10].toInt() and 0xFF == 0xFF
            && bytes[11].toInt() and 0xFF == 0xFF
        if (isV4Mapped) {
            val mappedV4 = InetAddress.getByAddress(bytes.copyOfRange(12, 16)) as Inet4Address
            return isForbiddenV4(mappedV4)
        }
        return false
    }

    /**
     * 判断一个 IPv4 是否落在指定 CIDR 段内。
     */
    private fun inCidr(addr: Inet4Address, cidrAddr: String, prefix: Int): Boolean {
        val target = ipv4ToInt(addr.address)
        val base = ipv4ToInt(InetAddress.getByName(cidrAddr).address)
        if (prefix == 0) return true
        val mask = (-1 shl (32 - prefix))
        return (target and mask) == (base and mask)
    }

    private fun ipv4ToInt(bytes: ByteArray): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }
}
