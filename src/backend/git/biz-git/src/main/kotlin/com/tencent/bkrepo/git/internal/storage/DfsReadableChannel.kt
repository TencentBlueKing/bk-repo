package com.tencent.bkrepo.git.internal.storage

import org.eclipse.jgit.internal.storage.dfs.ReadableChannel
import java.nio.ByteBuffer

/**
 * 支持预读的抽象读取channel
 * */
class DfsReadableChannel(
    var blockSize: Int,
    private val dfsDataReader: DfsDataReader
) : ReadableChannel {
    // 当前通道的pos
    private var position = 0L
    private var isOpen: Boolean = true
    override fun close() {
        isOpen = false
        dfsDataReader.close()
    }

    override fun read(dst: ByteBuffer): Int {
        val read = dfsDataReader.read(position, dst)
        position += read
        return read
    }

    override fun isOpen(): Boolean {
        return isOpen
    }

    /**
     * 当前channel的pos
     * */
    override fun position(): Long {
        return position
    }

    /**
     * 设置新的pos
     * */
    override fun position(newPosition: Long) {
        position = newPosition
    }

    /**
     * channel的大小
     * */
    override fun size(): Long {
        return dfsDataReader.size()
    }

    /**
     * 块大小
     * */
    override fun blockSize(): Int {
        return blockSize
    }

    override fun setReadAheadBytes(bufferSize: Int) {
        // empty
    }
}
