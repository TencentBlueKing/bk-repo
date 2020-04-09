package com.tencent.bkrepo.pypi.artifact.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
@JacksonXmlRootElement(localName = "struct")
data class Struct constructor(
    @JacksonXmlElementWrapper(localName = "members", useWrapping = false)
    @JacksonXmlProperty(localName = "member")
    val memberList: List<Member>?
)
