package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import com.thoughtworks.xstream.annotations.XStreamImplicit
import java.util.LinkedList

@XStreamAlias("package")
data class RpmPackageChangeLog(
    @XStreamAsAttribute
    override val pkgid: String,
    @XStreamAsAttribute
    override val name: String,
    override val version: RpmVersion,
    @XStreamImplicit(itemFieldName = "changelog")
    val changeLogs: LinkedList<RpmChangeLog>
) : RpmXmlPackage(pkgid, name, version)
