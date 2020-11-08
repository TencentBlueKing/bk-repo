package com.tencent.bkrepo.rpm.util.xStream

import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmEntry
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmFile
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadata
import com.tencent.bkrepo.rpm.util.xStream.repomd.Repomd
import com.thoughtworks.xstream.XStream

object XStreamUtil {
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
}
