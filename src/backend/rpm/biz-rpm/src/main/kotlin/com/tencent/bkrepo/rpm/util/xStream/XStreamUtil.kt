package com.tencent.bkrepo.rpm.util.xStream

import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmEntry
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmFile
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadata
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmPackage
import com.tencent.bkrepo.rpm.util.xStream.repomd.Repomd
import com.thoughtworks.xstream.XStream
import org.slf4j.LoggerFactory

object XStreamUtil {
    private val logger = LoggerFactory.getLogger(XStreamUtil::class.java)

    fun xmlToObject(xml: String): Any {
        val xStream = XStream()
        XStream.setupDefaultSecurity(xStream)
        xStream.autodetectAnnotations(true)
        xStream.alias("metadata", RpmMetadata::class.java)
        xStream.alias("repomd", Repomd::class.java)
        xStream.allowTypes(
            arrayOf(
                RpmMetadata::class.java,
                RpmEntry::class.java,
                RpmFile::class.java
            )
        )
        return xStream.fromXML(xml)
    }

    fun checkMarkFile(markFileContent: ByteArray): Boolean {
        return try {
            val xStream = XStream()
            XStream.setupDefaultSecurity(xStream)
            xStream.autodetectAnnotations(true)
            xStream.alias("package", RpmPackage::class.java)
            xStream.allowTypes(
                arrayOf(
                    RpmPackage::class.java,
                    RpmEntry::class.java,
                    RpmFile::class.java
                )
            )
            xStream.fromXML(markFileContent.inputStream())
            true
        } catch (e: Exception) {
            logger.warn("checkMarkFile error: ${e.message}")
            false
        }
    }
}
