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

package com.tencent.bkrepo.replication.fdtp

import com.tencent.bkrepo.common.service.util.okhttp.CertTrustManager
import com.tencent.bkrepo.fdtp.codec.AttributeMapKey
import com.tencent.bkrepo.fdtp.codec.DefaultEndpoint
import com.tencent.bkrepo.fdtp.codec.FdtpClientCodec
import io.netty.channel.Channel
import io.netty.channel.pool.ChannelPool
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.stream.ChunkedWriteHandler
import java.io.IOException
import java.net.ConnectException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/**
 * fdtp-aft助手
 *
 * 帮忙一些fdtp-aft的通用管理，例如：stream的创建、channel的初始化，连接的认证管理等等
 * */
object FdtpAFTHelper {

    /**
     * 创建stream
     * */
    fun createStream(channelPool: ChannelPool, certificate: String?, authManager: FdtpAuthManager): FdtpStream {
        var channel: Channel
        var streamId: Int
        do {
            try {
                channel = channelPool.acquire().sync().get()
            } catch (e: IOException) {
                throw ConnectException("connect refuse.")
            }
            streamId = generateStreamId(channel)
            // stream id小于0的不再使用，等待空闲自动关闭
        } while (streamId < 0)
        if (!channel.hasAttr(AttributeMapKey.AUTHENTICATED_KEY)) {
            // 新连接进行配置
            configChannel(channel, certificate, authManager)
        }
        channelPool.release(channel)
        return FdtpStream(channel, streamId)
    }

    /**
     * 配置channel
     *
     * 进行channel认证和基础配置
     * */
    private fun configChannel(channel: Channel, certificate: String?, authManager: FdtpAuthManager) {
        configSsl(channel, certificate)
        val authHandler = FdtpAFTClientAuthHandler(authManager)
        val pipeline = channel.pipeline()
        pipeline.addLast(authHandler)
        if (!authHandler.responseSuccessfullyCompleted()) {
            channel.close()
            error("Failed to server auth,close connection $channel")
        }
        channel.attr(AttributeMapKey.AUTHENTICATED_KEY).set(true)
        pipeline.addLast(FdtpClientCodec())
            .addLast(ChunkedWriteHandler())
            .addLast(FdtpAFTClientHandler())
    }

    private fun configSsl(channel: Channel, certificate: String?) {
        certificate ?: return
        val sslContext = buildSslContext(certificate)
        channel.pipeline().addLast(sslContext.newHandler(channel.alloc()))
    }

    @Throws(CertificateException::class, SSLException::class)
    fun buildSslContext(certificate: String): SslContext {
        val trustManager = CertTrustManager.createTrustManager(certificate)
        return SslContextBuilder
            .forClient().trustManager(trustManager)
            .build()
    }

    private fun generateStreamId(channel: Channel): Int {
        synchronized(channel) {
            val endpoint = if (channel.hasAttr(AttributeMapKey.CLIENT_ENDPOINT_KEY)) {
                channel.attr(AttributeMapKey.CLIENT_ENDPOINT_KEY).get()
            } else {
                val defaultEndpoint = DefaultEndpoint(false)
                channel.attr(AttributeMapKey.CLIENT_ENDPOINT_KEY).setIfAbsent(defaultEndpoint)
                defaultEndpoint
            }
            return endpoint.incrementAndGetNextStreamId()
        }
    }
}
