package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.pojo.RepodataUri
import com.tencent.bkrepo.rpm.util.redline.model.RpmMetadataWithOldStream
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil.objectToXml
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadata

object XmlStrUtil {

    // package 节点开始标识
    private const val PACKAGE_START_MARK = "  <package type=\"rpm\">"
    // package 结束开始标识
    private const val PACKAGE_END_MARK = "</package>\n"

    // RpmMetadata序列化成xml中 metadata 开始字符串
    private const val METADATA_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke.edu/metadata/rpm\" packages=\"1\">\n" +
        "  "
    // RpmMetadata序列化成xml中 metadata 结束字符串
    private const val METADATA_SUFFIX = "</metadata>"

    // metadata 根接节点中 packages 属性
    private const val packages = "packages=\""
    private const val end = ">"
    private const val nullStr = ""

    /**
     * 在原有xml索引文件开头写入新的内容
     *  @property rpmMetadataWithOldStream
     *  @return 更新后xml
     */
    fun insertPackage(rpmMetadataWithOldStream: RpmMetadataWithOldStream): String {
        val inputStream = rpmMetadataWithOldStream.OldPrimaryStream
        val rpmMetadata = rpmMetadataWithOldStream.newRpmMetadata
        val stringBuilder = StringBuilder(String(inputStream.readBytes()))
        // 定位插入字符串的位置
        val start = stringBuilder.indexOf(PACKAGE_START_MARK)

        val packageXml = rpmMetadata.rpmMetadataToPackageXml()

        stringBuilder.insert(start, "  $packageXml")
        stringBuilder.packagesPlus()
        return stringBuilder.toString()
    }

    /**
     * 针对重复节点则替换相应数据
     * @property rpmMetadataWithOldStream
     * @return 更新后xml
     */
    fun updatePackage(rpmMetadataWithOldStream: RpmMetadataWithOldStream): String {
        val inputStream = rpmMetadataWithOldStream.OldPrimaryStream
        val rpmMetadata = rpmMetadataWithOldStream.newRpmMetadata

        val locationStr = "<location href=\"${rpmMetadata.packages.first().location.href}\"/>"
        val stringBuilder = StringBuilder(String(inputStream.readBytes()))
        // 定位查找点
        val index = stringBuilder.indexOf(locationStr)
        val end = stringBuilder.indexOf(PACKAGE_END_MARK, index) + PACKAGE_END_MARK.length
        val start = stringBuilder.lastIndexOf(PACKAGE_START_MARK, index)

        val packageXml = rpmMetadata.rpmMetadataToPackageXml()

        stringBuilder.replace(start, end, "  $packageXml")
        stringBuilder.packagesPlus()
        return stringBuilder.toString()
    }

    /**
     * 将RpmMetadata 序列化为xml，然后去除metadata根节点。
     * 不直接序列化Package的目的是为了保留缩进格式。
     */
    private fun RpmMetadata.rpmMetadataToPackageXml(): String {
        return this.objectToXml()
            .removePrefix(METADATA_PREFIX)
            .removeSuffix(METADATA_SUFFIX)
    }

    /**
     * 按照仓库设置的repodata 深度分割请求参数
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
     * 更新索引文件中 package 数量+1
     */
    fun StringBuilder.packagesPlus(): String {
        val start = this.indexOf(packages) + packages.length
        val end = this.indexOf(end, start).dec()
        val sum = this.substring(start, end).toInt().inc()
        return this.replace(start, end, nullStr).insert(start, sum).toString()
    }
}
