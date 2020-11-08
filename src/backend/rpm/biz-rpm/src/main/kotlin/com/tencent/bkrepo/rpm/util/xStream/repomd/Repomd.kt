package com.tencent.bkrepo.rpm.util.xStream.repomd

import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import com.thoughtworks.xstream.annotations.XStreamImplicit
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.Writer

/**
 * repomd.xml
 */
@XStreamAlias("repomd")
data class Repomd(
    @XStreamImplicit()
    val repoDatas: List<RepoIndex>
) {
    @XStreamAsAttribute
    val xmlns: String = "http://linux.duke.edu/metadata/repo"
    @XStreamAsAttribute
    @XStreamAlias("xmlns:rpm")
    val rpm: String = "http://linux.duke.edu/metadata/rpm"

    fun toXml(): String{
        val outputStream = ByteArrayOutputStream()
        val writer: Writer = OutputStreamWriter(outputStream, "UTF-8")
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n")

        val xStream = XStream()
        xStream.autodetectAnnotations(true)
        xStream.toXML(this, writer)
        return String(outputStream.toByteArray())
    }
}
