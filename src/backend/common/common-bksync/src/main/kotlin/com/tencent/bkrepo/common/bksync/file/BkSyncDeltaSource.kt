package com.tencent.bkrepo.common.bksync.file

import com.google.common.primitives.Ints
import com.google.common.primitives.Longs
import com.tencent.bkrepo.common.api.stream.readInt
import com.tencent.bkrepo.common.api.stream.readLong
import com.tencent.bkrepo.common.bksync.file.BkSyncDeltaSource.Companion.toBkSyncDeltaSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import org.apache.commons.codec.binary.Hex

/**
 * bd抽象资源
 *
 * 定义了bd资源的格式，支持输出和读取bd资源。
 * bd资源可用于增量压缩中，记录了源文件和目标文件信息，然后
 * 通过这些信息，可以还原源文件，同时支持md5.
 * 另外bd资源也可以保存其他相关信息，只要需要记录两个key之间关系
 * 都可以用该格式
 * */
abstract class BkSyncDeltaSource(
    val src: String, // 源key
    val dest: String, // 目标key
    val md5Bytes: ByteArray, // 源md5
) {

    /**
     * 校验bd资源完整性
     * */
    private val crc32 = CRC32()
    private val srcBytes = src.toByteArray()
    private val destBytes = dest.toByteArray()
    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    /**
     * bd资源内容
     * */
    abstract fun content(): InputStream

    /**
     * bd资源内容长度
     * */
    abstract fun contentLength(): Long

    /**
     * 将该资源写入到[outputStream]
     *
     * bd格式分为三部分
     * 1. header 保存bd文件的基本信息，比如src/dest
     * 2. payload 实际内容
     * 3. trailer crc32
     * */
    fun writeTo(outputStream: OutputStream) {
        content().use {
            crc32.reset()
            outputStream.write(encodeHeader())
            var bytes = it.read(buffer)
            var bytesCopied = 0
            while (bytes >= 0) {
                crc32.update(buffer, 0, bytes)
                outputStream.write(buffer, 0, bytes)
                bytesCopied += bytes
                bytes = it.read(buffer)
            }
            outputStream.write(encodeTrailer())
        }
    }

    /**
     * 获取bd资源大小
     * */
    fun getSize(): Long {
        return srcBytes.size + destBytes.size + contentLength() + BkSyncDeltaFileHeader.STATIC_LENGTH
    }

    /**
     * 获取源md5
     * */
    fun getSrcMd5(): String {
        return Hex.encodeHexString(md5Bytes)
    }

    /**
     * 编码header
     *
     * header格式
     * Magic number            -4B
     * src and dest sum size   -4B (src size in high 16bit,dest size in low 16bit)
     * src                     -src size B
     * dest                    -dest size B
     * src md5                 -16B
     * data size               -8B
     * extra                   -1B
     * */
    private fun encodeHeader(): ByteArray {
        val headerOutput = ByteArrayOutputStream()
        headerOutput.write(Ints.toByteArray(BD_MAGIC))
        headerOutput.write(Ints.toByteArray(srcBytes.size.shl(16).or(destBytes.size)))
        headerOutput.write(srcBytes)
        headerOutput.write(destBytes)
        headerOutput.write(md5Bytes)
        headerOutput.write(Longs.toByteArray(contentLength()))
        headerOutput.write(0)
        return headerOutput.toByteArray()
    }

    /**
     * 编码尾部
     * */
    private fun encodeTrailer(): ByteArray {
        return Longs.toByteArray(crc32.value)
    }

    companion object {
        // BD资源魔术，2023bd
        const val BD_MAGIC = 0x14176264

        /**
         * 读取BD文件
         * @param contentFile BD内容保存文件
         * @return BD资源
         * */
        fun File.toBkSyncDeltaSource(contentFile: File): BkSyncDeltaSource {
            return this.inputStream().use { it.toBkSyncDeltaSource(contentFile) }
        }

        /**
         * 读取BD数据流
         * @param contentFile BD内容保存文件
         * @return BD资源
         * */
        fun InputStream.toBkSyncDeltaSource(contentFile: File): BkSyncDeltaSource {
            val header = readHeader(this)
            var remaining = header.dataSize
            val crc32 = CRC32()
            var buffer = ByteArray(remaining.coerceAtMost(DEFAULT_BUFFER_SIZE.toLong()).toInt())
            var bytes = this.read(buffer)
            contentFile.outputStream().use {
                while (bytes >= 0 && remaining > 0) {
                    crc32.update(buffer, 0, bytes)
                    it.write(buffer, 0, bytes)
                    remaining -= bytes
                    if (remaining < buffer.size) {
                        buffer = ByteArray(remaining.toInt())
                    }
                    bytes = this.read(buffer)
                }
            }
            readTrailer(this, crc32)
            with(header) {
                return FileBkSyncDeltaSource(src, dest, md5Bytes, contentFile)
            }
        }

        /**
         * 读取BD头
         * @param inputStream BD流
         * @return BD头
         * */
        fun readHeader(inputStream: InputStream): BkSyncDeltaFileHeader {
            val magicNum = inputStream.readInt()
            require(magicNum == BD_MAGIC) { "Not in BD format" }
            val sumLen = inputStream.readInt()
            val srcSize = sumLen.ushr(16)
            val destSize = 0xFFFF and sumLen
            require(srcSize > 0 && destSize > 0)
            // 剩余header大小
            val headerPayloadSize = srcSize + destSize + 25
            val bytes = ByteArray(headerPayloadSize)
            require(inputStream.read(bytes) == headerPayloadSize) { "Corrupt BD header" }
            val src = String(bytes, 0, srcSize)
            val dest = String(bytes, srcSize, destSize)
            val md5Bytes = bytes.copyOfRange(bytes.lastIndex - 24, bytes.lastIndex - 8)
            val dataSize = Longs.fromByteArray(bytes.copyOfRange(bytes.lastIndex - 8, bytes.lastIndex))
            val extra = bytes.last()
            require(dataSize > 0)
            return BkSyncDeltaFileHeader(src, dest, md5Bytes, dataSize, extra)
        }

        /**
         * 读取尾部
         * */
        private fun readTrailer(inputStream: InputStream, crc32: CRC32) {
            require(inputStream.readLong() == crc32.value) { "Corrupt BD trailer" }
        }
    }
}
