package com.tencent.bkrepo.rpm.util.xStream

import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmEntry
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmFile
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadata
import com.tencent.bkrepo.rpm.util.xStream.repomd.Repomd
import com.thoughtworks.xstream.XStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.Writer

object XStreamUtil {
    fun objectToXml(obj: Any): String {
        val xStream = XStream()
        val outputStream = ByteArrayOutputStream()
        val writer: Writer = OutputStreamWriter(outputStream, "UTF-8")
        if (obj is RpmMetadata) { writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n") }
        xStream.autodetectAnnotations(true)
        xStream.toXML(obj, writer)
        return String(outputStream.toByteArray())
    }

    fun xmlToObject(xml: String): Any {
        val xStream = XStream()
        XStream.setupDefaultSecurity(xStream)
        xStream.autodetectAnnotations(true)
        xStream.alias("metadata", RpmMetadata::class.java)
        xStream.alias("repomd", Repomd::class.java)
        xStream.allowTypes(arrayOf(RpmMetadata::class.java,
                RpmEntry::class.java,
                RpmFile::class.java))
        return xStream.fromXML(xml)
    }
}
