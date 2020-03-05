package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "methodCall")
data class MethodCall constructor(
    @JacksonXmlProperty(localName = "methodName")
    val methodName: String,
    @JacksonXmlProperty(localName = "params")
    val params: Params
)
