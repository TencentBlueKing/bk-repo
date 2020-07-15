package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class Member constructor(
    @JacksonXmlProperty(localName = "name")
    val name: String,
    @JacksonXmlProperty(localName = "value")
    val value: Value
)
