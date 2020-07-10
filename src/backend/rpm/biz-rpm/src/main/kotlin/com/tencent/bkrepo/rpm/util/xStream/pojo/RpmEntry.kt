package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute

@XStreamAlias("rpm:entry")
data class RpmEntry(
    @XStreamAsAttribute
    val name: String?
) {
    @XStreamAsAttribute
    var flags: String? = null
    @XStreamAsAttribute
    var epoch: String? = null
    @XStreamAsAttribute
    var ver: String? = null
    @XStreamAsAttribute
    var rel: String? = null
    @XStreamAsAttribute
    var pre: String? = null
}
