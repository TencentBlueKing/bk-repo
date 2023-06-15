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

package com.tencent.bkrepo.udt.netty.transport

import com.tencent.bkrepo.udt.OptionUDT
import com.tencent.bkrepo.udt.netty.transport.UdtChannelConfig.Companion.PROTOCOL_RECEIVE_BUFFER_SIZE
import com.tencent.bkrepo.udt.netty.transport.UdtChannelConfig.Companion.PROTOCOL_SEND_BUFFER_SIZE
import com.tencent.bkrepo.udt.netty.transport.UdtChannelConfig.Companion.SYSTEM_RECEIVE_BUFFER_SIZE
import com.tencent.bkrepo.udt.netty.transport.UdtChannelConfig.Companion.SYSTEM_SEND_BUFFER_SIZE
import com.tencent.bkrepo.udt.nio.ChannelUDT
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelOption.SO_BACKLOG
import io.netty.channel.ChannelOption.SO_LINGER
import io.netty.channel.ChannelOption.SO_RCVBUF
import io.netty.channel.ChannelOption.SO_REUSEADDR
import io.netty.channel.ChannelOption.SO_SNDBUF
import io.netty.channel.DefaultChannelConfig

open class DefaultUdtChannelConfig(channel: Channel, channelUDT: ChannelUDT, apply: Boolean) :
    DefaultChannelConfig(channel),
    UdtChannelConfig {
    companion object {
        private const val KB = 1024
        private const val MB = KB * KB
    }

    @Volatile
    private var protocolReceiveBuferSize = 10 * MB

    @Volatile
    private var protocolSendBuferSize = 10 * MB

    @Volatile
    private var systemReceiveBufferSize = 1 * MB

    @Volatile
    private var systemSendBuferSize = 1 * MB

    @Volatile
    private var allocatorReceiveBufferSize = 128 * KB

    @Volatile
    private var allocatorSendBufferSize = 128 * KB

    @Volatile
    private var backlog = 64

    @Volatile
    private var soLinger = 0

    @Volatile
    private var reuseAddress = true

    init {
        if (apply) {
            apply(channelUDT)
        }
    }

    protected fun apply(channelUDT: ChannelUDT) {
        val socketUDT = channelUDT.socketUDT()
        socketUDT.reuseAddress = isReuseAddress()
        socketUDT.sendBufferSize = getSendBufferSize()
        if (getSoLinger() <= 0) {
            socketUDT.setSoLinger(false, 0)
        } else {
            socketUDT.setSoLinger(true, getSoLinger())
        }
        socketUDT.setOption(
            OptionUDT.Protocol_Receive_Buffer_Size,
            getProtocolReceiveBufferSize(),
        )
        socketUDT.setOption(
            OptionUDT.Protocol_Send_Buffer_Size,
            getProtocolSendBufferSize(),
        )
        socketUDT.setOption(
            OptionUDT.System_Receive_Buffer_Size,
            getSystemReceiveBufferSize(),
        )
        socketUDT.setOption(
            OptionUDT.System_Send_Buffer_Size,
            getSystemSendBufferSize(),
        )
    }

    override fun <T : Any> getOption(option: ChannelOption<T>): T? {
        return when (option) {
            PROTOCOL_RECEIVE_BUFFER_SIZE -> getProtocolReceiveBufferSize() as T
            PROTOCOL_SEND_BUFFER_SIZE -> getProtocolSendBufferSize() as T
            SYSTEM_RECEIVE_BUFFER_SIZE -> getSystemReceiveBufferSize() as T
            SYSTEM_SEND_BUFFER_SIZE -> getSystemSendBufferSize() as T
            SO_RCVBUF -> getReceiveBufferSize() as T
            SO_SNDBUF -> getSendBufferSize() as T
            SO_REUSEADDR -> isReuseAddress() as T
            SO_LINGER -> getSoLinger() as T
            SO_BACKLOG -> getBacklog() as T
            else -> super.getOption(option)
        }
    }

    override fun getOptions(): MutableMap<ChannelOption<*>, Any> {
        return getOptions(
            super.getOptions(), PROTOCOL_RECEIVE_BUFFER_SIZE,
            PROTOCOL_SEND_BUFFER_SIZE, SYSTEM_RECEIVE_BUFFER_SIZE,
            SYSTEM_SEND_BUFFER_SIZE, SO_RCVBUF, SO_SNDBUF, SO_REUSEADDR,
            SO_LINGER, SO_BACKLOG,
        )
    }

    override fun <T : Any?> setOption(option: ChannelOption<T>?, value: T): Boolean {
        validate(option, value)
        when (option) {
            PROTOCOL_RECEIVE_BUFFER_SIZE -> setProtocolReceiveBufferSize(value as Int)
            PROTOCOL_SEND_BUFFER_SIZE -> setProtocolSendBufferSize(value as Int)
            SYSTEM_RECEIVE_BUFFER_SIZE -> setSystemReceiveBufferSize(value as Int)
            SYSTEM_SEND_BUFFER_SIZE -> setSystemSendBufferSize(value as Int)
            SO_RCVBUF -> setReceiveBufferSize(value as Int)
            SO_SNDBUF -> setSendBufferSize(value as Int)
            SO_REUSEADDR -> setReuseAddress(value as Boolean)
            SO_LINGER -> setSoLinger(value as Int)
            SO_BACKLOG -> setBacklog(value as Int)
            else -> return super.setOption(option, value)
        }
        return true
    }

    override fun getBacklog(): Int {
        return backlog
    }

    override fun getProtocolReceiveBufferSize(): Int {
        return protocolReceiveBuferSize
    }

    override fun getProtocolSendBufferSize(): Int {
        return protocolSendBuferSize
    }

    override fun getReceiveBufferSize(): Int {
        return allocatorReceiveBufferSize
    }

    override fun getSendBufferSize(): Int {
        return allocatorSendBufferSize
    }

    override fun getSoLinger(): Int {
        return soLinger
    }

    override fun getSystemReceiveBufferSize(): Int {
        return systemReceiveBufferSize
    }

    override fun getSystemSendBufferSize(): Int {
        return systemSendBuferSize
    }

    override fun isReuseAddress(): Boolean {
        return reuseAddress
    }

    override fun setBacklog(backlog: Int): UdtChannelConfig {
        this.backlog = backlog
        return this
    }

    override fun setProtocolReceiveBufferSize(size: Int): UdtChannelConfig {
        this.protocolReceiveBuferSize = size
        return this
    }

    override fun setProtocolSendBufferSize(size: Int): UdtChannelConfig {
        this.protocolSendBuferSize = size
        return this
    }

    override fun setReceiveBufferSize(receiveBufferSize: Int): UdtChannelConfig {
        this.allocatorReceiveBufferSize = receiveBufferSize
        return this
    }

    override fun setReuseAddress(reuseAddress: Boolean): UdtChannelConfig {
        this.reuseAddress = reuseAddress
        return this
    }

    override fun setSendBufferSize(sendBufferSize: Int): UdtChannelConfig {
        this.allocatorSendBufferSize = sendBufferSize
        return this
    }

    override fun setSoLinger(soLinger: Int): UdtChannelConfig {
        this.soLinger = soLinger
        return this
    }

    override fun setSystemReceiveBufferSize(size: Int): UdtChannelConfig {
        this.systemReceiveBufferSize = size
        return this
    }

    override fun setSystemSendBufferSize(size: Int): UdtChannelConfig {
        this.systemSendBuferSize = size
        return this
    }
}
