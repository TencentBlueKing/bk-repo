package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class Value constructor(
    @JacksonXmlProperty(localName = "string")
    val string: String?,
    @JacksonXmlProperty(localName = "int")
    val int: Int?,
    @JacksonXmlProperty(localName = "struct")
    val struct: Struct?,
    @JacksonXmlProperty(localName = "array")
    val array: Array?,
    @JacksonXmlProperty(localName = "boolean")
    val boolean: String?

)
