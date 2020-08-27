package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import com.thoughtworks.xstream.annotations.XStreamOmitField

@XStreamAlias("package")
data class RpmPackage(
    @XStreamAsAttribute
    val type: String,
    override val name: String,
    val arch: String,
    override val version: RpmVersion,
    @XStreamOmitField
    override val pkgid: String,
    val checksum: RpmChecksum,
    val summary: String?,
    val description: String?,
    val packager: String?,
    val url: String?,
    val time: RpmTime,
    val size: RpmSize,
    val location: RpmLocation,
    val format: RpmFormat
) : RpmXmlPackage(pkgid, name, version)
