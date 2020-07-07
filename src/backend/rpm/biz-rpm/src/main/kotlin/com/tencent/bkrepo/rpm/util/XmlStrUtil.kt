package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.PACKAGE_START_MARK
import com.tencent.bkrepo.rpm.METADATA_PREFIX
import com.tencent.bkrepo.rpm.METADATA_SUFFIX
import com.tencent.bkrepo.rpm.PACKAGE_END_MARK
import com.tencent.bkrepo.rpm.pojo.RepodataUri
import com.tencent.bkrepo.rpm.util.redline.model.RpmMetadataWithOldStream
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil

object XmlStrUtil {

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

        val metadataXml = XStreamUtil.objectToXml(rpmMetadata)
        val packageXml = metadataXml.removePrefix(METADATA_PREFIX).removeSuffix(METADATA_SUFFIX)

        stringBuilder.insert(start, "  $packageXml")
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
        // 为了保留xml的缩进格式
        val metadataXml = XStreamUtil.objectToXml(rpmMetadata)
        val packageXml = metadataXml.removePrefix(METADATA_PREFIX).removeSuffix(METADATA_SUFFIX)

        stringBuilder.replace(start, end, "  $packageXml")
        return stringBuilder.toString()
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
}
