package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class Array constructor(
    @JacksonXmlProperty(localName = "data")
    val data: Data
)
