package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute

data class RpmSize(
    @XStreamAsAttribute
    @XStreamAlias("package")
    val packager: Long,
    @XStreamAsAttribute
    val installed: Int,
    @XStreamAsAttribute
    val archive: Int
)
