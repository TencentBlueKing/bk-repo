package com.tencent.bkrepo.common.service.servlet

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import java.io.ByteArrayInputStream

/**
 * 基于数组的缓存ServletInputStream
 * */
class CachedServletInputStream(content: ByteArray) : ServletInputStream() {
    private val buffer = ByteArrayInputStream(content)
    override fun read(): Int {
        return buffer.read()
    }

    override fun isFinished(): Boolean {
        return buffer.available() == 0
    }

    override fun isReady(): Boolean {
        return true
    }

    override fun setReadListener(readListener: ReadListener?) {
        throw java.lang.RuntimeException("Not implemented")
    }
}
