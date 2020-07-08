package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class Data constructor(
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "value")
    val valueList: MutableList<Value>?
)
