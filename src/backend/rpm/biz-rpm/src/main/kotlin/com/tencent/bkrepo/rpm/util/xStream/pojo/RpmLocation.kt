package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAsAttribute

data class RpmLocation(
    @XStreamAsAttribute
    val href: String
)
