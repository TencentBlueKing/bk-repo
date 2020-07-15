package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAsAttribute

data class RpmTime(
    @XStreamAsAttribute
    val file: Long,
    @XStreamAsAttribute
    val build: Int
)
