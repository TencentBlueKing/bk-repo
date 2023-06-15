/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.udt.netty.transport.nio

import com.tencent.bkrepo.udt.TypeUDT
import com.tencent.bkrepo.udt.netty.transport.DefaultUdtChannelConfig
import com.tencent.bkrepo.udt.netty.transport.UdtChannel
import com.tencent.bkrepo.udt.netty.transport.UdtChannelConfig
import com.tencent.bkrepo.udt.nio.ServerSocketChannelUDT
import com.tencent.bkrepo.udt.nio.SocketChannelUDT
import io.netty.channel.ChannelException
import io.netty.channel.ChannelMetadata
import io.netty.channel.ChannelOutboundBuffer
import io.netty.channel.nio.AbstractNioMessageChannel
import io.netty.util.internal.SocketUtils
import io.netty.util.internal.logging.InternalLoggerFactory
import java.net.SocketAddress
import java.nio.channels.SelectionKey

abstract class NioUdtAcceptorChannel(type: TypeUDT) :
    AbstractNioMessageChannel(null, NioUdtProvider.newAcceptorChannelUDT(type), SelectionKey.OP_ACCEPT),
    UdtChannel {

    private val config: UdtChannelConfig

    init {
        val channelUDT = super.javaChannel() as ServerSocketChannelUDT
        try {
            channelUDT.configureBlocking(false)
            config = DefaultUdtChannelConfig(this, channelUDT, true)
        } catch (e: Exception) {
            try {
                channelUDT.close()
            } catch (e2: Exception) {
                if (logger.isWarnEnabled) {
                    logger.warn("Failed to close channel.", e2)
                }
            }
            throw ChannelException("Failed configure channel.", e)
        }
    }

    override fun config(): UdtChannelConfig {
        return config
    }

    override fun isActive(): Boolean {
        return javaChannel().socket().isBound
    }

    override fun localAddress0(): SocketAddress {
        return javaChannel().socket().localSocketAddress
    }

    override fun remoteAddress0(): SocketAddress? {
        return null
    }

    override fun doBind(localAddress: SocketAddress) {
        javaChannel().socket().bind(localAddress, config.getBacklog())
    }

    override fun doDisconnect() {
        throw UnsupportedOperationException()
    }

    override fun doConnect(remoteAddress: SocketAddress?, localAddress: SocketAddress?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun doFinishConnect() {
        throw UnsupportedOperationException()
    }

    override fun javaChannel(): ServerSocketChannelUDT {
        return super.javaChannel() as ServerSocketChannelUDT
    }

    override fun doWriteMessage(msg: Any?, `in`: ChannelOutboundBuffer?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun metadata(): ChannelMetadata {
        return METADATA
    }

    override fun doReadMessages(buf: MutableList<Any>): Int {
        val channelUDT = SocketUtils.accept(javaChannel()) as SocketChannelUDT
        return if (channelUDT == null) {
            0
        } else {
            buf.add(newConnectorChannel(channelUDT))
            1
        }
    }

    override fun doClose() {
        javaChannel().close()
    }

    protected abstract fun newConnectorChannel(channelUDT: SocketChannelUDT): UdtChannel

    companion object {
        private val logger = InternalLoggerFactory.getInstance(NioUdtAcceptorChannel::class.java)
        private val METADATA = ChannelMetadata(false, 16)
    }
}
