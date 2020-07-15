package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class Params constructor(
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "param")
    val paramList: List<Param>
)
