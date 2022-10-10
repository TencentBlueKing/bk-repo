package com.tencent.bkrepo.git.internal.storage

import java.nio.ByteBuffer

interface DfsDataReader : AutoCloseable {
    /**
     * @param pos 数据源pos
     * @param dst 需要填充目标buffer
     * */
    fun read(pos: Long, dst: ByteBuffer): Int

    /**
     * 数据源大小
     * */
    fun size(): Long
}
