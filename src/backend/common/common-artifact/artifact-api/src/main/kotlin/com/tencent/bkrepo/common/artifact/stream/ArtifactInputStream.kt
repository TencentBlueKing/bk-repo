package com.tencent.bkrepo.common.artifact.stream

import java.io.InputStream

class ArtifactInputStream(
    private val delegate: InputStream,
    val range: Range
) : InputStream() {

    private val listenerList = mutableListOf<StreamReadListener>()

    override fun read(): Int {
        return delegate.read().apply {
            if (this >= 0) {
                listenerList.forEach { it.data(this) }
            } else {
                notifyClose()
            }
        }
    }

    override fun read(b: ByteArray): Int {
        return delegate.read(b).apply {
            if (this >= 0) {
                listenerList.forEach { it.data(b, this) }
            } else {
                notifyClose()
            }
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return delegate.read(b, off, len).apply {
            if (this >= 0) {
                listenerList.forEach { it.data(b, this) }
            } else {
                notifyClose()
            }
        }
    }

    override fun close() {
        listenerList.forEach { it.close() }
        delegate.close()
    }

    override fun skip(n: Long) = delegate.skip(n)
    override fun available() = delegate.available()
    override fun reset() = delegate.reset()
    override fun mark(readlimit: Int) = delegate.mark(readlimit)
    override fun markSupported(): Boolean = delegate.markSupported()

    fun addListener(listener: StreamReadListener) {
        require(!range.isPartialContent()) { "ArtifactInputStream is partial content, may be result in data inconsistent" }
        listenerList.add(listener)
    }

    private fun notifyClose() {
        listenerList.forEach { it.close() }
    }
}

fun InputStream.toArtifactStream(range: Range): ArtifactInputStream {
    return if (this is ArtifactInputStream) this else ArtifactInputStream(this, range)
}

interface StreamReadListener {
    fun data(i: Int)
    fun data(buffer: ByteArray, length: Int)
    fun close()
}
