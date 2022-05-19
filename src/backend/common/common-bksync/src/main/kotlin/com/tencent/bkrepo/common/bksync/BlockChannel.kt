package com.tencent.bkrepo.common.bksync

import java.nio.channels.WritableByteChannel

/**
 * 块输入channel
 * */
interface BlockChannel : AutoCloseable {
    /**
     * 获取块内容
     * */
    fun transferTo(seq: Int, blockSize: Int, target: WritableByteChannel): Long

    /**
     * 获取连续块
     * @param startSeq 开始块序号,包含startSeq
     * @param endSeq 结束块序号，包含endSeq
     * @param blockSize 块大小
     * */
    fun transferTo(startSeq: Int, endSeq: Int, blockSize: Int, target: WritableByteChannel): Long

    /**
     * 流大小
     * */
    fun totalSize(): Long

    /**
     * 流名称
     * */
    fun name(): String
}
