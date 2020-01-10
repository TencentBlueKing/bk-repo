package com.tencent.bkrepo.pypi.pojo.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "methodCall")
class XmlMethodCallRootElement(
    @JacksonXmlElementWrapper(localName = "params")
    val params: XmlParamsRootElement?,
    @JacksonXmlProperty(localName = "methodName")
    val methodName: String ?= "search"
)