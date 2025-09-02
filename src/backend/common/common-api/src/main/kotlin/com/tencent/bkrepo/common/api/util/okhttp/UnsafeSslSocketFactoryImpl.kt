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

package com.tencent.bkrepo.common.api.util.okhttp

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * 创建不安全的ssl socket。主要是处理希望可以快速关闭连接的情况。
 * */
class UnsafeSslSocketFactoryImpl(private val delegate: SSLSocketFactory, private val closeTimeout: Long) :
    SSLSocketFactory() {
    init {
        require(closeTimeout >= 0)
    }

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        return (delegate.createSocket(s, host, port, autoClose) as SSLSocket).unsafe()
    }

    override fun createSocket(host: String, port: Int): Socket {
        return (delegate.createSocket(host, port) as SSLSocket).unsafe()
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        return (delegate.createSocket(host, port, localHost, localPort) as SSLSocket).unsafe()
    }

    override fun createSocket(host: InetAddress, port: Int): Socket {
        return (delegate.createSocket(host, port) as SSLSocket).unsafe()
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        return (delegate.createSocket(address, port, localAddress, localPort) as SSLSocket).unsafe()
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return delegate.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return delegate.supportedCipherSuites
    }

    private fun SSLSocket.unsafe() = UnsafeSSLSocketImpl(this, closeTimeout)
}
