package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.common.api.constant.StringPool.DASH
import com.tencent.bkrepo.common.api.constant.StringPool.DOT
import com.tencent.bkrepo.rpm.artifact.repository.RpmLocalRepository
import com.tencent.bkrepo.rpm.exception.RpmIndexTypeResolveException
import com.tencent.bkrepo.rpm.pojo.RepodataUri
import com.tencent.bkrepo.rpm.pojo.RpmVersion
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil.objectToXml
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadata
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmXmlMetadata
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream

object XmlStrUtils {

    private val logger = LoggerFactory.getLogger(RpmLocalRepository::class.java)

    // package 节点开始标识
    private const val PACKAGE_START_MARK = "  <package type=\"rpm\">"
    // package 结束开始标识
    private const val PACKAGE_END_MARK = "</package>\n"

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
    private const val METADATA_SUFFIX = "</metadata>"

    private const val num = 58

    // metadata 根接节点中 packages 属性
    private const val packages = "packages=\""
    private const val end = ">"
    private const val nullStr = ""

    /**
     * 在原有xml索引文件开头写入新的内容
     * 返回更新后xml
     */
    fun insertPackage(
        indexType: String,
        inputStream: InputStream,
        rpmXmlMetadata: RpmXmlMetadata,
        artifactUri: String
    ): String {
        val stringBuilder = StringBuilder(String(inputStream.readBytes()))
        // 定位插入字符串的位置
        val start: Int = when (indexType) {
            "others" -> {
                stringBuilder.indexOf(PACKAGE_OTHER_START_MARK).
                takeUnless { it == -1 }?: OTHERS_METADATA_PREFIX.length-2
            }
            "filelists" -> {
                stringBuilder.indexOf(PACKAGE_OTHER_START_MARK).
                takeUnless { it == -1 }?: FILELISTS_METADATA_PREFIX.length-2
            }
            "primary" -> {
                stringBuilder.indexOf(PACKAGE_START_MARK).
                takeUnless { it == -1 }?: PRIMARY_METADATA_PREFIX.length-2
            }
            else -> {
                logger.error("$artifactUri 中解析出$indexType 是不受支持的索引类型")
                throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
            }
        }
        val packageXml = rpmXmlMetadata.rpmMetadataToPackageXml(indexType)
        stringBuilder.insert(start, "  $packageXml")
        stringBuilder.packagesModify(true)
        return stringBuilder.toString()
    }

    /**
     * 针对重复节点则替换相应数据
     */
    fun updatePackage(
        indexType: String,
        inputStream: InputStream,
        rpmXmlMetadata: RpmXmlMetadata,
        artifactUri: String
    ): String {
        val epoch = rpmXmlMetadata.packages.first().version.epoch
        val ver = rpmXmlMetadata.packages.first().version.ver
        val rel = rpmXmlMetadata.packages.first().version.rel
        val name = rpmXmlMetadata.packages.first().name
        val locationStr: String = when (indexType) {
            "others", "filelists" -> {
                "name=\"$name\">\n" +
                    "    <version epoch=\"$epoch\" ver=\"$ver\" rel=\"$rel\"/>"
            }
            "primary" -> { "<location href=\"${(rpmXmlMetadata as RpmMetadata).packages.first().location.href}\"/>" }
            else -> {
                logger.error("$artifactUri 中解析出$indexType 是不受支持的索引类型")
                throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
            }
        }

        val stringBuilder = StringBuilder(String(inputStream.readBytes()))
        // 定位查找点
        val prefix: String = when (indexType) {
            "others", "filelists" -> { PACKAGE_OTHER_START_MARK }
            "primary" -> { PACKAGE_START_MARK }
            else -> {
                logger.error("$artifactUri 中解析出$indexType 是不受支持的索引类型")
                throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
            }
        }
        val index = stringBuilder.indexOf(locationStr) + num
        val end = stringBuilder.indexOf(PACKAGE_END_MARK, index) + PACKAGE_END_MARK.length
        val start = stringBuilder.lastIndexOf(prefix, index)
        //未找到正确位置
        return if (start == -1 || end == -1) {
            insertPackage(indexType,
                    ByteArrayInputStream(stringBuilder.toString().toByteArray()),
                    rpmXmlMetadata,
                    artifactUri)
        }else {
            val packageXml = rpmXmlMetadata.rpmMetadataToPackageXml(indexType)
            stringBuilder.replace(start, end, "  $packageXml")
            stringBuilder.toString()
        }
    }

    /**
     * 删除包对应的索引
     * @return 更新后xml
     */
    fun deletePackage(indexType: String, inputStream: InputStream, rpmVersion: RpmVersion, location: String): String {
        val name = rpmVersion.name
        val arch = rpmVersion.arch
        val epoch = rpmVersion.epoch
        val ver = rpmVersion.ver
        val rel = rpmVersion.rel
        val filename = "$name$DASH$ver$rel$DOT$arch.rpm"
        val locationStr: String = when (indexType) {
            "others", "filelists" -> {
                "name=\"$name\">\n" +
                    "    <version epoch=\"$epoch\" ver=\"$ver\" rel=\"$rel\"/>"
            }
            "primary" -> { "<location href=\"$location\"/>" }
            else -> {
                logger.error("$filename 中解析出$indexType 是不受支持的索引类型")
                throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
            }
        }
        val stringBuilder = StringBuilder(String(inputStream.readBytes()))
        // 定位查找点
        val prefix: String = when (indexType) {
            "others", "filelists" -> { PACKAGE_OTHER_START_MARK }
            "primary" -> { PACKAGE_START_MARK }
            else -> {
                logger.error("$filename 中解析出$indexType 是不受支持的索引类型")
                throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
            }
        }
        val index = stringBuilder.indexOf(locationStr) + num
        val end = stringBuilder.indexOf(PACKAGE_END_MARK, index) + PACKAGE_END_MARK.length
        val start = stringBuilder.lastIndexOf(prefix, index)

        stringBuilder.delete(start, end)
        stringBuilder.packagesModify(false)
        return stringBuilder.toString()
    }

    /**
     * 将RpmMetadata 序列化为xml，然后去除metadata根节点。
     * 不直接序列化Package的目的是为了保留缩进格式。
     */
    private fun RpmXmlMetadata.rpmMetadataToPackageXml(indexType: String): String {
        val ver = this.packages.first().version.ver
        val rel = this.packages.first().version.rel
        val name = this.packages.first().name
        val filename = "$name$DASH$ver$rel"
        val prefix = when (indexType) {
            "others" -> { OTHERS_METADATA_PREFIX }
            "primary" -> { PRIMARY_METADATA_PREFIX }
            "filelists" -> { FILELISTS_METADATA_PREFIX }
            else -> {
                logger.error("$filename 中解析出$indexType 是不受支持的索引类型")
                throw RpmIndexTypeResolveException("$indexType 是不受支持的索引类型")
            }
        }
        return this.objectToXml()
            .removePrefix(prefix)
            .removeSuffix(METADATA_SUFFIX)
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
    fun StringBuilder.packagesModify(mark: Boolean): String {
        val start = this.indexOf(packages) + packages.length
        val end = this.indexOf(end, start).dec()
        val sum = if (mark) {
            this.substring(start, end).toInt().inc()
        } else {
            this.substring(start, end).toInt().dec()
        }
        return this.replace(start, end, nullStr).insert(start, sum).toString()
    }

    fun StringBuilder.getPackageSum(): Int {
        val start = this.indexOf(packages) + packages.length
        val end = this.indexOf(end, start).dec()
        return this.substring(start, end).toInt().dec()
    }
}
