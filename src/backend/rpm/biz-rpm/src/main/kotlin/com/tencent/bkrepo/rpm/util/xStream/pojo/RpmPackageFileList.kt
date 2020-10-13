package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import com.thoughtworks.xstream.annotations.XStreamImplicit
import java.util.LinkedList

@XStreamAlias("package")
data class RpmPackageFileList(
    @XStreamAsAttribute
    override val pkgid: String,
    @XStreamAsAttribute
    override val name: String,
    override val version: RpmVersion,
    @XStreamImplicit(itemFieldName = "file")
    val files: List<RpmFile>
) : RpmXmlPackage(pkgid, name, version)
