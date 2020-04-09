package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "param")
data class Param constructor(
    @JacksonXmlProperty(localName = "value")
    val value: Value
)
