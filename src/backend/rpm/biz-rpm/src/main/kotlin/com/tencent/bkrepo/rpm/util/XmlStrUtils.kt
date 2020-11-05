package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.common.api.constant.StringPool.DASH
import com.tencent.bkrepo.rpm.pojo.Index
import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.pojo.RepodataUri
import com.tencent.bkrepo.rpm.pojo.RpmVersion
import com.tencent.bkrepo.rpm.pojo.XmlIndex
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil.toXml
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadata
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmXmlMetadata
import org.slf4j.LoggerFactory
import org.springframework.util.StopWatch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.regex.Pattern

object XmlStrUtils {
    private val logger = LoggerFactory.getLogger(XmlStrUtils::class.java)

    // package 节点开始标识
    private const val PACKAGE_START_MARK = "  <package type=\"rpm\">"
    // package 结束开始标识
    const val PACKAGE_END_MARK = "</package>\n"

    private const val PACKAGE_OTHER_START_MARK = "  <package pkgid"

    // RpmMetadata序列化成xml中 metadata 开始字符串
    private const val PRIMARY_METADATA_PREFIX = "<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke.edu/metadata/rpm\" packages=\"1\">\n"
    private const val OTHERS_METADATA_PREFIX = "<metadata xmlns=\"http://linux.duke.edu/metadata/other\" packages=\"1\">\n"
    private const val FILELISTS_METADATA_PREFIX = "<metadata xmlns=\"http://linux.duke.edu/metadata/filelists\" packages=\"1\">\n"
    // RpmMetadata序列化成xml中 metadata 结束字符串
    private const val METADATA_SUFFIX = "</metadata>"

    /**
     * 插入新索引
     */
    fun insertPackageIndex(
        randomAccessFile: RandomAccessFile,
        indexType: IndexType,
        rpmXmlMetadata: RpmXmlMetadata
    ): Int {
        val packageXml = rpmXmlMetadata.toPackageXml(indexType)
        insertPackageXml(randomAccessFile, packageXml)
        // updatePackageCount(randomAccessFile, indexType, 1, false)
        return 1
    }

    /**
     * 替换索引数据
     */
    fun updatePackageIndex(randomAccessFile: RandomAccessFile, indexType: IndexType, rpmXmlMetadata: RpmXmlMetadata): Int {
        val rpmVersion = RpmVersion(
            rpmXmlMetadata.packages.first().name,
            "default",
            rpmXmlMetadata.packages.first().version.epoch.toString(),
            rpmXmlMetadata.packages.first().version.ver,
            rpmXmlMetadata.packages.first().version.rel
        )
        logger.info("updatePackageIndex: [indexType|$rpmVersion]")
        val location = if (indexType == IndexType.PRIMARY) {
            (rpmXmlMetadata as RpmMetadata).packages.first().location.href
        } else {
            null
        }

        val locationStr = getLocationStr(indexType, rpmVersion, location)
        val prefix = getPackagePrefix(indexType)
        val packageXml = rpmXmlMetadata.toPackageXml(indexType)

        val stopWatch = StopWatch("updatePackageIndex")

        stopWatch.start("findPackageIndex")
        val xmlIndex = findPackageIndex(randomAccessFile, prefix, locationStr, PACKAGE_END_MARK)
        stopWatch.stop()

        val changeCount = if (xmlIndex == null) {
            logger.warn("find package index failed, skip delete index")
            stopWatch.start("insertPackageXml")
            insertPackageXml(randomAccessFile, packageXml)
            stopWatch.stop()
            1
        } else {
            stopWatch.start("updatePackageIndex")
            val cleanLength = xmlIndex.suffixEndIndex - xmlIndex.prefixIndex
            updatePackageXml(randomAccessFile, xmlIndex.prefixIndex, cleanLength, packageXml)
            stopWatch.stop()
            0
        }
        if (logger.isDebugEnabled) {
            logger.debug("updatePackageIndexStat: $stopWatch")
        }
        return changeCount
    }

    /**
     * 删除包对应的索引
     * [indexType] 索引类型
     * [file] 需要删除内容的索引文件
     * [rpmVersion]
     * [location] rpm构件相对repodata的目录
     * [File] 更新后xml
     */
    fun deletePackageIndex(
        randomAccessFile: RandomAccessFile,
        indexType: IndexType,
        rpmVersion: RpmVersion,
        location: String?
    ): Int {
        logger.info("deletePackageIndex: [$indexType|$rpmVersion|$location]")
        val locationStr = getLocationStr(indexType, rpmVersion, location)
        val prefix = getPackagePrefix(indexType)

        val stopWatch = StopWatch("deletePackageIndex")
        stopWatch.start("findIndex")
        val xmlIndex = findPackageIndex(randomAccessFile, prefix, locationStr, PACKAGE_END_MARK)
        stopWatch.stop()

        if (xmlIndex == null) {
            logger.warn("deletePackageIndex, findPackageIndex failed, skip delete index")
            return 0
        } else {
            stopWatch.start("deleteContent")
            deletePackageXml(randomAccessFile, xmlIndex)
            stopWatch.stop()
        }
        if (logger.isDebugEnabled) {
            logger.debug("deletePackageIndexStat: $stopWatch")
        }
        return -1
    }

    /**
     * 将RpmMetadata 序列化为xml，然后去除metadata根节点。
     * 不直接序列化Package的目的是为了保留缩进格式。
     */
    private fun RpmXmlMetadata.toPackageXml(indexType: IndexType): String {
        val prefix = when (indexType) {
            IndexType.OTHERS -> OTHERS_METADATA_PREFIX
            IndexType.PRIMARY -> PRIMARY_METADATA_PREFIX
            IndexType.FILELISTS -> FILELISTS_METADATA_PREFIX
        }
        return this.toXml().removePrefix(prefix).removeSuffix(METADATA_SUFFIX)
    }

    /**
     * 按照仓库设置的repodata深度 分割请求参数
     */
    fun resolveRepodataUri(uri: String, depth: Int): RepodataUri {
        val uriList = uri.removePrefix("/").split("/")
        val repodataPath = java.lang.StringBuilder()
        for (i in 0 until depth) {
            repodataPath.append(uriList[i]).append("/")
        }
        val artifactRelativePath = uri.removePrefix("/").split(repodataPath.toString())[1]
        return RepodataUri(repodataPath.toString(), artifactRelativePath)
    }

    /**
     * 在文件名前加上sha1值。
     */
    fun getGroupNodeFullPath(uri: String, fileSha1: String): String {
        val uriList = uri.removePrefix("/").split("/")
        val filename = "$fileSha1$DASH${uriList.last()}"
        val stringBuilder = StringBuilder("/")
        val size = uriList.size
        for (i in 0 until size - 1) {
            stringBuilder.append(uriList[i]).append("/")
        }
        stringBuilder.append(filename)
        return stringBuilder.toString()
    }

    fun indexOf(randomAccessFile: RandomAccessFile, str: String): Long {
        val bufferSize = str.toByteArray().size + 1
        val buffer = ByteArray(bufferSize)
        var mark: Int
        // 保存上一次读取的内容
        var tempStr = ""
        var index = 0L
        randomAccessFile.seek(0L)
        while (randomAccessFile.read(buffer).also { mark = it } > 0) {
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
        return -1L
    }

    /**
     * 插入rpm包索引
     */
    fun insertPackageXml(randomAccessFile: RandomAccessFile, packageXml: String) {
        val insertIndex = randomAccessFile.length() - METADATA_SUFFIX.length
        updatePackageXml(randomAccessFile, insertIndex, 0, packageXml)
    }

    /**
     * 删除rpm包索引
     */
    fun deletePackageXml(randomAccessFile: RandomAccessFile, xmlIndex: XmlIndex) {
        updatePackageXml(randomAccessFile, xmlIndex.prefixIndex, xmlIndex.suffixEndIndex - xmlIndex.prefixIndex, "")
    }

    /**
     * [File] 需要查找的文件
     * [XmlIndex] 封装结果
     * [prefixStr] rpm 包索引package节点的开始字符串
     * [locationStr] 可以唯一定位一个 rpm 包索引package节点位置的 字符串
     * [suffixStr] rpm 包索引package节点的结束字符串
     */
    fun findPackageIndex(
        randomAccessFile: RandomAccessFile,
        prefixStr: String,
        locationStr: String,
        suffixStr: String
    ): XmlIndex? {
        if (logger.isDebugEnabled) {
            logger.debug("findPackageIndex: [$prefixStr|$locationStr|$suffixStr]")
        }
        var prefixIndex: Long = -1L
        var locationIndex: Long = -1L
        var suffixIndex: Long = -1L

        val buffer = ByteArray(locationStr.toByteArray().size + 1)
        var len: Int
        var index: Long = 0
        // 保存上一次读取的内容
        var tempStr = ""
        randomAccessFile.seek(0L)
        loop@ while (randomAccessFile.read(buffer).also { len = it } > 0) {
            val content = String(buffer, 0, len)
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

        return if (prefixIndex <= 0L || locationIndex <= 0L || suffixIndex <= 0L) {
            logger.warn("findPackageIndex failed, locationStr: $locationStr, prefixIndex: $prefixIndex, locationIndex: $locationIndex, suffixIndex: $suffixIndex")
            null
        } else {
            val suffixEndIndex = suffixIndex + suffixStr.length
            if (logger.isDebugEnabled) {
                logger.debug("findPackageIndex result: [$prefixIndex|$locationIndex|$suffixIndex|$suffixEndIndex]")
            }
            XmlIndex(prefixIndex, locationIndex, suffixIndex, suffixEndIndex)
        }
    }

    /**
     * 查找内容，返回xx位置？
     * [index] 查找开始位置 0
     * [returnIndex] 需要查找的文件 xml节点在文件中的开始位置
     * [targetStr] 需要查找的字符串
     * [bufferSize] 缓存大小
     */
    private fun String.searchContent(index: Long, returnIndex: Long, targetStr: String, bufferSize: Int): Index {
        val location = this.indexOf(targetStr)
        return if (location >= 0) {
            Index(index + location - bufferSize, true)
        } else {
            Index(bufferSize + (if (returnIndex.toInt() == -1) 0 else returnIndex), false)
        }
    }

    /**
     * 更新索引文件中 package 数量
     */
    fun updatePackageCount(randomAccessFile: RandomAccessFile, indexType: IndexType, changCount: Int, calculatePackage: Boolean) {
        logger.info("updatePackageCount, indexType: $indexType, changCount: $changCount, calculatePackage: $calculatePackage")
        val currentCount = resolvePackageCount(randomAccessFile, indexType)
        logger.info("currentCount: $currentCount")

        val packageCount = if (calculatePackage) {
            calculatePackageCount(randomAccessFile, indexType)
        } else {
            currentCount + changCount
        }
        logger.info("packageCount: $packageCount")
        if (packageCount == currentCount) {
            logger.info("package count not change")
            return
        }

        val packageCountIndex = when (indexType) {
            IndexType.PRIMARY ->
                """<?xml version="1.0" encoding="UTF-8" ?>
<metadata xmlns="http://linux.duke.edu/metadata/common" xmlns:rpm="http://linux.duke.edu/metadata/rpm" packages="""".length
            IndexType.FILELISTS ->
                """<?xml version="1.0" encoding="UTF-8" ?>
<metadata xmlns="http://linux.duke.edu/metadata/filelists" packages="""".length
            IndexType.OTHERS ->
                """<?xml version="1.0" encoding="UTF-8" ?>
<metadata xmlns="http://linux.duke.edu/metadata/other" packages="""".length
        }
        updatePackageXml(randomAccessFile, packageCountIndex.toLong(), currentCount.toString().length.toLong(), packageCount.toString())
    }

    /**
     * 更新索引
     * [randomAccessFile] 文件内容
     * [index] 更新位置
     * [cleanLength] 清理长度
     * [newContent] 新内容
     */
    fun updatePackageXml(randomAccessFile: RandomAccessFile, updateIndex: Long, cleanLength: Long, newContent: String) {
        if (logger.isDebugEnabled) {
            logger.debug("updatePackageXml: updateIndex: $updateIndex, cleanLength: $cleanLength, newContentLength: ${newContent.length}")
        }
        // 追加到文件最后
        randomAccessFile.seek(updateIndex + cleanLength)
        if (randomAccessFile.filePointer == randomAccessFile.length()) {
            if (newContent.isNotEmpty()) {
                randomAccessFile.seek(updateIndex)
                randomAccessFile.write(newContent.toByteArray())
                randomAccessFile.setLength(randomAccessFile.filePointer)
            }
            return
        }

        var bufferFile: File? = null
        try {
            var memoryBuffer: ByteArrayOutputStream? = null
            // 插入点离文件末尾小于2M时使用内存缓存
            val outputStream = if (randomAccessFile.length() - randomAccessFile.filePointer > 2 * 1024 * 1024) {
                bufferFile = createTempFile("updatePackageXml_", ".buffer")
                if (logger.isDebugEnabled) {
                    logger.debug("create buffer file: ${bufferFile.absolutePath}")
                }
                bufferFile.outputStream()
            } else {
                memoryBuffer = ByteArrayOutputStream()
                memoryBuffer
            }
            // 缓存文件后半部分
            outputStream.use { stream ->
                val buffer = newBuffer()
                var len: Int
                while (randomAccessFile.read(buffer).also { len = it } > 0) {
                    stream.write(buffer, 0, len)
                }
                stream.flush()
            }

            randomAccessFile.seek(updateIndex)
            if (newContent.isNotEmpty()) {
                randomAccessFile.write(newContent.toByteArray())
            }

            if (memoryBuffer != null) {
                randomAccessFile.write(memoryBuffer.toByteArray())
            } else {
                bufferFile!!.inputStream().use { inputStream ->
                    val buffer = newBuffer()
                    var len: Int
                    while (inputStream.read(buffer).also { len = it } > 0) {
                        randomAccessFile.write(buffer, 0, len)
                    }
                }
            }
            randomAccessFile.setLength(randomAccessFile.filePointer)
        } finally {
            if (bufferFile != null && bufferFile.exists()) {
                bufferFile.delete()
                logger.debug("buffer file(${bufferFile.absolutePath}) deleted")
            }
        }
    }

    private fun getLocationStr(
        indexType: IndexType,
        rpmVersion: RpmVersion,
        location: String?
    ): String {
        return when (indexType) {
            IndexType.OTHERS, IndexType.FILELISTS -> {
                with(rpmVersion) {
                    """name="$name">
    <version epoch="$epoch" ver="$ver" rel="$rel"/>"""
                }
            }
            IndexType.PRIMARY -> {
                """<location href="$location"/>"""
            }
        }
    }

    private fun getPackagePrefix(indexType: IndexType): String {
        return when (indexType) {
            IndexType.OTHERS, IndexType.FILELISTS -> {
                PACKAGE_OTHER_START_MARK
            }
            IndexType.PRIMARY -> {
                PACKAGE_START_MARK
            }
        }
    }

    /**
     * 解析索引文件中的 package 数量
     */
    private fun calculatePackageCount(randomAccessFile: RandomAccessFile, indexType: IndexType): Int {
        val markStr = when (indexType) {
            IndexType.PRIMARY -> """<package type="rpm">"""
            IndexType.FILELISTS, IndexType.OTHERS -> "<package pkgid="
        }
        randomAccessFile.seek(0L)
        var line: String?
        var count = 0
        loop@ while (randomAccessFile.readLine().also { line = it } != null) {
            if (line!!.contains(markStr)) {
                ++count
            }
        }
        return count
    }

    /**
     * 解析索引文件中的 package 值(metadata -> packages)
     */
    fun resolvePackageCount(randomAccessFile: RandomAccessFile, indexType: IndexType): Int {
        val regex = when (indexType) {
            IndexType.PRIMARY -> """^<metadata xmlns="http://linux.duke.edu/metadata/common" xmlns:rpm="http://linux.duke.edu/metadata/rpm" packages="(\d+)">$"""
            IndexType.FILELISTS -> """^<metadata xmlns="http://linux.duke.edu/metadata/filelists" packages="(\d+)">$"""
            IndexType.OTHERS -> """^<metadata xmlns="http://linux.duke.edu/metadata/other" packages="(\d+)">$"""
        }

        randomAccessFile.seek(0L)
        var line: String?
        var lineNum = 0
        while (randomAccessFile.readLine().also { line = it } != null) {
            val matcher = Pattern.compile(regex).matcher(line!!)
            if (matcher.find()) {
                return matcher.group(1).toInt()
            }
            // 索引文件应该在文件的第二行
            if (lineNum++ > 50) {
                throw RuntimeException("resolve package count from file failed")
            }
        }
        throw RuntimeException("resolve package count from file failed")
    }

    private fun newBuffer(): ByteArray {
        return ByteArray(1024 * 1024)
    }

//    fun outFile(randomAccessFile: RandomAccessFile) {
//        if (!logger.isDebugEnabled) return
//        randomAccessFile.seek(0)
//        logger.debug("outFile")
//        var line: String?
//        while (randomAccessFile.readLine().also { line = it } != null) {
//            logger.debug("--$line--")
//        }
//    }
}
