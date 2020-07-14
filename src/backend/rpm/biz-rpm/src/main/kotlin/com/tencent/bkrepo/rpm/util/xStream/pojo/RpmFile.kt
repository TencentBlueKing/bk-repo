package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import com.thoughtworks.xstream.annotations.XStreamConverter
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter

@XStreamConverter(value = ToAttributedValueConverter::class, strings = ["filePath"])
data class RpmFile(
    @XStreamAsAttribute
    val type: String?,
    val filePath: String
)
