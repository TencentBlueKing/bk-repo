package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import com.thoughtworks.xstream.annotations.XStreamImplicit

@XStreamAlias("metadata")
class RpmMetadata(
    @XStreamImplicit(itemFieldName = "package")
    val packages: Set<RpmPackage>,
    @XStreamAsAttribute
    @XStreamAlias("packages")
    var packageNum: Long
) {
        @XStreamAsAttribute
        val xmlns: String = "http://linux.duke.edu/metadata/common"
        @XStreamAsAttribute
        @XStreamAlias("xmlns:rpm")
        val rpm: String = "http://linux.duke.edu/metadata/rpm"
}
