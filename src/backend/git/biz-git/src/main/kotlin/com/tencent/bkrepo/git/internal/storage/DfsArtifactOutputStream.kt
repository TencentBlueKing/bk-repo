package com.tencent.bkrepo.git.internal.storage

import com.tencent.bkrepo.common.artifact.resolve.file.chunk.RandomAccessArtifactFile
import org.eclipse.jgit.internal.storage.dfs.DfsOutputStream
import java.nio.ByteBuffer

/**
 * dfs 输出流
 * */
abstract class DfsArtifactOutputStream(private val file: RandomAccessArtifactFile) : DfsOutputStream() {

    @Volatile
    private var closed: Boolean = false
    private val closeLock = Any()

    override fun write(buf: ByteArray, off: Int, len: Int) {
        file.write(buf, off, len)
    }

    override fun read(position: Long, buf: ByteBuffer): Int {
        return file.read(position, buf)
    }

    override fun close() {
        if (closed) {
            return
        }
        synchronized(closeLock) {
            if (closed) {
                return
            }
            closed = true
        }
        file.finish()
        doFlush()
        file.close()
    }

    /**
     * 冲刷文件，将文件写入目的地
     * */
    abstract fun doFlush()
}
