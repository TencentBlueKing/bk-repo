package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAsAttribute

data class RpmVersion(
    @XStreamAsAttribute
    val epoch: Int,
    @XStreamAsAttribute
    val ver: String,
    @XStreamAsAttribute
    val rel: String
)
