package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.rpm.exception.RpmVersionNotFoundException
import com.tencent.bkrepo.rpm.pojo.Index
import com.tencent.bkrepo.rpm.pojo.XmlIndex
import java.io.IOException
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

object FileInputStreamUtils {

    private const val bufferSize: Long = 5 * 1024 * 1024L

    @Throws(IOException::class)
    fun File.rpmIndex(str: String): Int {
        val fileInputStream = FileInputStream(this)
        val bufferSize = str.toByteArray().size + 1
        val buffer = ByteArray(bufferSize)
        var mark: Int
        // 保存上一次读取的内容
        var tempStr = ""
        var index = 0
        while (fileInputStream.read(buffer).also { mark = it } > 0) {
            val content = String(buffer, 0, mark)
            val insideIndex = (tempStr + content).indexOf(str)
            if (insideIndex >= 0) {
                index = index + insideIndex - bufferSize
                return index
            } else {
                tempStr = content
                index += buffer.size
            }
        }
        return -1
    }

    /**
     * xml 临时文件最后添加新的内容
     */
    @Throws(IOException::class)
    fun File.insertContent(packageXml: String): File {
        val fileSize = this.length()
        val index = fileSize - XmlStrUtils.METADATA_SUFFIX.length
        RandomAccessFile(this, "rw").use {
            it.seek(index)
            it.write("  $packageXml".toByteArray())
        }
        return this
    }

    @Throws(IOException::class)
    fun File.deleteContent(xmlIndex: XmlIndex): File {
        val prefixTempFile = File.createTempFile("prefix", "xml")
        val accessRandomPrefixTempFile = RandomAccessFile(prefixTempFile, "rw")

        val randomAccessFile = RandomAccessFile(this, "rw")
        try {
            // 保存前一部分
            val prefixMark = xmlIndex.prefixIndex
            var prefixCount = 0L
            val preBuffer = ByteArray(bufferSize.toInt())
            loop@while (randomAccessFile.read(preBuffer) > 0) {
                val tempCount = prefixCount + preBuffer.size
                if (tempCount > prefixMark) {
                    accessRandomPrefixTempFile.write(preBuffer, 0, (prefixMark - prefixCount).toInt())
                    break@loop
                } else {
                    accessRandomPrefixTempFile.write(preBuffer, 0, preBuffer.size)
                }
                prefixCount += bufferSize.toInt()
            }

            // 将后一部分接到accessRandomPrefixTempFile
            randomAccessFile.seek(xmlIndex.suffixIndex + XmlStrUtils.PACKAGE_END_MARK.length)
            val sufBuffer = ByteArray(bufferSize.toInt())
            var mark: Int
            while (randomAccessFile.read(sufBuffer).also { mark = it } > 0) {
                accessRandomPrefixTempFile.write(sufBuffer, 0, mark)
            }
        } finally {
            accessRandomPrefixTempFile.closeQuietly()
            randomAccessFile.closeQuietly()
            this.delete()
        }
        return prefixTempFile
    }

    /**
     * xml
     */
    fun File.indexPackage(
        prefixStr: String,
        locationStr: String,
        suffixStr: String
    ): XmlIndex {
        var prefixIndex: Long = -1L
        var locationIndex: Long = -1L
        var suffixIndex: Long = -1L

        val bufferSize = locationStr.toByteArray().size + 1

        val fileInputStream = FileInputStream(this)
        val buffer = ByteArray(bufferSize)
        var mark: Int
        var index: Long = 0
        // 保存上一次读取的内容
        var tempStr = ""
        loop@while (fileInputStream.read(buffer).also { mark = it } > 0) {
            val content = String(buffer, 0, mark)
            if (locationIndex < 0) {
                val prefix = (tempStr + content).searchContent(index, prefixIndex, prefixStr, buffer.size)
                val location = (tempStr + content).searchContent(index, locationIndex, locationStr, buffer.size)
                if (location.isFound) {
                    locationIndex = location.index
                    val suffix = (tempStr + content).searchContent(index, suffixIndex, suffixStr, buffer.size)
                    if (suffix.index > locationIndex) {
                        suffixIndex = suffix.index
                        break@loop
                    }
                }
                if (!location.isFound && prefix.isFound) {
                    prefixIndex = prefix.index
                }
                if (location.isFound && prefix.isFound && prefix.index < location.index) {
                    prefixIndex = prefix.index
                }
            }
            if (locationIndex > 0) {
                val suffix = (tempStr + content).searchContent(index, suffixIndex, suffixStr, buffer.size)
                if (suffix.index > locationIndex) {
                    suffixIndex = suffix.index
                    break@loop
                }
            }
            index += buffer.size
            tempStr = content
        }
        if (prefixIndex <= 0L || locationIndex <= 0L || suffixIndex <= 0L) {
            throw RpmVersionNotFoundException("prefixIndex: $prefixIndex; locationIndex: $locationIndex;suffixIndex: $suffixIndex")
        }
        return XmlIndex(
            prefixIndex,
            locationIndex,
            suffixIndex
        )
    }

    private fun String.searchContent(
        index: Long,
        returnIndex: Long,
        targetStr: String,
        bufferSize: Int
    ): Index {
        val location = this.indexOf(targetStr)
        return if (location >= 0) {
            Index(
                index + location - bufferSize,
                true
            )
        } else {
            Index(
                bufferSize + (if (returnIndex.toInt() == -1) 0 else returnIndex),
                false
            )
        }
    }
}
