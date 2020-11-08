package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.annotations.XStreamOmitField
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.Writer

open class RpmXmlMetadata(
    @XStreamOmitField
    open val packages: List<RpmXmlPackage>,
    @XStreamOmitField
    open var packageNum: Long
) {
    fun toXml(): String {
        val outputStream = ByteArrayOutputStream()
        val writer: Writer = OutputStreamWriter(outputStream, "UTF-8")
        val xStream = XStream()
        xStream.autodetectAnnotations(true)
        xStream.toXML(this, writer)
        return String(outputStream.toByteArray())
    }
}
