package com.tencent.bkrepo.git.internal.storage

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class DfsFileDataReader(private val file: File) : DfsDataReader {
    private val fd = RandomAccessFile(file, "r")
    private val length = fd.length()
    private val readLock = Any()
    private var close = false
    override fun read(pos: Long, dst: ByteBuffer): Int {
        synchronized(readLock) {
            // 当reader被缓存中移除时，创建新的临时reader读取，并立马关闭
            if (close) {
                val reader = DfsFileDataReader(file)
                reader.use {
                    return reader.read(pos, dst)
                }
            }
            val size = dst.remaining().coerceAtMost((length - pos).toInt())
            if (size == 0) {
                return -1
            }
            val bytes = ByteArray(size)
            fd.seek(pos)
            fd.readFully(bytes, 0, size)
            dst.put(bytes)
            return size
        }
    }

    override fun size(): Long {
        return length
    }

    override fun close() {
        if (close) {
            return
        }
        synchronized(readLock) {
            if (close) {
                return
            }
            close = true
            fd.close()
        }
    }
}
