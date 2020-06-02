package com.tencent.bkrepo.common.artifact.stream

import java.io.InputStream

class ArtifactInputStream(
    private val delegate: InputStream,
    val range: Range
) : InputStream() {
    override fun read() = delegate.read()
    override fun read(b: ByteArray) = delegate.read(b)
    override fun read(b: ByteArray, off: Int, len: Int) = delegate.read(b, off, len)
    override fun skip(n: Long) = delegate.skip(n)
    override fun available() = delegate.available()
    override fun reset() = delegate.reset()
    override fun close() = delegate.close()
    override fun mark(readlimit: Int) = delegate.mark(readlimit)
    override fun markSupported(): Boolean = delegate.markSupported()

    companion object {
        val EMPTY = ArtifactInputStream(EmptyInputStream.INSTANCE, Range.EMPTY)
    }
}

fun InputStream.toArtifactStream(range: Range): ArtifactInputStream {
    return if (this is ArtifactInputStream) this else ArtifactInputStream(this, range)
}

fun InputStream.toArtifactStream(): ArtifactInputStream {
    return if (this is ArtifactInputStream) this else ArtifactInputStream(this, Range.ofFull(this.available().toLong()))
}
