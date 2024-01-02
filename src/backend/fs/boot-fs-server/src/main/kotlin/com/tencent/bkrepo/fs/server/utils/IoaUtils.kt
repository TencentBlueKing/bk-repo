/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.fs.server.utils

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.hash.sha256
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.fs.server.config.propteries.IoaProperties
import com.tencent.bkrepo.fs.server.request.IoaLoginRequest
import com.tencent.bkrepo.fs.server.request.IoaTicketRequest
import com.tencent.bkrepo.fs.server.response.IoaTicketResponse
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.resolver.HostsFileEntriesResolver
import io.netty.resolver.ResolvedAddressTypes
import io.netty.resolver.dns.DnsAddressResolverGroup
import io.netty.resolver.dns.DnsNameResolverBuilder
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.net.InetAddress
import java.time.Duration
import kotlin.random.Random


@EnableConfigurationProperties(IoaProperties::class)
class IoaUtils(
    ioaProperties: IoaProperties
) {

    init {
        Companion.ioaProperties = ioaProperties
    }

    companion object {
        private val logger = LoggerFactory.getLogger(IoaUtils::class.java)
        private lateinit var ioaProperties: IoaProperties

        private val httpClient by lazy {
            val hostsFileEntriesResolver = HostsFileEntriesResolver { inetHost, types ->
                val ipv4 = types == ResolvedAddressTypes.IPV4_ONLY || types == ResolvedAddressTypes.IPV4_PREFERRED
                if (ioaProperties.ticketUrl.contains(inetHost) && ipv4 && ioaProperties.ticketIp.isNotBlank()) {
                    InetAddress.getByName(ioaProperties.ticketIp)
                } else {
                    null
                }
            }
            val resolver = DnsAddressResolverGroup(DnsNameResolverBuilder()
                .eventLoop(NioEventLoopGroup().next())
                .channelFactory { io.netty.channel.socket.nio.NioDatagramChannel() }
                .hostsFileEntriesResolver(hostsFileEntriesResolver)
            )

            val provider = ConnectionProvider.builder("IOA").maxIdleTime(Duration.ofSeconds(30L)).build()
            val client = HttpClient.create(provider).responseTimeout(Duration.ofSeconds(30L)).resolver(resolver)
            val connector = ReactorClientHttpConnector(client)
            WebClient.builder().clientConnector(connector).build()
        }

        suspend fun checkTicket(ioaLoginRequest: IoaLoginRequest) {
            val timestamp = (System.currentTimeMillis() / 1000L).toString()
            val random = Random.nextInt(1000, 9999).toString()
            val appId = ioaProperties.appId
            val appKey = ioaProperties.appKey

            val values = with(ioaLoginRequest) {
                IoaTicketRequest(
                    username = userName,
                    appId = appId,
                    appName = appName,
                    appVersion = appVer,
                    st = ticket,
                    reqUrl = ioaProperties.requestUrl,
                    deviceId = deviceId
                )
            }

            val jsonBodyValue = values.toJsonString()
            val signOrigin = "$timestamp&$random&$appKey&$jsonBodyValue"
            val sSignature = signOrigin.sha256()

            val headers = mutableMapOf(
                HttpHeaders.CONTENT_TYPE to MediaTypes.APPLICATION_JSON,
                HttpHeaders.HOST to ioaProperties.host,
                "STimestamp" to timestamp,
                "SRandom" to random,
                "SAppId" to appId,
                "SSignature" to sSignature,
            )
            if (ioaProperties.token.isNotBlank()) {
                headers["timestamp"] = timestamp
                headers["signature"] = "$timestamp${ioaProperties.token}$timestamp".sha256()
            }

            val response = httpClient.post()
                .uri(ioaProperties.ticketUrl)
                .headers { httpHeaders -> headers.forEach { httpHeaders.add(it.key, it.value) } }
                .bodyValue(jsonBodyValue)
                .retrieve()
                .bodyToMono(String::class.java)
                .awaitSingle()

            val ioaTicketResponse = response.readJsonString<IoaTicketResponse>()
            if (ioaTicketResponse.ret != 0) {
                logger.info("ioa ticket check failed with $ioaLoginRequest, $ioaTicketResponse")
                throw AuthenticationException("Check ticket failed: $ioaTicketResponse")
            }
        }

        suspend fun proxyTicketRequest(request: ServerRequest): IoaTicketResponse {
            val ioaTicketRequest = request.bodyToMono(String::class.java).awaitSingle()
            val headers = request.headers().asHttpHeaders().toSingleValueMap().toMutableMap()
            headers[HttpHeaders.HOST] = ioaProperties.host
            val response = httpClient.post()
                .uri(ioaProperties.ticketUrl)
                .headers { it.setAll(headers) }
                .bodyValue(ioaTicketRequest)
                .retrieve()
                .bodyToMono(String::class.java)
                .awaitSingle()
            val ioaTicketResponse = response.readJsonString<IoaTicketResponse>()
            if (ioaTicketResponse.ret != 0) {
                logger.info("ioa ticket check failed with $ioaTicketRequest, $headers, $ioaTicketResponse")
            }
            return ioaTicketResponse
        }
    }
}
