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
                notifyFinish()
            }
        }
    }

    override fun read(b: ByteArray): Int {
        return delegate.read(b).apply {
            if (this >= 0) {
                listenerList.forEach { it.data(b, this) }
            } else {
                notifyFinish()
            }
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return delegate.read(b, off, len).apply {
            if (this >= 0) {
                listenerList.forEach { it.data(b, this) }
            } else {
                notifyFinish()
            }
        }
    }

    override fun close() {
        delegate.close()
        listenerList.forEach { it.close() }
    }

    override fun skip(n: Long) = delegate.skip(n)
    override fun available() = delegate.available()
    override fun reset() = delegate.reset()
    override fun mark(readlimit: Int) = delegate.mark(readlimit)
    override fun markSupported(): Boolean = delegate.markSupported()

    /**
     * 添加流读取监听器[listener]
     */
    fun addListener(listener: StreamReadListener) {
        if (range.isPartialContent()) {
            listener.close()
            throw IllegalArgumentException("ArtifactInputStream is partial content, maybe cause data inconsistent")
        }
        listenerList.add(listener)
    }

    /**
     * 通知各个listener流读取完成
     */
    private fun notifyFinish() {
        listenerList.forEach { it.finish() }
    }
}

fun InputStream.toArtifactStream(range: Range): ArtifactInputStream {
    return if (this is ArtifactInputStream) this else ArtifactInputStream(this, range)
}

interface StreamReadListener {
    fun data(i: Int)
    fun data(buffer: ByteArray, length: Int)

    /**
     * 流读取完成
     */
    fun finish()

    /**
     * 流关闭
     */
    fun close()
}
