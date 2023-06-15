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

import io.netty.channel.ChannelConfig
import io.netty.channel.ChannelOption

interface UdtChannelConfig : ChannelConfig {
    companion object {
        val PROTOCOL_RECEIVE_BUFFER_SIZE = ChannelOption.valueOf<Int>("PROTOCOL_RECEIVE_BUFFER_SIZE")
        val PROTOCOL_SEND_BUFFER_SIZE = ChannelOption.valueOf<Int>("PROTOCOL_SEND_BUFFER_SIZE")
        val SYSTEM_RECEIVE_BUFFER_SIZE = ChannelOption.valueOf<Int>("SYSTEM_RECEIVE_BUFFER_SIZE")
        val SYSTEM_SEND_BUFFER_SIZE = ChannelOption.valueOf<Int>("SYSTEM_SEND_BUFFER_SIZE")
    }

    /**
     * Gets [KindUDT.ACCEPTOR] channel backlog.
     */
    fun getBacklog(): Int

    /**
     * Gets [OptionUDT.Protocol_Receive_Buffer_Size]
     */
    fun getProtocolReceiveBufferSize(): Int

    /**
     * Gets [OptionUDT.Protocol_Send_Buffer_Size]
     */
    fun getProtocolSendBufferSize(): Int

    /**
     * Gets the [ChannelOption.SO_RCVBUF] option.
     */
    fun getReceiveBufferSize(): Int

    /**
     * Gets the [ChannelOption.SO_SNDBUF] option.
     */
    fun getSendBufferSize(): Int

    /**
     * Gets the [ChannelOption.SO_LINGER] option.
     */
    fun getSoLinger(): Int

    /**
     * Gets [OptionUDT.System_Receive_Buffer_Size]
     */
    fun getSystemReceiveBufferSize(): Int

    /**
     * Gets [OptionUDT.System_Send_Buffer_Size]
     */
    fun getSystemSendBufferSize(): Int

    /**
     * Gets the [ChannelOption.SO_REUSEADDR] option.
     */
    fun isReuseAddress(): Boolean

    /**
     * Sets [KindUDT.ACCEPTOR] channel backlog.
     */
    fun setBacklog(backlog: Int): UdtChannelConfig

    /**
     * Sets [OptionUDT.Protocol_Receive_Buffer_Size]
     */
    fun setProtocolReceiveBufferSize(size: Int): UdtChannelConfig

    /**
     * Sets [OptionUDT.Protocol_Send_Buffer_Size]
     */
    fun setProtocolSendBufferSize(size: Int): UdtChannelConfig

    /**
     * Sets the [ChannelOption.SO_RCVBUF] option.
     */
    fun setReceiveBufferSize(receiveBufferSize: Int): UdtChannelConfig

    /**
     * Sets the [ChannelOption.SO_REUSEADDR] option.
     */
    fun setReuseAddress(reuseAddress: Boolean): UdtChannelConfig

    /**
     * Sets the [ChannelOption.SO_SNDBUF] option.
     */
    fun setSendBufferSize(sendBufferSize: Int): UdtChannelConfig

    /**
     * Sets the [ChannelOption.SO_LINGER] option.
     */
    fun setSoLinger(soLinger: Int): UdtChannelConfig

    /**
     * Sets [OptionUDT.System_Receive_Buffer_Size]
     */
    fun setSystemReceiveBufferSize(size: Int): UdtChannelConfig

    /**
     * Sets [OptionUDT.System_Send_Buffer_Size]
     */
    fun setSystemSendBufferSize(size: Int): UdtChannelConfig
}