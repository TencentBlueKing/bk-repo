package com.tencent.bkrepo.common.bksync

/**
 * 块输入流
 * */
interface BlockInputStream : AutoCloseable {
    /**
     * 获取块内容
     * */
    fun getBlock(seq: Int, blockSize: Int, blockData: ByteArray): Int

    /**
     * 获取连续块
     * @param startSeq 开始块序号,包含startSeq
     * @param endSeq 结束块序号，包含endSeq
     * @param blockSize 块大小
     * */
    fun getBlock(startSeq: Int, endSeq: Int, blockSize: Int): ByteArray

    /**
     * 流大小
     * */
    fun totalSize(): Long

    /**
     * 流名称
     * */
    fun name(): String
}
