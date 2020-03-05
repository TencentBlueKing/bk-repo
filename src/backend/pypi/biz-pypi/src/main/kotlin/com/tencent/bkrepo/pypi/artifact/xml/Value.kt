package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "value")
data class Value constructor(
    @JacksonXmlProperty(localName = "string")
    val string: String?,
    @JacksonXmlProperty(localName = "int")
    val int: Int?,
    @JacksonXmlProperty(localName = "struct")
    val struct: Struct?,
    @JacksonXmlProperty(localName = "array")
    val array: Array?
)
