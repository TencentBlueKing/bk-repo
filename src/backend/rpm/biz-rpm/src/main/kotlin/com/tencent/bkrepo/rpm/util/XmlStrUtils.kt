/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.  
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.common.api.constant.StringPool.DASH
import com.tencent.bkrepo.rpm.artifact.repository.RpmLocalRepository
import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.pojo.RepodataUri
import com.tencent.bkrepo.rpm.pojo.RpmVersion
import com.tencent.bkrepo.rpm.util.FileInputStreamUtils.deleteContent
import com.tencent.bkrepo.rpm.util.FileInputStreamUtils.findPackageIndex
import com.tencent.bkrepo.rpm.util.FileInputStreamUtils.insertContent
import com.tencent.bkrepo.rpm.util.FileInputStreamUtils.rpmIndex
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil.objectToXml
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadata
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmXmlMetadata
import org.slf4j.LoggerFactory
import org.springframework.util.StopWatch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.util.regex.Pattern

object XmlStrUtils {

    private val logger = LoggerFactory.getLogger(RpmLocalRepository::class.java)

    // package 节点开始标识
    private const val PACKAGE_START_MARK = "  <package type=\"rpm\">"
    // package 结束开始标识
    const val PACKAGE_END_MARK = "</package>\n"

    private const val PACKAGE_OTHER_START_MARK = "  <package pkgid"

    // RpmMetadata序列化成xml中 metadata 开始字符串
    private const val PRIMARY_METADATA_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke.edu/metadata/rpm\" packages=\"1\">\n" +
        "  "
    private const val OTHERS_METADATA_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<metadata " +
        "xmlns=\"http://linux.duke.edu/metadata/other\" packages=\"1\">\n" +
        "  "
    private const val FILELISTS_METADATA_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<metadata " +
        "xmlns=\"http://linux.duke.edu/metadata/filelists\" packages=\"1\">\n" +
        "  "
    // RpmMetadata序列化成xml中 metadata 结束字符串
    const val METADATA_SUFFIX = "</metadata>"

    /**
     * 在原有xml索引文件开头写入新的内容
     * 返回更新后xml
     */
    fun insertPackage(
        indexType: IndexType,
        file: File,
        rpmXmlMetadata: RpmXmlMetadata,
        calculatePackage: Boolean
    ): File {
        val packageXml = rpmXmlMetadata.rpmMetadataToPackageXml(indexType)
        val stopWatch = StopWatch()
        stopWatch.start("insertContent")
        file.insertContent(packageXml)
        stopWatch.stop()
        stopWatch.start("updatePackageCount")
        val resultFile = file.packagesModify(indexType, true, calculatePackage)
        stopWatch.stop()
        if (logger.isDebugEnabled) {
            logger.debug("insertRpmPackageStat: $stopWatch")
        }
        return resultFile
    }

    /**
     * 针对重复节点则替换相应数据
     */
    fun updatePackage(
        indexType: IndexType,
        file: File,
        rpmXmlMetadata: RpmXmlMetadata
    ): File {
        val rpmVersion = RpmVersion(
            rpmXmlMetadata.packages.first().name,
            "default",
            rpmXmlMetadata.packages.first().version.epoch.toString(),
            rpmXmlMetadata.packages.first().version.ver,
            rpmXmlMetadata.packages.first().version.rel
        )
        val location = if (indexType == IndexType.PRIMARY) {
            (rpmXmlMetadata as RpmMetadata).packages.first().location.href
        } else { null }

        val locationStr = getLocationStr(indexType, rpmVersion, location)
        val prefix = getPackagePrefix(indexType)

        val packageXml = rpmXmlMetadata.rpmMetadataToPackageXml(indexType)
        val stopWatch = StopWatch()
        stopWatch.start("findPackageIndex")
        val xmlIndex = file.findPackageIndex(prefix, locationStr, PACKAGE_END_MARK)
        stopWatch.stop()

        var resultFile = file
        if (xmlIndex == null) {
            logger.warn("updateFileLists, findPackageIndex failed, skip delete index")
        } else {
            stopWatch.start("deleteContent")
            resultFile = file.deleteContent(xmlIndex)
            stopWatch.stop()
        }

        stopWatch.start("insertContent")
        resultFile = resultFile.insertContent(packageXml)
        stopWatch.stop()

        if (xmlIndex == null) {
            stopWatch.start("updatePackageCount")
            resultFile = resultFile.packagesModify(indexType, mark = false, calculatePackage = false)
            stopWatch.stop()
        }
        if (logger.isDebugEnabled) {
            logger.debug("updateRpmPackageIndexStat: $stopWatch")
        }
        return resultFile
    }

    /**
     * 删除包对应的索引
     * [indexType] 索引类型
     * [file] 需要删除内容的索引文件
     * [rpmVersion]
     * [location] rpm构件相对repodata的目录
     * [File] 更新后xml
     */
    fun deletePackage(
        indexType: IndexType,
        file: File,
        rpmVersion: RpmVersion,
        location: String?
    ): File {
        val locationStr = getLocationStr(indexType, rpmVersion, location)
        val prefix = getPackagePrefix(indexType)

        val stopWatch = StopWatch()
        stopWatch.start("findIndex")
        val xmlIndex = file.findPackageIndex(prefix, locationStr, PACKAGE_END_MARK)
        stopWatch.stop()

        var resultFile = file
        if (xmlIndex == null) {
            logger.warn("deletePackage, findPackageIndex failed, skip delete index")
        } else {
            stopWatch.start("deleteContent")
            val fileAfterDelete = file.deleteContent(xmlIndex)
            stopWatch.stop()
            stopWatch.start("updatePackageCount")
            resultFile = fileAfterDelete.packagesModify(indexType, mark = false, calculatePackage = false)
            stopWatch.stop()
        }
        if (logger.isDebugEnabled) {
            logger.debug("updateRpmFileListIndexStat: $stopWatch")
        }
        return resultFile
    }

    private fun getLocationStr(
        indexType: IndexType,
        rpmVersion: RpmVersion,
        location: String?
    ): String {
        return when (indexType) {
            IndexType.OTHERS, IndexType.FILELISTS -> {
                with(rpmVersion) {
                    "name=\"$name\">\n" +
                        "    <version epoch=\"$epoch\" ver=\"$ver\" rel=\"$rel\"/>"
                }
            }
            IndexType.PRIMARY -> {
                "<location href=\"$location\"/>"
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
     * 将RpmMetadata 序列化为xml，然后去除metadata根节点。
     * 不直接序列化Package的目的是为了保留缩进格式。
     */
    private fun RpmXmlMetadata.rpmMetadataToPackageXml(indexType: IndexType): String {
        val prefix = when (indexType) {
            IndexType.OTHERS -> {
                OTHERS_METADATA_PREFIX
            }
            IndexType.PRIMARY -> {
                PRIMARY_METADATA_PREFIX
            }
            IndexType.FILELISTS -> {
                FILELISTS_METADATA_PREFIX
            }
        }
        return this.objectToXml()
            .removePrefix(prefix)
    }

    /**
     * 按照仓库设置的repodata深度 分割请求参数
     */
    fun splitUriByDepth(uri: String, depth: Int): RepodataUri {
        val uriList = uri.removePrefix("/").split("/")
        val repodataPath = StringBuilder("/")
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

    /**
     * 更新索引文件中 package 数量
     * [mark] true:package加1，false: package减1
     */
    fun File.packagesModify(indexType: IndexType, mark: Boolean, calculatePackage: Boolean): File {
        val regex = when (indexType) {
            IndexType.PRIMARY ->
                "^<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke" +
                    ".edu/metadata/rpm\" packages=\"(\\d+)\">$"
            IndexType.FILELISTS -> "^<metadata xmlns=\"http://linux.duke.edu/metadata/filelists\" packages=\"(\\d+)\">$"
            IndexType.OTHERS -> "^<metadata xmlns=\"http://linux.duke.edu/metadata/other\" packages=\"(\\d+)\">$"
        }

        val markStr = when (indexType) {
            IndexType.PRIMARY -> "<package type=\"rpm\">"
            IndexType.FILELISTS, IndexType.OTHERS -> "<package pkgid="
        }

        var line: String?
        var num = 0
        val resultFile = File.createTempFile(indexType.name, "xml")
        try {
            BufferedReader(InputStreamReader(FileInputStream(this))).use { bufferReader ->
                if (calculatePackage) {
                    // 遍历包数量
                    loop@ while (bufferReader.readLine().also { line = it } != null) {
                        if (line?.contains(markStr)!!) {
                            ++num
                        }
                        if (line == "</metadata>") break@loop
                    }
                } else {
                    loop@ while (bufferReader.readLine().also { line = it } != null) {
                        val matcher = Pattern.compile(regex).matcher(line!!)
                        if (matcher.find()) {
                            num = matcher.group(1).toInt()
                            break@loop
                        }
                    }
                    if (mark) ++num else --num
                }
            }

            val updatedStr = when (indexType) {
                IndexType.PRIMARY ->
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                        "<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke" +
                        ".edu/metadata/rpm\" packages=\"$num\">\n"
                IndexType.FILELISTS ->
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                        "<metadata xmlns=\"http://linux.duke.edu/metadata/filelists\" packages=\"$num\">\n"
                IndexType.OTHERS ->
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                        "<metadata xmlns=\"http://linux.duke.edu/metadata/other\" packages=\"$num\">\n"
            }
            val index = this.rpmIndex("  <package").takeIf { it >= 0 } ?: updatedStr.length

            try {
                BufferedOutputStream(FileOutputStream(resultFile)).use { outputStream ->
                    outputStream.write(updatedStr.toByteArray())
                    val buffer = ByteArray(1 * 1024 * 1024)
                    var markSeek: Int
                    RandomAccessFile(this, "rw").use { randomAccessFile ->
                        randomAccessFile.seek(index.toLong())
                        while (randomAccessFile.read(buffer).also { markSeek = it } > 0) {
                            outputStream.write(buffer, 0, markSeek)
                        }
                        outputStream.flush()
                    }
                }
                return resultFile
            } catch (e: Exception) {
                logger.info("resultFile:${resultFile.name} 创建失败！")
                resultFile.delete()
                throw e
            }
        } finally {
            this.delete()
        }
    }
}
