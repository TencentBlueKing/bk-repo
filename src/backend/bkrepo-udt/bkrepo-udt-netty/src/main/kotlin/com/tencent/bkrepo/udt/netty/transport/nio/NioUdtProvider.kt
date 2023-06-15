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

import com.tencent.bkrepo.udt.SocketUDT
import com.tencent.bkrepo.udt.TypeUDT
import com.tencent.bkrepo.udt.netty.transport.UdtChannel
import com.tencent.bkrepo.udt.netty.transport.UdtServerChannel
import com.tencent.bkrepo.udt.nio.ChannelUDT
import com.tencent.bkrepo.udt.nio.KindUDT
import com.tencent.bkrepo.udt.nio.SelectorProviderUDT
import com.tencent.bkrepo.udt.nio.ServerSocketChannelUDT
import com.tencent.bkrepo.udt.nio.SocketChannelUDT
import io.netty.channel.Channel
import io.netty.channel.ChannelException
import io.netty.channel.ChannelFactory
import java.io.IOException
import java.lang.IllegalStateException

class NioUdtProvider<T : UdtChannel>(val type: TypeUDT, val kind: KindUDT) : ChannelFactory<T> {
    companion object {
        val BYTE_PROVIDER = SelectorProviderUDT.STREAM
        val BYTE_ACCEPTOR = NioUdtProvider<UdtServerChannel>(TypeUDT.STREAM, KindUDT.ACCEPTOR)
        val BYTE_CONNECTOR = NioUdtProvider<UdtChannel>(TypeUDT.STREAM, KindUDT.CONNECTOR)
        fun newAcceptorChannelUDT(type: TypeUDT): ServerSocketChannelUDT {
            try {
                return SelectorProviderUDT.from(type).openServerSocketChannel()
            } catch (e: IOException) {
                throw ChannelException("failed to open a server socket channel", e)
            }
        }

        fun newConnectorChannelUDT(type: TypeUDT): SocketChannelUDT {
            try {
                return SelectorProviderUDT.from(type).openSocketChannel()
            } catch (e: IOException) {
                throw ChannelException("failed to open a socket channel", e)
            }
        }

        fun socketUDT(channel: Channel): SocketUDT? {
            val channelUDT = channelUDT(channel)
            return channelUDT?.socketUDT()
        }

        fun channelUDT(channel: Channel): ChannelUDT? {
            return when (channel) {
                is NioUdtByteAcceptorChannel -> channel.javaChannel()
                is NioUdtByteConnectorChannel -> channel.javaChannel()
                else -> null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun newChannel(): T {
        when (kind) {
            KindUDT.ACCEPTOR -> {
                when (type) {
                    TypeUDT.STREAM -> return NioUdtByteAcceptorChannel() as T
                    else -> throw IllegalStateException("wrong type=$type")
                }
            }

            KindUDT.CONNECTOR -> {
                when (type) {
                    TypeUDT.STREAM -> return NioUdtByteConnectorChannel() as T
                    else -> throw IllegalStateException("wrong type=$type")
                }
            }

            else -> throw ChannelException("wrong kind=$kind")
        }
    }
}
