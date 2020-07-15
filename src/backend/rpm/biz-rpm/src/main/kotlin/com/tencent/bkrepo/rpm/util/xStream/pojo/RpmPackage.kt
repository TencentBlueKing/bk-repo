package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute

/**
 *
 */
@XStreamAlias("package")
data class RpmPackage(
    @XStreamAsAttribute
    val type: String,
    val name: String,
    val arch: String,
    val version: RpmVersion,
    val checksum: RpmChecksum,
    val summary: String?,
    val description: String?,
    val packager: String?,
    val url: String?,
    val time: RpmTime,
    val size: RpmSize,
    val location: RpmLocation,
    val format: RpmFormat
)
