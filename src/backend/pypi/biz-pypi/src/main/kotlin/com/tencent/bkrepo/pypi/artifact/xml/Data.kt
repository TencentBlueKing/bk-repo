package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "data")
data class Data constructor(
    @JacksonXmlElementWrapper(localName = "valueList", useWrapping = false)
    @JacksonXmlProperty(localName = "value")
    val valueList: List<Value>
)
