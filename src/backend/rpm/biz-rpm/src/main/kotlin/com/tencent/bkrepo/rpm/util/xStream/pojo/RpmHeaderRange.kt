package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAsAttribute

data class RpmHeaderRange(
    @XStreamAsAttribute
    val start: Int,
    @XStreamAsAttribute
    val end: Int
)
