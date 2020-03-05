package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "params")
data class Params constructor(
    @JacksonXmlElementWrapper(localName = "paramList", useWrapping = false)
    @JacksonXmlProperty(localName = "param")
    val paramList: List<Param>
)
