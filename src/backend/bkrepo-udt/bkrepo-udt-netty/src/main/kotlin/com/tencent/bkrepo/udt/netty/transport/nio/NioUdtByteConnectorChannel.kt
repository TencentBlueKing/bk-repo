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

import com.tencent.bkrepo.udt.StatusUDT
import com.tencent.bkrepo.udt.TypeUDT
import com.tencent.bkrepo.udt.netty.transport.DefaultUdtChannelConfig
import com.tencent.bkrepo.udt.netty.transport.UdtChannel
import com.tencent.bkrepo.udt.netty.transport.UdtChannelConfig
import com.tencent.bkrepo.udt.nio.SocketChannelUDT
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelException
import io.netty.channel.ChannelFuture
import io.netty.channel.FileRegion
import io.netty.channel.nio.AbstractNioByteChannel
import io.netty.util.internal.SocketUtils
import io.netty.util.internal.logging.InternalLoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.SelectionKey
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

class NioUdtByteConnectorChannel(parent: Channel?, channelUDT: SocketChannelUDT) :
    AbstractNioByteChannel(parent, channelUDT), UdtChannel {

    private val config: UdtChannelConfig

    init {
        try {
            channelUDT.configureBlocking(false)
            config = when (channelUDT.socketUDT().status()) {
                StatusUDT.INIT, StatusUDT.OPENED -> DefaultUdtChannelConfig(this, channelUDT, true)
                else -> DefaultUdtChannelConfig(this, channelUDT, false)
            }
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

    constructor(channelUDT: SocketChannelUDT) : this(null, channelUDT)
    constructor(type: TypeUDT) : this(NioUdtProvider.newConnectorChannelUDT(type))
    constructor() : this(TypeUDT.STREAM)

    override fun config(): UdtChannelConfig {
        return config
    }

    override fun isActive(): Boolean {
        val channelUDT = javaChannel()
        return channelUDT.isOpen && channelUDT.isConnectFinished
    }

    override fun localAddress0(): SocketAddress {
        return javaChannel().socket().localSocketAddress
    }

    override fun remoteAddress0(): SocketAddress {
        return javaChannel().socket().remoteSocketAddress
    }

    override fun doBind(localAddress: SocketAddress) {
        privilegedBind(javaChannel(), localAddress)
    }

    override fun doDisconnect() {
        doClose()
    }

    override fun doClose() {
        javaChannel().close()
    }

    override fun doConnect(remoteAddress: SocketAddress?, localAddress: SocketAddress?): Boolean {
        val bindAddr = localAddress ?: InetSocketAddress(0)
        doBind(bindAddr)
        var success = false
        try {
            val connected = SocketUtils.connect(javaChannel(), remoteAddress)
            if (!connected) {
                selectionKey().interestOps(selectionKey().interestOps() or SelectionKey.OP_CONNECT)
            }
            success = true
            return connected
        } finally {
            if (!success) {
                doClose()
            }
        }
    }

    override fun doFinishConnect() {
        if (javaChannel().finishConnect()) {
            selectionKey().interestOps(selectionKey().interestOps() and SelectionKey.OP_CONNECT.inv())
        } else {
            throw Error("Provider error: failed to finish connect. Provider library should be upgraded.")
        }
    }

    override fun shutdownInput(): ChannelFuture {
        return newFailedFuture(UnsupportedOperationException("shutdownInput"))
    }

    override fun doWriteFileRegion(region: FileRegion?): Long {
        throw UnsupportedOperationException()
    }

    override fun doReadBytes(buf: ByteBuf): Int {
        val allocHandle = unsafe().recvBufAllocHandle()
        allocHandle.attemptedBytesRead(buf.writableBytes())
        return buf.writeBytes(javaChannel(), allocHandle.attemptedBytesRead())
    }

    override fun doWriteBytes(buf: ByteBuf): Int {
        val expectedWrittenBytes: Int = buf.readableBytes()
        return buf.readBytes(javaChannel(), expectedWrittenBytes)
    }

    public override fun javaChannel(): SocketChannelUDT {
        return super.javaChannel() as SocketChannelUDT
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(NioUdtByteConnectorChannel::class.java)
        private fun privilegedBind(socketChannel: SocketChannelUDT, localAddress: SocketAddress) {
            try {
                AccessController.doPrivileged(
                    PrivilegedExceptionAction {
                        socketChannel.bind(localAddress)
                        null
                    },
                )
            } catch (e: PrivilegedActionException) {
                throw e.cause as IOException
            }
        }
    }
}
