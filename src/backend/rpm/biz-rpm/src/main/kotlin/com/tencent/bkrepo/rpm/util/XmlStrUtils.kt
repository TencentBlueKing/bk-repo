package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.common.api.constant.StringPool.DASH
import com.tencent.bkrepo.rpm.artifact.repository.RpmLocalRepository
import com.tencent.bkrepo.rpm.exception.RpmIndexTypeResolveException
import com.tencent.bkrepo.rpm.pojo.RepodataUri
import com.tencent.bkrepo.rpm.pojo.RpmVersion
import com.tencent.bkrepo.rpm.util.FileInputStreamUtils.deleteContent
import com.tencent.bkrepo.rpm.util.FileInputStreamUtils.findPackageIndex
import com.tencent.bkrepo.rpm.util.FileInputStreamUtils.insertContent
import com.tencent.bkrepo.rpm.util.FileInputStreamUtils.rpmIndex
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toRpmVersion
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
import java.io.InputStream
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
        indexType: String,
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

    fun insertFileLists(
        indexType: String,
        file: File,
        inputStream: InputStream,
        calculatePackage: Boolean
    ): File {
        val fileLists = String(inputStream.use { it.readBytes() })
        val stopWatch = StopWatch()
        stopWatch.start("insertContent")
        file.insertContent(fileLists)
        stopWatch.stop()
        stopWatch.start("updatePackageCount")
        val resultFile = file.packagesModify(indexType, true, calculatePackage)
        stopWatch.stop()
        if (logger.isDebugEnabled) {
            logger.debug("insertRpmFileListIndexStat: $stopWatch")
        }
        return resultFile
    }

    fun updateFileLists(
        file: File,
        tempFileFullPath: String,
        inputStream: InputStream,
        metadata: Map<String, String>
    ): File {
        val rpmVersion = metadata.toRpmVersion(tempFileFullPath)
        val locationStr = with(rpmVersion) {
            "name=\"$name\">\n" +
                "    <version epoch=\"$epoch\" ver=\"$ver\" rel=\"$rel\"/>"
        }
        val prefix = PACKAGE_OTHER_START_MARK
        val packageXml = String(inputStream.use { it.readBytes() })
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
        resultFile.insertContent(packageXml)
        stopWatch.stop()

        if (xmlIndex == null) {
            stopWatch.start("updatePackageCount")
            resultFile = resultFile.packagesModify("filelists", mark = false, calculatePackage = false)
            stopWatch.stop()
        }
        if (logger.isDebugEnabled) {
            logger.debug("updateRpmFileListIndexStat: $stopWatch")
        }
        return resultFile
    }

    /**
     * 针对重复节点则替换相应数据
     */
    fun updatePackage(
        indexType: String,
        file: File,
        rpmXmlMetadata: RpmXmlMetadata,
        artifactUri: String
    ): File {
        val epoch = rpmXmlMetadata.packages.first().version.epoch
        val ver = rpmXmlMetadata.packages.first().version.ver
        val rel = rpmXmlMetadata.packages.first().version.rel
        val name = rpmXmlMetadata.packages.first().name
        val locationStr: String = when (indexType) {
            "others", "filelists" -> {
                "name=\"$name\">\n" +
                    "    <version epoch=\"$epoch\" ver=\"$ver\" rel=\"$rel\"/>"
            }
            "primary" -> {
                "<location href=\"${(rpmXmlMetadata as RpmMetadata).packages.first().location.href}\"/>"
            }
            else -> {
                logger.error("$artifactUri 中解析出$indexType 是不受支持的索引类型")
                throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
            }
        }

        // 定位查找点
        val prefix: String = when (indexType) {
            "others", "filelists" -> {
                PACKAGE_OTHER_START_MARK
            }
            "primary" -> {
                PACKAGE_START_MARK
            }
            else -> {
                logger.error("$artifactUri 中解析出$indexType 是不受支持的索引类型")
                throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
            }
        }
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
     * @return 更新后xml
     */
    fun deletePackage(
        indexType: String,
        file: File,
        rpmVersion: RpmVersion,
        location: String
    ): File {
        val name = rpmVersion.name
        val arch = rpmVersion.arch
        val epoch = rpmVersion.epoch
        val ver = rpmVersion.ver
        val rel = rpmVersion.rel
        val filename = "$name-$ver-$rel.$arch.rpm"
        val locationStr: String = when (indexType) {
            "others", "filelists" -> {
                "name=\"$name\">\n" +
                    "    <version epoch=\"$epoch\" ver=\"$ver\" rel=\"$rel\"/>"
            }
            "primary" -> {
                "<location href=\"$location\"/>"
            }
            else -> {
                logger.error("$filename 中解析出$indexType 是不受支持的索引类型")
                throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
            }
        }
        // 定位查找点
        val prefix: String = when (indexType) {
            "others", "filelists" -> {
                PACKAGE_OTHER_START_MARK
            }
            "primary" -> {
                PACKAGE_START_MARK
            }
            else -> {
                logger.error("$filename 中解析出$indexType 是不受支持的索引类型")
                throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
            }
        }
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

    /**
     * 将RpmMetadata 序列化为xml，然后去除metadata根节点。
     * 不直接序列化Package的目的是为了保留缩进格式。
     */
    fun RpmXmlMetadata.rpmMetadataToPackageXml(indexType: String): String {
        val ver = this.packages.first().version.ver
        val rel = this.packages.first().version.rel
        val name = this.packages.first().name
        val filename = "$name$DASH$ver$rel"
        val prefix = when (indexType) {
            "others" -> {
                OTHERS_METADATA_PREFIX
            }
            "primary" -> {
                PRIMARY_METADATA_PREFIX
            }
            "filelists" -> {
                FILELISTS_METADATA_PREFIX
            }
            else -> {
                logger.error("$filename 中解析出$indexType 是不受支持的索引类型")
                throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
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

    /**
     * 更新索引文件中 package 数量
     * [mark] true:package加1，false: package减1
     */
    fun File.packagesModify(indexType: String, mark: Boolean, calculatePackage: Boolean): File {
        val regex = when (indexType) {
            "primary" ->
                "^<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke" +
                    ".edu/metadata/rpm\" packages=\"(\\d+)\">$"
            "filelists" -> "<metadata xmlns=\"http://linux.duke.edu/metadata/filelists\" packages=\"(\\d+)\">"
            "others" -> "<metadata xmlns=\"http://linux.duke.edu/metadata/other\" packages=\"(\\d+)\">"
            else -> throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
        }

        // 遍历包数量
        val markStr = when (indexType) {
            "primary" -> "<package type=\"rpm\">"
            "filelists", "others" -> "<package pkgid="
            else -> throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
        }

        var line: String
        var num = 0
        var resultFile: File
        try {
            BufferedReader(InputStreamReader(FileInputStream(this))).use { bufferReader ->
                if (calculatePackage) {
                    // 遍历包数量
                    loop@ while (bufferReader.readLine().also { line = it } != null) {
                        if (line.contains(markStr)) {
                            ++num
                        }
                        if (line == "</metadata>") break@loop
                    }
                } else {
                    loop@ while (bufferReader.readLine().also { line = it } != null) {
                        val matcher = Pattern.compile(regex).matcher(line)
                        if (matcher.find()) {
                            num = matcher.group(1).toInt()
                            break@loop
                        }
                    }
                    if (mark) ++num else --num
                }
            }

            val updatedStr = when (indexType) {
                "primary" ->
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                        "<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke" +
                        ".edu/metadata/rpm\" packages=\"$num\">\n"
                "filelists" ->
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                        "<metadata xmlns=\"http://linux.duke.edu/metadata/filelists\" packages=\"$num\">\n"
                "others" ->
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                        "<metadata xmlns=\"http://linux.duke.edu/metadata/other\" packages=\"$num\">\n"
                else -> throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
            }
            val index = this.rpmIndex("  <package").takeIf { it >= 0 } ?: updatedStr.length

            resultFile = File.createTempFile(indexType, "xml")
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
        return resultFile
    }
}
