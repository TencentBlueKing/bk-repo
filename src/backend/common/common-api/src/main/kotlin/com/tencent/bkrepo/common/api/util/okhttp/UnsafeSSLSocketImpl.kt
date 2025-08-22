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

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.util.AsyncUtils.trace
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketAddress
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import kotlin.system.measureNanoTime

/**
 * 不安全的ssl socket。重写了SSLSocketImpl的close方法，允许强势关闭连接。
 * 但是因此，可能会破坏tls的正确关闭。
 * */
class UnsafeSSLSocketImpl(private val delegate: SSLSocket, private val closeTimeout: Long) : SSLSocket() {

    init {
        require(closeTimeout >= 0)
    }

    private val closeLock = Any()

    override fun getSupportedCipherSuites(): Array<String> {
        return delegate.supportedCipherSuites
    }

    override fun getEnabledCipherSuites(): Array<String> {
        return delegate.enabledCipherSuites
    }

    override fun setEnabledCipherSuites(suites: Array<out String>?) {
        delegate.enabledCipherSuites = suites
    }

    override fun getSupportedProtocols(): Array<String> {
        return delegate.supportedProtocols
    }

    override fun getEnabledProtocols(): Array<String> {
        return delegate.enabledProtocols
    }

    override fun setEnabledProtocols(protocols: Array<out String>?) {
        delegate.enabledProtocols = protocols
    }

    override fun getSession(): SSLSession {
        return delegate.session
    }

    override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener?) {
        delegate.addHandshakeCompletedListener(listener)
    }

    override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener?) {
        delegate.removeHandshakeCompletedListener(listener)
    }

    override fun startHandshake() {
        delegate.startHandshake()
    }

    override fun setUseClientMode(mode: Boolean) {
        delegate.useClientMode = mode
    }

    override fun getUseClientMode(): Boolean {
        return delegate.useClientMode
    }

    override fun setNeedClientAuth(need: Boolean) {
        delegate.needClientAuth = need
    }

    override fun getNeedClientAuth(): Boolean {
        return delegate.needClientAuth
    }

    override fun setWantClientAuth(want: Boolean) {
        delegate.wantClientAuth = want
    }

    override fun getWantClientAuth(): Boolean {
        return delegate.wantClientAuth
    }

    override fun setEnableSessionCreation(flag: Boolean) {
        delegate.enableSessionCreation = flag
    }

    override fun getEnableSessionCreation(): Boolean {
        return delegate.enableSessionCreation
    }

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        delegate.connect(endpoint, timeout)
    }

    override fun isClosed(): Boolean {
        return delegate.isClosed
    }

    override fun shutdownInput() {
        delegate.shutdownInput()
    }

    override fun isInputShutdown(): Boolean {
        return delegate.isInputShutdown
    }

    override fun shutdownOutput() {
        delegate.shutdownOutput()
    }

    override fun isOutputShutdown(): Boolean {
        return delegate.isOutputShutdown
    }

    override fun getInputStream(): InputStream {
        return delegate.inputStream
    }

    override fun getOutputStream(): OutputStream {
        return delegate.outputStream
    }

    override fun getSSLParameters(): SSLParameters {
        return delegate.sslParameters
    }

    override fun setSSLParameters(params: SSLParameters?) {
        delegate.sslParameters = params
    }

    override fun getApplicationProtocol(): String {
        return delegate.applicationProtocol
    }

    override fun setHandshakeApplicationProtocolSelector(
        selector: BiFunction<
            SSLSocket,
            MutableList<String>, String,
            >?,
    ) {
        delegate.handshakeApplicationProtocolSelector = selector
    }

    override fun getHandshakeApplicationProtocolSelector(): BiFunction<SSLSocket, MutableList<String>, String> {
        return delegate.getHandshakeApplicationProtocolSelector()
    }

    override fun getHandshakeApplicationProtocol(): String {
        return delegate.handshakeApplicationProtocol
    }

    /**
     * ssl socket在write阻塞时，close也会阻塞。如果close长时间阻塞，则影响上层业务。
     * */
    override fun close() {
        if (logger.isDebugEnabled) {
            logger.debug("Close socket $delegate")
        }
        if (delegate.isClosed) {
            return
        }
        synchronized(closeLock) {
            if (delegate.isClosed) {
                return
            }
            if (closeTimeout == 0L) {
                delegate.close()
                return
            }
            val timeoutFuture = threadPool.schedule(
                Runnable { closeImmediately() }.trace(),
                closeTimeout,
                TimeUnit.SECONDS,
            )
            val closeTime = measureNanoTime { delegate.closeQuietly() }
            if (closeTime < Duration.ofSeconds(closeTimeout).toNanos()) {
                timeoutFuture.cancel(false)
            }
        }
    }

    /**
     * 立即关闭ssl socket,可能会破坏tls的正确关闭
     * */
    @Synchronized
    fun closeImmediately() {
        try {
            val clazz = Class.forName("sun.security.ssl.SSLSocketImpl")
            val closeSocketMethod = clazz.getDeclaredMethod("closeSocket", Boolean::class.java)
            closeSocketMethod.isAccessible = true
            if (!delegate.isClosed) {
                closeSocketMethod.invoke(delegate, false)
                // 低版本的jdk没有这个字段，但是并不影响socket的关闭
                clazz.declaredFields.firstOrNull { it.name == "tlsIsClosed" }?.let {
                    it.isAccessible = true
                    it.set(delegate, true)
                }
                logger.info("Success close socket $delegate")
            }
        } catch (e: Exception) {
            logger.warn("Unable close socket $delegate", e)
        }
    }

    private fun Closeable.closeQuietly() {
        try {
            this.close()
        } catch (ignored: Throwable) {
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UnsafeSSLSocketImpl::class.java)
        private val defaultFactory =
            ThreadFactoryBuilder().setNameFormat("unsafe-sslSocket-watchDog-%d").setDaemon(true).build()
        private val threadPool = Executors.newSingleThreadScheduledExecutor(defaultFactory)
    }
}
